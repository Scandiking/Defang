package com.defang.launcher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app grayscale for watched apps, implemented by toggling the system
 * color-correction (daltonizer) secure setting when a watched session starts
 * and restoring it when the session ends. Android has no true per-app color
 * filter — overlay windows cannot desaturate content beneath them — so this
 * system-wide toggle scoped to session boundaries is the standard technique.
 *
 * Requires WRITE_SECURE_SETTINGS (same one-time adb grant used for
 * accessibility self-enable). Without it every call is a silent no-op, so the
 * feature degrades gracefully on ungranted installs.
 *
 * The user's own color-correction configuration (e.g. deuteranomaly
 * correction) is saved before we touch it and restored afterwards. The
 * "applied" flag is persisted so a crash mid-session can be recovered on the
 * next service start instead of leaving the whole phone gray.
 */
@Singleton
class GrayscaleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesDataStore,
) {

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Turns grayscale on for a watched session. Idempotent. */
    suspend fun enable() {
        if (!hasPermission()) return
        if (!prefs.grayscaleEnabled.first()) return
        if (prefs.grayscaleApplied.first()) return // already ours — keep saved state intact

        val resolver = context.contentResolver
        val prevEnabled = Settings.Secure.getInt(resolver, DALTONIZER_ENABLED, 0)
        val prevMode = Settings.Secure.getInt(resolver, DALTONIZER_MODE, -1)
        prefs.saveDaltonizerState(prevEnabled, prevMode)

        try {
            Settings.Secure.putInt(resolver, DALTONIZER_MODE, MODE_MONOCHROMACY)
            Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, 1)
        } catch (_: SecurityException) {
            return
        }
        prefs.setGrayscaleApplied(true)
    }

    /** Restores the user's original color-correction state. Idempotent. */
    suspend fun disable() {
        if (!hasPermission()) return
        if (!prefs.grayscaleApplied.first()) return

        val resolver = context.contentResolver
        val prevEnabled = prefs.savedDaltonizerEnabled.first()
        val prevMode = prefs.savedDaltonizerMode.first()

        try {
            Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, prevEnabled)
            if (prevMode != -1) {
                Settings.Secure.putInt(resolver, DALTONIZER_MODE, prevMode)
            }
        } catch (_: SecurityException) {
            return
        }
        prefs.setGrayscaleApplied(false)
    }

    /**
     * Crash recovery: if we left grayscale on (process killed mid-session),
     * restore color. Call on service (re)connect, before any new session.
     */
    suspend fun recoverIfStale() {
        if (prefs.grayscaleApplied.first()) disable()
    }

    companion object {
        // Hidden-API setting names, stable across Android versions
        private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER_MODE = "accessibility_display_daltonizer"

        /** Daltonizer mode 0 = monochromacy (full grayscale). */
        private const val MODE_MONOCHROMACY = 0
    }
}
