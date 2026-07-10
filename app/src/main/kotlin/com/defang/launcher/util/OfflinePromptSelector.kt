package com.defang.launcher.util

import android.content.Context
import com.defang.launcher.R
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects offline activity prompts, balancing categories across the day.
 * "Give me another one" cycles without repeating within the same session-end.
 *
 * The user's own tasks (Settings > Your own tasks) form an extra category
 * that competes on equal footing with the built-in ones when non-empty.
 */
@Singleton
class OfflinePromptSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    prefs: PreferencesDataStore,
) {
    enum class Category(val arrayResId: Int) {
        MOVEMENT(R.array.offline_prompts_movement),
        SPACE(R.array.offline_prompts_space),
        SENSORY(R.array.offline_prompts_sensory),
        SOCIAL(R.array.offline_prompts_social),
        CREATIVE(R.array.offline_prompts_creative),
        CUSTOM(0),
    }

    private val sessionShown = mutableSetOf<String>()
    private val dailyCategoryCount = mutableMapOf<Category, Int>()

    // Kept current by the collector below; read from the overlay path without suspending.
    @Volatile
    private var customTasks: List<String> = emptyList()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefs.customTasks.collect { customTasks = it }
        }
    }

    /** Call when a new end-card or cool-down starts to reset the within-session set. */
    fun resetSession() = sessionShown.clear()

    fun next(): String {
        val available = Category.entries.filter { poolFor(it).isNotEmpty() }

        // Pick least-used category today (with randomness on ties)
        val category = available
            .minByOrNull { dailyCategoryCount.getOrDefault(it, 0) + (0..2).random() }
            ?: available.random()

        val pool = poolFor(category)
        val candidates = pool.filter { it !in sessionShown }
        val finalCandidates = candidates.ifEmpty {
            sessionShown.clear()
            pool
        }
        val prompt = finalCandidates.random()
        sessionShown.add(prompt)
        dailyCategoryCount[category] = dailyCategoryCount.getOrDefault(category, 0) + 1
        return prompt
    }

    private fun poolFor(category: Category): List<String> =
        if (category == Category.CUSTOM) customTasks
        else context.resources.getStringArray(category.arrayResId).toList()
}
