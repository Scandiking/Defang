package com.defang.launcher.ui.settings.apptier

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.AppTier
import com.defang.launcher.domain.model.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppTierItem(
    val packageName: String,
    val label: String,
    val tier: AppTier,
)

@HiltViewModel
class AppTierViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AppConfigRepository,
) : ViewModel() {

    val apps: StateFlow<List<AppTierItem>> = repo.observeAll()
        .map { list ->
            list.map { entity ->
                AppTierItem(
                    packageName = entity.packageName,
                    label = entity.appLabel,
                    tier = AppTier.fromDbValue(entity.tier),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTier(packageName: String, tier: AppTier) {
        viewModelScope.launch {
            repo.setTier(packageName, tier.dbValue)
        }
    }
}
