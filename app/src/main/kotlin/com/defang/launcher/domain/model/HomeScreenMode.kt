package com.defang.launcher.domain.model

/** What the launcher home screen shows. Stored by ordinal — append only. */
enum class HomeScreenMode {
    /** Black screen with only the system status and navigation bars. */
    EMPTY,

    /** Only the tidbit of the day. */
    TIDBIT,

    /** Clock, date, and the tidbit of the day (default). */
    CLOCK_AND_TIDBIT;

    companion object {
        fun fromOrdinal(value: Int): HomeScreenMode =
            entries.getOrElse(value) { CLOCK_AND_TIDBIT }
    }
}
