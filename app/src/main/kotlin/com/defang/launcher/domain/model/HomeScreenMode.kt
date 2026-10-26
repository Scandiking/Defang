package com.defang.launcher.domain.model

/** What the launcher home screen shows. Stored by ordinal — append only. */
enum class HomeScreenMode {
    /** Black screen with only the system status and navigation bars. */
    EMPTY,

    /** Only the tidbit of the day. */
    TIDBIT,

    /** Clock, date, and the tidbit of the day (default). */
    CLOCK_AND_TIDBIT,

    /**
     * Blank screen; swipe up opens a search box with the keyboard immediately
     * active. No app list until the user types, so the list can't cue them
     * into opening something (issue #14).
     */
    SEARCH_ONLY,

    /** Like [SEARCH_ONLY], but the full app list shows below the search box (issue #70). */
    SEARCH_ONLY_WITH_LIST;

    /** Either search-only variant: drawer opens straight into search, auto-open on one match. */
    val isSearchOnly: Boolean get() = this == SEARCH_ONLY || this == SEARCH_ONLY_WITH_LIST

    companion object {
        fun fromOrdinal(value: Int): HomeScreenMode =
            entries.getOrElse(value) { CLOCK_AND_TIDBIT }
    }
}
