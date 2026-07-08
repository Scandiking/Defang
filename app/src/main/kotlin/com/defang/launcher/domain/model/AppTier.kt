package com.defang.launcher.domain.model

enum class AppTier(val dbValue: Int) {
    /** Opens instantly — no overlay, no timer. Camera, Calculator, Maps, etc. */
    UTILITY(0),

    /** Full friction stack: intent gate → session timer → cool-down. */
    WATCHED(1);

    companion object {
        fun fromDbValue(value: Int): AppTier = entries.firstOrNull { it.dbValue == value } ?: UTILITY
    }
}
