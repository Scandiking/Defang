package com.defang.launcher.util

import android.content.Context
import com.defang.launcher.R
import com.defang.launcher.domain.model.ContentTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects awareness tidbits pseudo-randomly with same-day no-repeat.
 *
 * Rules:
 * - Never show the same tidbit twice in the same calendar day.
 * - Never show the same tidbit twice in a row (across days).
 * - Rotate across the full track library before repeating.
 */
@Singleton
class TidbitSelector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // State held in memory — survives process death infrequently; acceptable.
    private val shownToday: MutableMap<ContentTrack, MutableSet<Int>> = mutableMapOf()
    private var lastDate: LocalDate? = null
    private var lastIndex: Map<ContentTrack, Int> = emptyMap()

    fun next(track: ContentTrack): String {
        val today = LocalDate.now()
        if (today != lastDate) {
            shownToday.clear()
            lastDate = today
        }
        val library = libraryFor(track)
        val shown = shownToday.getOrPut(track) { mutableSetOf() }
        val last = lastIndex[track] ?: -1

        // Candidates: not shown today, not last used; if all exhausted, reset daily set
        val candidates = library.indices.filter { it !in shown && it != last }
        val finalCandidates = candidates.ifEmpty {
            shown.clear()
            library.indices.filter { it != last }
        }
        val chosen = finalCandidates.random()
        shown.add(chosen)
        lastIndex = lastIndex + (track to chosen)
        return library[chosen]
    }

    private fun libraryFor(track: ContentTrack): Array<String> {
        val resId = when (track) {
            ContentTrack.GENERAL -> R.array.tidbits_general
            ContentTrack.SOCIAL  -> R.array.tidbits_social
            ContentTrack.ADULT   -> R.array.tidbits_adult
        }
        return context.resources.getStringArray(resId)
    }
}
