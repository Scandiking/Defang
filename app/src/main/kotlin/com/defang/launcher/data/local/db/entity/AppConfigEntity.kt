package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-app configuration stored in Room.
 *
 * tier:
 *   0 = UTILITY  — zero friction, opens instantly
 *   1 = WATCHED  — intent gate + session timer + cool-down
 *
 * sessionLimitMinutes: how long before the end card appears (default 15)
 * cooldownMinutes: how long the cool-down lockout lasts (default 30)
 * gateDelaySeconds: countdown on the intent gate (default 8)
 */
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val tier: Int = 0,
    val sessionLimitMinutes: Int = 15,
    val cooldownMinutes: Int = 30,
    val gateDelaySeconds: Int = 8,
    /** Epoch millis when the cool-down ends, 0 if not in cool-down */
    val cooldownEndsAt: Long = 0L,
)
