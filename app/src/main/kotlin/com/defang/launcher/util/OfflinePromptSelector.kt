package com.defang.launcher.util

import android.content.Context
import com.defang.launcher.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects offline activity prompts, balancing categories across the day.
 * "Give me another one" cycles without repeating within the same session-end.
 */
@Singleton
class OfflinePromptSelector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Category(val arrayResId: Int) {
        MOVEMENT(R.array.offline_prompts_movement),
        SPACE(R.array.offline_prompts_space),
        SENSORY(R.array.offline_prompts_sensory),
        SOCIAL(R.array.offline_prompts_social),
        CREATIVE(R.array.offline_prompts_creative),
    }

    private val sessionShown = mutableSetOf<String>()
    private val dailyCategoryCount = mutableMapOf<Category, Int>()

    /** Call when a new end-card or cool-down starts to reset the within-session set. */
    fun resetSession() = sessionShown.clear()

    fun next(): String {
        // Pick least-used category today (with randomness on ties)
        val category = Category.entries
            .minByOrNull { dailyCategoryCount.getOrDefault(it, 0) + (0..2).random() }
            ?: Category.entries.random()

        val pool = context.resources.getStringArray(category.arrayResId)
        val candidates = pool.filter { it !in sessionShown }
        val finalCandidates = candidates.ifEmpty {
            sessionShown.clear()
            pool.toList()
        }
        val prompt = finalCandidates.random()
        sessionShown.add(prompt)
        dailyCategoryCount[category] = dailyCategoryCount.getOrDefault(category, 0) + 1
        return prompt
    }
}
