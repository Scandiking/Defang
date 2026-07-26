package com.defang.launcher.domain.usecase

import com.defang.launcher.data.repository.SessionRepository
import java.util.Calendar
import javax.inject.Inject

/**
 * Total watched-app opens since local midnight, across all watched apps.
 *
 * Drives the intent gate's escalation floor: the more the user has opened
 * watched apps today, the harsher the warning shown at rest before they drag
 * the slider. "Today" is the same calendar day in the device locale, matching
 * [GetDailyExtensionStatusUseCase].
 */
class GetTodayOpenCountUseCase @Inject constructor(
    private val repo: SessionRepository,
) {
    suspend fun count(): Int {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return repo.countSince(dayStart)
    }
}
