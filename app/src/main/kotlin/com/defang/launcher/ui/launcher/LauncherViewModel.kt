package com.defang.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.domain.model.HomeScreenMode
import com.defang.launcher.util.TidbitSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val label: String,
)

/** One row of the optional home screen usage panel. */
data class HomeUsageRow(
    val label: String,
    val minutes: Long,
    val limitMinutes: Int,
)

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val needsOnboarding: Boolean = false,
    val homeTidbit: String = "",
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesDataStore,
    private val appConfigRepo: AppConfigRepository,
    private val sessionRepo: SessionRepository,
    private val tidbitSelector: TidbitSelector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState

    val homeMode: StateFlow<HomeScreenMode> = prefs.homeScreenMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, HomeScreenMode.CLOCK_AND_TIDBIT
    )

    init {
        viewModelScope.launch {
            val onboardingDone = prefs.isOnboardingDone.first()
            val installedApps = loadInstalledApps()
            seedAppConfigs(installedApps)
            // Defang itself is listed so settings stay reachable from the drawer.
            // LauncherActivity routes a tap on our own package to SettingsActivity.
            val allApps = (installedApps + AppInfo(context.packageName, "Defang"))
                .sortedBy { it.label.lowercase() }
            _uiState.value = LauncherUiState(
                apps = allApps,
                needsOnboarding = !onboardingDone,
                homeTidbit = tidbitSelector.daily(ContentTrack.GENERAL),
            )
        }
    }

    /** Re-derives the tidbit of the day — called on resume so it rolls over at midnight. */
    fun refreshHomeTidbit() {
        _uiState.value = _uiState.value.copy(
            homeTidbit = tidbitSelector.daily(ContentTrack.GENERAL),
        )
    }

    val homeUsageEnabled: StateFlow<Boolean> = prefs.homeUsageEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    private val _homeUsage = MutableStateFlow<List<HomeUsageRow>>(emptyList())
    val homeUsage: StateFlow<List<HomeUsageRow>> = _homeUsage

    /**
     * Recomputes today's usage panel — called on resume, which is exactly when
     * the number can have changed (a session just ended and we're back home).
     * Only watched apps with time on the clock today appear; a clean day shows
     * nothing at all.
     */
    fun refreshHomeUsage() {
        viewModelScope.launch {
            if (!prefs.homeUsageEnabled.first()) {
                _homeUsage.value = emptyList()
                return@launch
            }
            val zone = java.time.ZoneId.systemDefault()
            val dayStart = java.time.LocalDate.now(zone)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()

            val minutesByPkg = sessionRepo.getSessionsSince(dayStart)
                .groupBy { it.packageName }
                .mapValues { (_, sessions) ->
                    sessions.sumOf { s ->
                        val end = if (s.endTime > s.startTime) s.endTime else now
                        end - s.startTime
                    } / 60_000
                }

            _homeUsage.value = appConfigRepo.observeAll().first()
                .filter { it.tier == AppTier.WATCHED.dbValue }
                .mapNotNull { config ->
                    val minutes = minutesByPkg[config.packageName] ?: return@mapNotNull null
                    if (minutes <= 0) return@mapNotNull null
                    HomeUsageRow(
                        label = config.appLabel,
                        minutes = minutes,
                        limitMinutes = config.sessionLimitMinutes.coerceAtLeast(1),
                    )
                }
                .sortedByDescending { it.minutes }
                .take(3)
        }
    }

    fun onQueryChange(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
    }

    val filteredApps: List<AppInfo>
        get() {
            val q = _uiState.value.query.trim()
            return if (q.isEmpty()) _uiState.value.apps
            else _uiState.value.apps.filter {
                it.label.contains(q, ignoreCase = true)
            }
        }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                )
            }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private suspend fun seedAppConfigs(apps: List<AppInfo>) {
        // Default watched list — user can adjust in settings
        val defaultWatched = setOf(
            // Social media
            "com.instagram.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",       // TikTok
            "com.ss.android.ugc.trill",       // TikTok (some regions)
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.X.android",
            // Facebook
            "com.facebook.katana",
            "com.facebook.lite",
            // YouTube
            "com.google.android.youtube",
            // Dating apps
            "com.tinder",
            "com.bumble.app",
            "co.hinge.app",
            "com.okcupid.okcupid",
            "com.grindr.android",
            "com.badoo.mobile",
            "com.match.android",
            "com.poc.happn",              // Happn
            "com.meetic.jconnecte",       // Meetic
            // Adult content — sideloaded apps (not on Play Store)
            "com.pornhub.pornhub",
            "com.xvideos.app",
            "com.xhamster.android",
            "com.xnxx.app",
            "com.onlyfans.app",
            "com.fancentro.android",
        )
        val configs = apps.map { app ->
            AppConfigEntity(
                packageName = app.packageName,
                appLabel = app.label,
                tier = if (app.packageName in defaultWatched) 1 else 0,
            )
        }
        appConfigRepo.seedInstalledApps(configs)
    }
}
