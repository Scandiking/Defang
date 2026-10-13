package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.SessionEntity

/**
 * One watched app's (or site's) own usage, from local session history — the
 * raw material for the personal tidbits. Never leaves the device.
 */
data class PersonalUsage(
    val todayMinutes: Int,
    val opensToday: Int,
    val weekMinutes: Int,
    /** Average per day over the last 7 days, or over fewer if history is younger. */
    val avgDailyMinutes: Int,
    /** Calendar days the 7-day window actually has data for (1..7). */
    val daysOfHistory: Int,
) {
    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L

        /**
         * Builds usage for [pkg] from [sessions] (the last 7 days, any app).
         * Browser-site gates are keyed by [pattern] instead, so a site's
         * numbers aren't the whole browser's. Only ended sessions count toward
         * time; every row started today counts as an open.
         */
        fun from(
            sessions: List<SessionEntity>,
            pkg: String,
            pattern: String?,
            now: Long,
            dayStart: Long,
        ): PersonalUsage {
            val mine = sessions.filter {
                if (pattern != null) it.watchedPattern == pattern
                else it.packageName == pkg && it.watchedPattern == null
            }
            fun minutes(rows: List<SessionEntity>) = (rows
                .filter { it.endTime > it.startTime }
                .sumOf { it.endTime - it.startTime } / 60_000L).toInt()

            val today = mine.filter { it.startTime >= dayStart }
            val weekMinutes = minutes(mine)
            val oldest = mine.minOfOrNull { it.startTime }
            val days = if (oldest == null) 1
            else (((dayStart - oldest) + DAY_MS - 1) / DAY_MS + 1).toInt().coerceIn(1, 7)

            return PersonalUsage(
                todayMinutes = minutes(today),
                opensToday = today.size,
                weekMinutes = weekMinutes,
                avgDailyMinutes = weekMinutes / days,
                daysOfHistory = days,
            )
        }
    }
}
