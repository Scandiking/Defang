package com.defang.launcher.domain.model

/**
 * How long the usage session log (opens, time, extension justifications) is
 * kept. Stored by ordinal — append only. DONT_TRACK stops new sessions from
 * being written at all; INDEFINITE never prunes (today's de facto default,
 * kept as an explicit choice rather than a silent lack of a cap).
 */
enum class RetentionLevel {
    DONT_TRACK,
    DAYS_7,
    MONTHS_3,
    MONTHS_6,
    MONTHS_12,
    INDEFINITE;

    /** Rows older than this cutoff should be pruned. Null means "never prune". */
    fun cutoffMillis(now: Long = System.currentTimeMillis()): Long? = when (this) {
        DONT_TRACK -> now
        DAYS_7 -> now - 7L * DAY_MS
        MONTHS_3 -> now - 90L * DAY_MS
        MONTHS_6 -> now - 180L * DAY_MS
        MONTHS_12 -> now - 365L * DAY_MS
        INDEFINITE -> null
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L

        fun fromOrdinal(value: Int): RetentionLevel =
            entries.getOrElse(value) { INDEFINITE }
    }
}
