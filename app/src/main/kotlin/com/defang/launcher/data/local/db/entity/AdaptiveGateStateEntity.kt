package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Adaptive-threshold escalation state for one gate (issue #16). Keyed by
 * gateKey — the package name for apps, the watch pattern for browser sites —
 * matching the keying already used for pendingGate/activeGateKey in
 * DefangAccessibilityService. One table for both, rather than duplicating the
 * fields across AppConfigEntity and WatchedUrlEntity the way cooldown is.
 *
 * level: current escalation step (0 = baseline, capped at a fixed max).
 * lastOpenAtMs: when the level was last touched — used to compute both the
 * next escalation (reopened within the escalate window?) and decay (how many
 * decay intervals have elapsed since).
 */
@Entity(tableName = "adaptive_gate_state")
data class AdaptiveGateStateEntity(
    @PrimaryKey val gateKey: String,
    val level: Int = 0,
    val lastOpenAtMs: Long = 0L,
)
