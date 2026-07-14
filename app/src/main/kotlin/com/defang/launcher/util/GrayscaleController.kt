package com.defang.launcher.util

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Ground truth for "is the lock screen currently showing" — a colorful lock
     * screen is a cue in the exact habit loop this app exists to interrupt, so
     * it must render grayscale unconditionally, independent of session/gate
     * state. Checked fresh on every call rather than cached.
     */
    fun isDeviceLocked(): Boolean {
        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isKeyguardLocked == true
    }

    // enable() and disable() are check-then-act over suspending DataStore reads,
    // and the accessibility service calls enable() on every window event from a
    // watched app. Without mutual exclusion two interleaved enable() calls both
    // pass the "already applied" check; the second one then snapshots the
    // already-gray daltonizer as the user's "previous" state, and every disable()
    // from then on restores gray — permanently. The mutex makes each operation
    // atomic within the process (settings UI shares this singleton too).
    private val lock = Mutex()

    /** Turns grayscale on for a watched session. Idempotent. */
    suspend fun enable() = lock.withLock {
        if (!hasPermission()) return@withLock
        if (!prefs.grayscaleEnabled.first()) return@withLock
        if (prefs.grayscaleApplied.first()) return@withLock // already ours — keep saved state intact

        val resolver = context.contentResolver
        var prevEnabled = Settings.Secure.getInt(resolver, DALTONIZER_ENABLED, 0)
        var prevMode = Settings.Secure.getInt(resolver, DALTONIZER_MODE, -1)
        if (prevEnabled == 1 && prevMode == MODE_MONOCHROMACY) {
            // The "previous" state is exactly our own gray signature — almost
            // certainly residue of an earlier unclean session, not a deliberate
            // user choice. Recording it would make disable() restore gray forever.
            // Cost of this assumption: a user who genuinely runs system-wide
            // monochromacy gets color correction switched off after each watched
            // session — recoverable in system settings, unlike permanent gray.
            prevEnabled = 0
            prevMode = -1
        }
        prefs.saveDaltonizerState(prevEnabled, prevMode)

        // Mark applied before touching the setting: a crash between the two then
        // triggers recoverIfStale(), which harmlessly re-restores the saved state.
        // The old order (write setting, then mark) left gray on with no recovery.
        prefs.setGrayscaleApplied(true)
        try {
            Settings.Secure.putInt(resolver, DALTONIZER_MODE, MODE_MONOCHROMACY)
            Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, 1)
        } catch (_: SecurityException) {
            prefs.setGrayscaleApplied(false)
        }
    }

    /** Restores the user's original color-correction state. Idempotent. */
    suspend fun disable() = lock.withLock {
        if (!hasPermission()) return@withLock
        if (!prefs.grayscaleApplied.first()) return@withLock
        // Never restore color while the lock screen is showing — whichever
        // call site asked, this always wins. The one legitimate "restore
        // color" moment (ACTION_USER_PRESENT) fires exactly when the keyguard
        // has just been dismissed, so it isn't blocked by this check.
        if (isDeviceLocked()) return@withLock

        val resolver = context.contentResolver
        val prevEnabled = prefs.savedDaltonizerEnabled.first()
        val prevMode = prefs.savedDaltonizerMode.first()

        try {
            Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, prevEnabled)
            if (prevMode != -1) {
                Settings.Secure.putInt(resolver, DALTONIZER_MODE, prevMode)
            }
        } catch (_: SecurityException) {
            return@withLock
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
