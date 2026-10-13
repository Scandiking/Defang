package com.defang.launcher.util

import com.defang.launcher.domain.model.PersonalUsage

/** A personal line kind and the number it states. Formatted by [TidbitSelector.personal]. */
data class PersonalLine(val kind: Kind, val value: Int) {
    enum class Kind { DAILY_AVERAGE, TODAY, OPENS_TODAY, WEEK, YEAR }
}

/**
 * Which personal lines the numbers back up. Each has a floor so a line only
 * appears when it actually stings — "2 minutes a day" is not an indictment.
 */
object PersonalTidbits {
    const val MIN_AVG_DAILY_MINUTES = 10
    const val MIN_TODAY_MINUTES = 15
    const val MIN_OPENS_TODAY = 4
    const val MIN_WEEK_MINUTES = 60
    const val MIN_YEAR_HOURS = 24

    fun candidates(u: PersonalUsage): List<PersonalLine> = buildList {
        // An "average" from a single day is just today — leave that to TODAY.
        if (u.daysOfHistory >= 2 && u.avgDailyMinutes >= MIN_AVG_DAILY_MINUTES) {
            add(PersonalLine(PersonalLine.Kind.DAILY_AVERAGE, u.avgDailyMinutes))
            val yearHours = u.avgDailyMinutes * 365 / 60
            if (yearHours >= MIN_YEAR_HOURS) add(PersonalLine(PersonalLine.Kind.YEAR, yearHours))
        }
        if (u.todayMinutes >= MIN_TODAY_MINUTES) {
            add(PersonalLine(PersonalLine.Kind.TODAY, u.todayMinutes))
        }
        if (u.opensToday >= MIN_OPENS_TODAY) {
            add(PersonalLine(PersonalLine.Kind.OPENS_TODAY, u.opensToday))
        }
        if (u.daysOfHistory >= 2 && u.weekMinutes >= MIN_WEEK_MINUTES) {
            add(PersonalLine(PersonalLine.Kind.WEEK, u.weekMinutes))
        }
    }
}
