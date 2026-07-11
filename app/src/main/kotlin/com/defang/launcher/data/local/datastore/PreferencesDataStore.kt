package com.defang.launcher.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.defang.launcher.domain.model.HomeScreenMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "defang_prefs")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // ── Onboarding ──────────────────────────────────────────────────────────
    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    val isOnboardingDone: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() = store.edit { it[KEY_ONBOARDING_DONE] = true }

    // ── Distribution lockdown warning ─────────────────────────────────────────
    // Shown once after onboarding: Google's September 2026 sideload lockdown
    // and what it means for installing Defang updates. See PRD §10.
    private val KEY_LOCKDOWN_WARNED = booleanPreferencesKey("lockdown_warned")

    val isLockdownWarned: Flow<Boolean> = store.data.map { it[KEY_LOCKDOWN_WARNED] ?: false }

    suspend fun setLockdownWarned() = store.edit { it[KEY_LOCKDOWN_WARNED] = true }

    // ── Daily extension ──────────────────────────────────────────────────────
    // We store the calendar date string (yyyy-MM-dd) on which the extension was used.
    private val KEY_EXTENSION_DATE = stringPreferencesKey("extension_date")

    val extensionUsedDate: Flow<String?> = store.data.map { it[KEY_EXTENSION_DATE] }

    suspend fun markExtensionUsed(date: String) = store.edit { it[KEY_EXTENSION_DATE] = date }

    // ── Last known foreground package ────────────────────────────────────────
    private val KEY_LAST_FG_PKG = stringPreferencesKey("last_fg_pkg")

    val lastForegroundPackage: Flow<String?> = store.data.map { it[KEY_LAST_FG_PKG] }

    suspend fun setLastForegroundPackage(pkg: String) = store.edit { it[KEY_LAST_FG_PKG] = pkg }

    // ── Global defaults — apply to all watched apps ───────────────────────────
    private val KEY_GATE_DELAY    = intPreferencesKey("gate_delay_seconds")
    private val KEY_SESSION_LIMIT = intPreferencesKey("session_limit_minutes")
    private val KEY_COOLDOWN      = intPreferencesKey("cooldown_minutes")

    val gateDelaySeconds: Flow<Int>    = store.data.map { it[KEY_GATE_DELAY]    ?: 8  }
    val sessionLimitMinutes: Flow<Int> = store.data.map { it[KEY_SESSION_LIMIT] ?: 15 }
    val cooldownMinutes: Flow<Int>     = store.data.map { it[KEY_COOLDOWN]      ?: 30 }

    suspend fun setGateDelay(seconds: Int)    = store.edit { it[KEY_GATE_DELAY]    = seconds }
    suspend fun setSessionLimit(minutes: Int) = store.edit { it[KEY_SESSION_LIMIT] = minutes }
    suspend fun setCooldown(minutes: Int)     = store.edit { it[KEY_COOLDOWN]      = minutes }

    // ── Per-app grayscale ─────────────────────────────────────────────────────
    private val KEY_GRAYSCALE_ENABLED = booleanPreferencesKey("grayscale_enabled")
    // True while WE have switched the system daltonizer on — used to restore
    // the user's own color-correction state and to recover after a crash.
    private val KEY_GRAYSCALE_APPLIED = booleanPreferencesKey("grayscale_applied")
    private val KEY_SAVED_DALTONIZER_ENABLED = intPreferencesKey("saved_daltonizer_enabled")
    private val KEY_SAVED_DALTONIZER_MODE    = intPreferencesKey("saved_daltonizer_mode")

    val grayscaleEnabled: Flow<Boolean> = store.data.map { it[KEY_GRAYSCALE_ENABLED] ?: true }
    val grayscaleApplied: Flow<Boolean> = store.data.map { it[KEY_GRAYSCALE_APPLIED] ?: false }

    suspend fun setGrayscaleEnabled(on: Boolean) = store.edit { it[KEY_GRAYSCALE_ENABLED] = on }
    suspend fun setGrayscaleApplied(on: Boolean) = store.edit { it[KEY_GRAYSCALE_APPLIED] = on }

    suspend fun saveDaltonizerState(enabled: Int, mode: Int) = store.edit {
        it[KEY_SAVED_DALTONIZER_ENABLED] = enabled
        it[KEY_SAVED_DALTONIZER_MODE] = mode
    }

    val savedDaltonizerEnabled: Flow<Int> = store.data.map { it[KEY_SAVED_DALTONIZER_ENABLED] ?: 0 }
    val savedDaltonizerMode: Flow<Int>    = store.data.map { it[KEY_SAVED_DALTONIZER_MODE] ?: -1 }

    // ── Notification sanitization ─────────────────────────────────────────────
    private val KEY_NOTIF_SANITIZE = booleanPreferencesKey("notification_sanitize_enabled")

    val notificationSanitizeEnabled: Flow<Boolean> =
        store.data.map { it[KEY_NOTIF_SANITIZE] ?: true }

    suspend fun setNotificationSanitizeEnabled(on: Boolean) =
        store.edit { it[KEY_NOTIF_SANITIZE] = on }

    // ── Notification batching ─────────────────────────────────────────────────
    // Delivery windows in minutes-since-midnight; -1 = window disabled.
    // Batching is active iff at least one window is set.
    private val KEY_BATCH_WINDOW_1 = intPreferencesKey("batch_window_1")
    private val KEY_BATCH_WINDOW_2 = intPreferencesKey("batch_window_2")

    val batchWindow1: Flow<Int> = store.data.map { it[KEY_BATCH_WINDOW_1] ?: -1 }
    val batchWindow2: Flow<Int> = store.data.map { it[KEY_BATCH_WINDOW_2] ?: -1 }

    suspend fun setBatchWindow1(minutesOfDay: Int) =
        store.edit { it[KEY_BATCH_WINDOW_1] = minutesOfDay }

    suspend fun setBatchWindow2(minutesOfDay: Int) =
        store.edit { it[KEY_BATCH_WINDOW_2] = minutesOfDay }

    // ── Home screen usage panel ───────────────────────────────────────────────
    // The launcher hosts no AppWidgetHost, so the usage widget can't be placed
    // on Defang's own home screen — this toggle renders the same data natively.
    private val KEY_HOME_USAGE = booleanPreferencesKey("home_usage_enabled")

    val homeUsageEnabled: Flow<Boolean> = store.data.map { it[KEY_HOME_USAGE] ?: false }

    suspend fun setHomeUsageEnabled(on: Boolean) = store.edit { it[KEY_HOME_USAGE] = on }

    // ── Home screen mode ──────────────────────────────────────────────────────
    private val KEY_HOME_MODE = intPreferencesKey("home_screen_mode")

    val homeScreenMode: Flow<HomeScreenMode> = store.data.map {
        HomeScreenMode.fromOrdinal(it[KEY_HOME_MODE] ?: HomeScreenMode.CLOCK_AND_TIDBIT.ordinal)
    }

    suspend fun setHomeScreenMode(mode: HomeScreenMode) =
        store.edit { it[KEY_HOME_MODE] = mode.ordinal }

    // ── Custom offline tasks ──────────────────────────────────────────────────
    // User-entered task prompts shown on the end card alongside the built-in
    // library. Newline-encoded; tasks themselves are single-line by construction.
    private val KEY_CUSTOM_TASKS = stringPreferencesKey("custom_tasks")

    val customTasks: Flow<List<String>> = store.data.map { prefs ->
        (prefs[KEY_CUSTOM_TASKS] ?: "").lineSequence()
            .filter { it.isNotBlank() }
            .toList()
    }

    suspend fun addCustomTask(task: String) = store.edit { prefs ->
        val current = (prefs[KEY_CUSTOM_TASKS] ?: "").lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()
        val cleaned = task.replace('\n', ' ').trim()
        if (cleaned.isNotEmpty() && cleaned !in current) {
            current.add(cleaned)
            prefs[KEY_CUSTOM_TASKS] = current.joinToString("\n")
        }
    }

    suspend fun removeCustomTask(task: String) = store.edit { prefs ->
        val remaining = (prefs[KEY_CUSTOM_TASKS] ?: "").lineSequence()
            .filter { it.isNotBlank() && it != task }
            .toList()
        prefs[KEY_CUSTOM_TASKS] = remaining.joinToString("\n")
    }

    // Suppressed-notification counts per package, persisted so batching
    // survives process death. Encoded "pkg=count" per line.
    private val KEY_SUPPRESSED_COUNTS = stringPreferencesKey("suppressed_counts")

    val suppressedCounts: Flow<Map<String, Int>> = store.data.map { prefs ->
        (prefs[KEY_SUPPRESSED_COUNTS] ?: "").lineSequence()
            .mapNotNull { line ->
                val idx = line.lastIndexOf('=')
                if (idx <= 0) null
                else line.substring(0, idx) to (line.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null)
            }
            .toMap()
    }

    suspend fun setSuppressedCounts(counts: Map<String, Int>) = store.edit { prefs ->
        prefs[KEY_SUPPRESSED_COUNTS] = counts.entries
            .joinToString("\n") { "${it.key}=${it.value}" }
    }
}
