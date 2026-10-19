package com.defang.launcher.domain.usecase

import com.defang.launcher.data.repository.SessionRepository
import com.defang.launcher.domain.model.PersonalUsage
import java.util.Calendar
import javax.inject.Inject

/**
 * This app's (or site's) usage over today and the last 7 calendar days, for
 * the personal tidbits. Read from local session history only.
 */
class GetPersonalUsageUseCase @Inject constructor(
    private val repo: SessionRepository,
    private val capResolver: SessionCapResolver,
) {
    suspend fun forKey(pkg: String, pattern: String?): PersonalUsage {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
        return PersonalUsage.from(
            sessions = repo.getSessionsSince(weekStart),
            pkg = pkg,
            pattern = pattern,
            now = System.currentTimeMillis(),
            dayStart = dayStart,
            capMs = capResolver.load(),
        )
    }
}
