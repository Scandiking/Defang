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
        // Top-ranked sites per theporndude.com category listings, 2026-07-13
        "hqporner", "beeg", "yourporn", "motherless", "xmoviesforyou",
        "youjizz", "pornone", "3movs", "porndig", "cumlouder", "txxx",
        "porndoe", "pornhat", "ok.xxx", "porn00",
        "camsoda", "stripchat", "sinparty", "livejasmin", "streamate",
        "bongacams", "jerkmate", "cam4", "myfreecams", "flirtify",
        "flirtbate", "amateurtv", "xlovecam", "imlive",
        "bangbros", "spicevids", "iknowthatgirl", "letsdoeit", "adulttime",
        "faphouse", "candyai", "hentaiedpro", "teamskeet", "mylf",
        "puretaboo", "naughtyamerica", "girlfriendgpt", "mofos", "evilangel",
        "vixen", "adultprime", "pornbox", "familystrokes", "swappz",
        "javhd", "czechav",
        "tikporn", "fikfap", "fyptt", "kwiky", "xfree", "xxxtik",
        "xxxfollow", "ogfap", "avrebo", "sharesome",
        "fapello", "simpcity", "pimpbunny", "coomerparty", "erothots",
        "xxbrits", "leakgallery", "porn4fans", "porntn", "thothub",
        "borntobefuck",
        "vrporn", "povr", "sexlikereal", "vrbangers", "virtualrealporn",
        "badoinkvr", "braindancevr", "virtualtaboo", "darkroomvr", "wankzvr",
        "wifebucket", "exploitedcollegegirls", "netvideogirls", "trueamateurs",
        "backroomcastingcouch", "czechcasting", "bangbus", "privatesociety",
        "woodmancastingx", "lovehomeporn", "letspostit",
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
