package com.defang.launcher.ui.settings.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defang.launcher.data.repository.WatchedUrlRepository
import com.defang.launcher.domain.model.WatchedUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchedSitesViewModel @Inject constructor(
    private val repo: WatchedUrlRepository,
) : ViewModel() {

    val sites: StateFlow<List<WatchedUrl>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Normalizes [raw] and upserts it. Returns false if the input isn't a valid
     * host (so the screen can flag it); true on success.
     */
    fun add(raw: String, isAdult: Boolean): Boolean {
        val pattern = WatchedUrl.normalize(raw) ?: return false
        viewModelScope.launch {
            repo.upsert(
                WatchedUrl(
                    pattern = pattern,
                    label = pattern,
                    isAdult = isAdult,
                    enabled = true,
                    cooldownEndsAt = 0L,
                )
            )
        }
        return true
    }

    fun remove(pattern: String) {
        viewModelScope.launch { repo.delete(pattern) }
    }

    fun setAdult(pattern: String, isAdult: Boolean) {
        viewModelScope.launch { repo.setAdult(pattern, isAdult) }
    }

    fun setEnabled(pattern: String, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(pattern, enabled) }
    }
}
