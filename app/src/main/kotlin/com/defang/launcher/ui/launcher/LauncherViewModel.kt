package com.defang.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
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
    /** Effective display label — the system label unless the user renamed it. */
    val label: String,
    /** The system-supplied label, kept even after a rename (dialog placeholder). */
    val rawLabel: String = label,
    /** Non-null when the user has overridden the label. Cosmetic only — never
     *  touches packageName, which stays the identity used for gating/launching. */
    val customLabel: String? = null,
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
    /** Set when another installed app shares this app's displayed name and
     *  the one-time nudge to rename it hasn't been answered yet. */
    val renamePrompt: AppInfo? = null,
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

    val letterRailScale: StateFlow<Float> = prefs.letterRailScale.stateIn(
        viewModelScope, SharingStarted.Eagerly, 1f
    )
    val letterRailXOffsetDp: StateFlow<Int> = prefs.letterRailXOffsetDp.stateIn(
        viewModelScope, SharingStarted.Eagerly, 4
    )

    init {
        viewModelScope.launch {
            val onboardingDone = prefs.isOnboardingDone.first()
            val (apps, renamePrompt) = reloadApps()
            _uiState.value = LauncherUiState(
                apps = apps,
                needsOnboarding = !onboardingDone,
                homeTidbit = tidbitSelector.daily(ContentTrack.GENERAL),
                // One-time heads-up about Google's install lockdown — after
                // onboarding so it isn't the first thing a new user sees
                showLockdownWarning = onboardingDone && !prefs.isLockdownWarned.first(),
                // Same reasoning — don't compete with onboarding for attention
                renamePrompt = if (onboardingDone) renamePrompt else null,
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
            val (apps, renamePrompt) = reloadApps()
            _uiState.value = _uiState.value.copy(apps = apps, renamePrompt = renamePrompt)
        }
    }

    /** User picked "Rename" (either from the long-press menu or the duplicate-name
     *  prompt). Blank clears back to the system label. Cosmetic only — packageName,
     *  the identity used for gating/launching, is never touched. */
    fun renameApp(packageName: String, newLabel: String) {
        viewModelScope.launch {
            appConfigRepo.setCustomLabel(packageName, newLabel.trim().ifBlank { null })
            appConfigRepo.setRenamePromptDismissed(packageName, true)
            val (apps, renamePrompt) = reloadApps()
            _uiState.value = _uiState.value.copy(apps = apps, renamePrompt = renamePrompt)
        }
    }

    /** User dismissed the duplicate-name prompt without renaming — don't ask again. */
    fun dismissRenamePrompt(packageName: String) {
        viewModelScope.launch {
            appConfigRepo.setRenamePromptDismissed(packageName, true)
            _uiState.value = _uiState.value.copy(renamePrompt = null)
        }
    }

    /** Scans packages (off the main thread), syncs the DB, applies label overrides,
     *  and picks the next duplicate-name pair (if any) to nudge the user about. */
    private suspend fun reloadApps(): Pair<List<AppInfo>, AppInfo?> {
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

        val configs = appConfigRepo.observeAll().first().associateBy { it.packageName }

        // Defang itself is listed so settings stay reachable from the drawer.
        // LauncherActivity routes a tap on our own package to SettingsActivity.
        val apps = (installedApps + workApps + AppInfo(context.packageName, "Defang"))
            .map { app ->
                val custom = configs[app.packageName]?.customLabel?.trim()?.takeIf { it.isNotEmpty() }
                if (custom != null) app.copy(label = custom, customLabel = custom) else app
            }
            .sortedBy { it.label.lowercase() }

        return apps to findRenamePromptCandidate(apps, configs)
    }

    /**
     * Two apps sharing a displayed name is common with generic-labelled apps
     * (several banks all ship a "Mobilbank") and gets worse once icons/branding
     * are stripped, since the name is the only thing left to tell them apart by.
     * Surfaces the first still-undecided member of the first colliding pair so
     * the drawer can offer a one-time rename nudge — never re-asks once a
     * package has an answer (renamed or "not now") on file.
     */
    private fun findRenamePromptCandidate(
        apps: List<AppInfo>,
        configs: Map<String, AppConfigEntity>,
    ): AppInfo? {
        val collisions = apps
            .filter { it.packageName != context.packageName }
            .groupBy { it.label.trim().lowercase() }
            .filterValues { it.size > 1 }
        for (group in collisions.values) {
            val candidate = group.firstOrNull { app ->
                val cfg = configs[app.packageName]
                cfg?.customLabel.isNullOrBlank() && cfg?.renamePromptDismissed != true
            }
            if (candidate != null) return candidate
        }
        return null
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
                        label = config.customLabel?.trim()?.takeIf { it.isNotEmpty() } ?: config.appLabel,
                        minutes = minutes,
                        limitMinutes = config.sessionLimitMinutes.coerceAtLeast(1),
                    )
                }
                .sortedByDescending { it.minutes }
                .take(3)
        }
    }

    /**
     * Where an app came from — the closest thing Android exposes on-device to
     * a "developer name", and often the only way to tell apart two apps with
     * the same generic label (e.g. a stock Samsung app vs. an F-Droid one).
     * Looked up on demand (rename dialog open), not during the drawer scan —
     * a PackageManager round-trip per app on every refresh isn't worth it for
     * something only shown when the user asks.
     */
    suspend fun installSourceLabel(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            when (installer) {
                null -> "Preinstalled or sideloaded"
                "com.android.vending" -> "Play Store"
                "org.fdroid.fdroid" -> "F-Droid"
                "com.aurora.store" -> "Aurora Store"
                "com.sec.android.app.samsungapps" -> "Galaxy Store"
                "com.amazon.venezia" -> "Amazon Appstore"
                "com.huawei.appmarket" -> "AppGallery"
                else -> installer
            }
        } catch (e: PackageManager.NameNotFoundException) {
            ""
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
                val list = try {
                    launcherApps.getActivityList(null, profile)
                } catch (e: SecurityException) {
                    emptyList()
                }
                list
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
