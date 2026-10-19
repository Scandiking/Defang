package com.defang.launcher.domain.usecase

import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.SessionDuration
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Builds the per-row cap for [SessionDuration.countedMs]: app rows use the
 * app's session limit, site rows the global limit (at least the 15 min the
 * site gate uses). Loads config once, so call it once per query.
 */
class SessionCapResolver @Inject constructor(
    private val appConfigRepo: AppConfigRepository,
    private val prefs: PreferencesDataStore,
) {
    suspend fun load(): (SessionEntity) -> Long {
        val appLimits = appConfigRepo.observeAll().first()
            .associate { it.packageName to it.sessionLimitMinutes }
        val siteLimit = maxOf(prefs.sessionLimitMinutes.first(), DEFAULT_LIMIT_MINUTES)
        return { row ->
            val limit = if (row.watchedPattern != null) siteLimit
            else appLimits[row.packageName] ?: DEFAULT_LIMIT_MINUTES
            SessionDuration.capMs(limit)
        }
    }

    /** Counted ms per row, ready to sum. */
    suspend fun counter(now: Long): (SessionEntity) -> Long {
        val cap = load()
        return { row -> SessionDuration.countedMs(row, now, cap(row)) }
    }

    private companion object {
        const val DEFAULT_LIMIT_MINUTES = 15
    }
}
