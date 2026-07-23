package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.WatchedUrlEntity

/**
 * A user-configured watched website. [pattern] is the stored key (normalized
 * host + optional path prefix); [host] and [pathPrefix] are the parsed pieces
 * used for matching a live URL.
 */
data class WatchedUrl(
    val pattern: String,
    val label: String,
    val isAdult: Boolean,
    val enabled: Boolean,
    val cooldownEndsAt: Long,
) {
    /** Host portion of [pattern], e.g. "funnyjunk.com". */
    val host: String = pattern.substringBefore('/')

    /**
     * Path prefix portion of [pattern] including its leading slash, e.g.
     * "/nsfw"; empty when the pattern is host-only.
     */
    val pathPrefix: String =
        pattern.substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }

    /**
     * True when [urlHost]/[urlPath] fall under this pattern:
     *  - host matches exactly or as a sub-domain (host-suffix), and
     *  - path starts with [pathPrefix] (or the pattern is host-only).
     *
     * [urlHost] and [urlPath] are expected already lowercased.
     */
    fun matches(urlHost: String, urlPath: String): Boolean {
        val hostOk = urlHost == host || urlHost.endsWith(".$host")
        val pathOk = pathPrefix.isEmpty() || urlPath.startsWith(pathPrefix)
        return hostOk && pathOk
    }

    fun toEntity() = WatchedUrlEntity(
        pattern = pattern,
        label = label,
        isAdult = isAdult,
        enabled = enabled,
        cooldownEndsAt = cooldownEndsAt,
    )

    companion object {
        /**
         * Normalizes raw user input into a storable [pattern]: lowercase, drop
         * the scheme and a leading "www.", strip a trailing slash, and collapse
         * to host (+ optional path). Returns null if no host survives.
         */
        fun normalize(raw: String): String? {
            var s = raw.trim().lowercase()
            if (s.isEmpty()) return null
            s = s.substringAfter("://", s)          // drop scheme if present
            s = s.removePrefix("www.")
            s = s.trimEnd('/')
            // Guard: a bare word with no dot isn't a host we can match on.
            val host = s.substringBefore('/')
            if (host.isEmpty() || !host.contains('.')) return null
            return s
        }
    }
}

fun WatchedUrlEntity.toDomain() = WatchedUrl(
    pattern = pattern,
    label = label,
    isAdult = isAdult,
    enabled = enabled,
    cooldownEndsAt = cooldownEndsAt,
)
