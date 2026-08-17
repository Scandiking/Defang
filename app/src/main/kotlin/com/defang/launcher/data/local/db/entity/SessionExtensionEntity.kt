package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One gate-extension justification. Links the session that was extended to
 * the follow-on session it produced, so the reason text can be reviewed
 * later against what actually happened (issue #17 — was "I need more time"
 * genuine or just impulse?), without overloading [SessionEntity.intentDeclared]
 * the way the old "Extension: $reason" prefix hack did.
 */
@Entity(tableName = "session_extension")
data class SessionExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val extendedSessionId: Long,
    val newSessionId: Long,
    val reason: String,
    val timestamp: Long,
)
