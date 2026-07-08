package com.defang.launcher.domain.usecase

import com.defang.launcher.data.repository.SessionRepository
import java.util.Calendar
import javax.inject.Inject

/**
 * Checks whether the user has already used their one extension today.
 * "Today" is defined as same calendar day in the device locale.
 */
class GetDailyExtensionStatusUseCase @Inject constructor(
    private val repo: SessionRepository,
) {
    suspend fun isExtensionAvailable(): Boolean {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return repo.getExtensionUsedToday(dayStart) == null
    }
}
