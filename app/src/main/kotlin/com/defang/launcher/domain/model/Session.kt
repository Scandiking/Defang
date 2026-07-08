package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.SessionEntity

data class Session(
    val id: Long,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val intentDeclared: String?,
    val extensionUsed: Boolean,
) {
    val durationMs: Long get() = if (endTime > 0) endTime - startTime else 0L
    val isActive: Boolean get() = endTime == 0L
}

fun SessionEntity.toDomain() = Session(
    id = id,
    packageName = packageName,
    startTime = startTime,
    endTime = endTime,
    intentDeclared = intentDeclared,
    extensionUsed = extensionUsed,
)
