package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.AppConfigEntity

/** Domain model — decoupled from Room entity. */
data class AppConfig(
    val packageName: String,
    val appLabel: String,
    val tier: AppTier,
    val sessionLimitMinutes: Int,
    val cooldownMinutes: Int,
    val gateDelaySeconds: Int,
    val cooldownEndsAt: Long,
) {
    val isInCooldown: Boolean get() = cooldownEndsAt > System.currentTimeMillis()
}

fun AppConfigEntity.toDomain() = AppConfig(
    packageName = packageName,
    appLabel = appLabel,
    tier = AppTier.fromDbValue(tier),
    sessionLimitMinutes = sessionLimitMinutes,
    cooldownMinutes = cooldownMinutes,
    gateDelaySeconds = gateDelaySeconds,
    cooldownEndsAt = cooldownEndsAt,
)

fun AppConfig.toEntity() = AppConfigEntity(
    packageName = packageName,
    appLabel = appLabel,
    tier = tier.dbValue,
    sessionLimitMinutes = sessionLimitMinutes,
    cooldownMinutes = cooldownMinutes,
    gateDelaySeconds = gateDelaySeconds,
    cooldownEndsAt = cooldownEndsAt,
)
