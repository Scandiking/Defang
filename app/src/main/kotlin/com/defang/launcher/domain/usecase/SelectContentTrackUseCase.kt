package com.defang.launcher.domain.usecase

import com.defang.launcher.domain.model.ContentTrack
import javax.inject.Inject

/** Maps a package name or browser domain to the appropriate awareness content track. */
class SelectContentTrackUseCase @Inject constructor() {

    private val socialPackages = setOf(
        "com.instagram.android",
        "com.snapchat.android",
        "com.zhiliaoapp.musically",       // TikTok
        "com.ss.android.ugc.trill",       // TikTok (some regions)
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.X.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.pinterest",
        "com.tumblr",
    )

    private val socialDomains = setOf(
        "instagram.com", "www.instagram.com",
        "twitter.com", "x.com",
        "reddit.com", "www.reddit.com",
        "facebook.com", "www.facebook.com",
        "tiktok.com", "www.tiktok.com",
        "snapchat.com", "www.snapchat.com",
        "youtube.com", "www.youtube.com",
        "pinterest.com", "www.pinterest.com",
        "tumblr.com", "www.tumblr.com",
    )

    fun forPackage(packageName: String): ContentTrack =
        if (packageName in socialPackages) ContentTrack.SOCIAL else ContentTrack.GENERAL

    fun forDomain(domain: String): ContentTrack = when {
        domain in socialDomains -> ContentTrack.SOCIAL
        // Adult domains are user-configurable in Phase 2; hardcode a minimal default for now
        domain.contains("pornhub") || domain.contains("xvideos") || domain.contains("xnxx") ->
            ContentTrack.ADULT
        else -> ContentTrack.GENERAL
    }
}
