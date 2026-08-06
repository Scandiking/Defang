package com.defang.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.UserManager
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val label: String,
    /** Personal profile unless this app came from loadWorkProfileApps(). */
    val userHandle: android.os.UserHandle = Process.myUserHandle(),
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
    val showLockdownWarning: Boolean = false,
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
            _uiState.value = LauncherUiState(
                apps = reloadApps(),
                needsOnboarding = !onboardingDone,
                homeTidbit = tidbitSelector.daily(ContentTrack.GENERAL),
                // One-time heads-up about Google's install lockdown — after
                // onboarding so it isn't the first thing a new user sees
                showLockdownWarning = onboardingDone && !prefs.isLockdownWarned.first(),
            )
        }
    }

    /**
     * Re-scans installed apps, seeds newly installed ones, and prunes rows for
     * apps that are gone. The ViewModel outlives many activity resumes (a
     * launcher process sticks around), so without this the list is frozen at the
     * snapshot taken on first launch. Called when the drawer opens and whenever a
     * package add/remove broadcast fires — cheap enough for both.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(apps = reloadApps())
        }
    }

    /** Scans packages (off the main thread), syncs the DB, returns the drawer list. */
    private suspend fun reloadApps(): List<AppInfo> {
        val installedApps = withContext(Dispatchers.IO) { loadInstalledApps() }
        seedAppConfigs(installedApps)
        appConfigRepo.pruneUninstalled(installedApps.map { it.packageName })

        // Opt-in — apps from a work profile (or other secondary user profile)
        // are display+launch only, not run through the watched-app tier system:
        // AppConfigRepository keys configs by package name alone, and a work
        // profile app commonly shares its package name with a personal one.
        val workApps = if (prefs.workProfileAppsEnabled.first()) {
            withContext(Dispatchers.IO) { loadWorkProfileApps() }
        } else {
            emptyList()
        }

        // Defang itself is listed so settings stay reachable from the drawer.
        // LauncherActivity routes a tap on our own package to SettingsActivity.
        return (installedApps + workApps + AppInfo(context.packageName, "Defang"))
            .sortedBy { it.label.lowercase() }
    }

    /** Re-derives the tidbit of the day — called on resume so it rolls over at midnight. */
    fun refreshHomeTidbit() {
        _uiState.value = _uiState.value.copy(
            homeTidbit = tidbitSelector.daily(ContentTrack.GENERAL),
        )
    }

    fun dismissLockdownWarning() {
        _uiState.value = _uiState.value.copy(showLockdownWarning = false)
        viewModelScope.launch { prefs.setLockdownWarned() }
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

    /** Packages the user has hidden from the drawer — they only appear via search. */
    val hiddenPackages: StateFlow<Set<String>> = appConfigRepo.observeAll()
        .map { list -> list.filter { it.hidden }.map { it.packageName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Drawer list: hidden apps are excluded while browsing, but a non-empty
     * search query matches them again — rarely-used apps (airport, bank, tax)
     * stay installed without occupying the list.
     */
    fun filteredApps(hidden: Set<String>): List<AppInfo> {
        val q = _uiState.value.query.trim()
        return if (q.isEmpty()) {
            _uiState.value.apps.filterNot { it.packageName in hidden }
        } else {
            _uiState.value.apps.filter { it.label.contains(q, ignoreCase = true) }
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

    /** Apps installed under other user profiles associated with this user (e.g. a work profile). */
    private fun loadWorkProfileApps(): List<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val userManager = context.getSystemService(UserManager::class.java) ?: return emptyList()
        val myProfile = Process.myUserHandle()
        return userManager.userProfiles
            .filter { it != myProfile }
            .flatMap { profile ->
                launcherApps.getActivityList(null, profile)
                    .filter { it.componentName.packageName != context.packageName }
                    .map { info ->
                        AppInfo(
                            packageName = info.componentName.packageName,
                            label = info.label.toString(),
                            userHandle = profile,
                        )
                    }
            }
    }

    private suspend fun seedAppConfigs(apps: List<AppInfo>) {
        // Default watched list — user can adjust in settings
        val defaultWatched = setOf(
            // Social media
            "com.instagram.android","com.google.android.documentsui",
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
