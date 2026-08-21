package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.AdaptiveGateStateDao
import com.defang.launcher.data.local.db.entity.AdaptiveGateStateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptive gate-threshold escalation (issue #16, opt-in). Reopening the same
 * gate within [escalateWindowMs] of the last open raises its level by one
 * (capped at [maxLevel]); the level decays back down by one per
 * [decayIntervalMs] of elapsed time since, computed lazily on the next touch
 * rather than via a background job — there is nothing to decay toward until
 * the gate is checked again anyway.
 */
@Singleton
class AdaptiveGateRepository @Inject constructor(
    private val dao: AdaptiveGateStateDao,
) {
    /**
     * Records a gate trigger (an open attempt, not just a successful unlock —
     * that's the reopening behavior the feature targets) and returns the
     * resulting level.
     */
    suspend fun recordOpenAndGetLevel(
        gateKey: String,
        escalateWindowMs: Long,
        decayIntervalMs: Long,
        maxLevel: Int,
    ): Int {
        val now = System.currentTimeMillis()
        val prev = dao.get(gateKey)
        val level = nextLevel(prev, now, escalateWindowMs, decayIntervalMs, maxLevel)
        dao.upsert(AdaptiveGateStateEntity(gateKey, level, now))
        return level
    }

    /** Read-only decay-adjusted level, for display without recording an open. */
    suspend fun peekLevel(gateKey: String, decayIntervalMs: Long, maxLevel: Int): Int {
        val prev = dao.get(gateKey) ?: return 0
        val now = System.currentTimeMillis()
        val decaySteps = ((now - prev.lastOpenAtMs) / decayIntervalMs).toInt()
        return (prev.level - decaySteps).coerceIn(0, maxLevel)
    }

    /** The user's manual override: reset every gate's escalation to baseline. */
    suspend fun resetAll() = dao.deleteAll()

    private fun nextLevel(
        prev: AdaptiveGateStateEntity?,
        now: Long,
        escalateWindowMs: Long,
        decayIntervalMs: Long,
        maxLevel: Int,
    ): Int {
        if (prev == null) return 0
        val elapsed = now - prev.lastOpenAtMs
        val decaySteps = (elapsed / decayIntervalMs).toInt()
        var level = (prev.level - decaySteps).coerceAtLeast(0)
        if (elapsed < escalateWindowMs) level = (level + 1).coerceAtMost(maxLevel)
        return level
    }
}
