package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-added website to gate in the browser.
 *
 * pattern: normalized host, optionally with a path prefix — lowercase, no
 *   scheme, no leading "www.", no trailing slash. Examples:
 *     "9gag.com"            → matches any path on 9gag.com (and sub-domains)
 *     "funnyjunk.com/nsfw"  → matches only paths starting with /nsfw
 *
 * isAdult: true → the 1-minute adult leash + adult tidbits + no extension;
 *   false → the standard watched treatment (15 min session / 30 min cool-down).
 *
 * cooldownEndsAt: epoch millis when this pattern's cool-down ends, 0 if none.
 *   Keyed per-pattern so cooling down one site doesn't lock out another in the
 *   same browser.
 */
@Entity(tableName = "watched_url")
data class WatchedUrlEntity(
    @PrimaryKey val pattern: String,
    val label: String,
    val isAdult: Boolean = false,
    val enabled: Boolean = true,
    val cooldownEndsAt: Long = 0L,
)
