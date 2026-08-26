package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One session = one intent-gate pass through a watched app.
 *
 * intentDeclared: the string the user tapped (or null if they just waited out the countdown)
 * extensionUsed: true if the user used their daily extension during this session
 * endTime: epoch millis, 0 while session is still active
 * watchedPattern: the browser watch-pattern/domain key for a watched-site
 *   session (null for a watched-app session) — needed to recover an
 *   in-progress session's cooldown target after an unexpected process death
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val intentDeclared: String? = null,
    val extensionUsed: Boolean = false,
    val watchedPattern: String? = null,
)
