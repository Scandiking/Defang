package com.defang.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val label: String,
)

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val query: String = "",
    val needsOnboarding: Boolean = false,
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesDataStore,
    private val appConfigRepo: AppConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState

    init {
        viewModelScope.launch {
            val onboardingDone = prefs.isOnboardingDone.first()
            val allApps = loadInstalledApps()
            seedAppConfigs(allApps)
            _uiState.value = LauncherUiState(
                apps = allApps,
                needsOnboarding = !onboardingDone,
            )
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
