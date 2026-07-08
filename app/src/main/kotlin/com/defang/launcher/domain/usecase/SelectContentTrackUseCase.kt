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

    private val adultPackages = setOf(
        "com.pornhub.pornhub",
        "com.xvideos.app",
        "com.xhamster.android",
        "com.xnxx.app",
        "com.onlyfans.app",
        "com.fancentro.android",
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

    private val adultDomainKeywords = listOf(
        "pornhub", "xvideos", "xhamster", "xnxx", "onlyfans",
        "brazzers", "redtube", "youporn", "tube8", "spankbang",
        "eporner", "thisvid", "porntrex", "tnaflix",
    )

    fun forPackage(packageName: String): ContentTrack = when {
        packageName in adultPackages -> ContentTrack.ADULT
        packageName in socialPackages -> ContentTrack.SOCIAL
        else -> ContentTrack.GENERAL
    }

    fun forDomain(domain: String): ContentTrack = when {
        adultDomainKeywords.any { domain.contains(it) } -> ContentTrack.ADULT
        domain in socialDomains -> ContentTrack.SOCIAL
        else -> ContentTrack.GENERAL
    }
}
