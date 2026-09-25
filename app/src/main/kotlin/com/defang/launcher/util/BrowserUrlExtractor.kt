package com.defang.launcher.util

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the current URL from the address bar of supported browsers
 * via the accessibility window tree.
 *
 * Only reads URL bar nodes — never reads page content.
 *
 * Supported browsers and their URL bar view IDs:
 *   Chrome / Chromium-based   → url_bar
 *   Vanadium (GrapheneOS)     → url_bar
 *   Firefox (Play Store)      → mozac_browser_toolbar_url_view
 *   Firefox Fenix (F-Droid)   → mozac_browser_toolbar_url_view
 *   IronFox                   → mozac_browser_toolbar_url_view
 *   Samsung Internet          → location_bar_edit_text
 *   Edge, Brave, Opera        → url_bar / url_field
 *   DuckDuckGo                → omnibarTextInput
 *   Fulguris                  → search
 *   Other Chromium forks      → url_bar (Vivaldi, Kiwi, Bromite, Cromite, Ecosia, …)
 *   Other Mozilla forks       → mozac_browser_toolbar_url_view (Focus, Iceraven, Mull, Tor, …)
 */
@Singleton
class BrowserUrlExtractor @Inject constructor() {

    /** Maps package name → ordered list of view IDs to try for the URL bar. */
    private val urlBarIds: Map<String, List<String>> = mapOf(
        "com.android.chrome"            to listOf("com.android.chrome:id/url_bar"),
        "com.chrome.beta"               to listOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev"                to listOf("com.chrome.dev:id/url_bar"),
        "com.chrome.canary"             to listOf("com.chrome.canary:id/url_bar"),
        // Vanadium — GrapheneOS's hardened Chromium build, same view IDs as Chrome.
        "app.vanadium.browser"          to listOf("app.vanadium.browser:id/url_bar"),
        "org.mozilla.firefox"           to listOf(
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title",
        ),
        "org.mozilla.firefox_beta"      to listOf(
            "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
        ),
        // Fenix (Firefox for Android) F-Droid / GitHub build — different package ID
        // than the Play Store build (org.mozilla.firefox) but shares the same view IDs.
        "org.mozilla.fenix"             to listOf(
            "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        ),
        "org.mozilla.fennec_fdroid"     to listOf(
            "org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_url_view",
        ),
        // IronFox — hardened Fenix fork, same Mozilla Components view IDs.
        "org.ironfoxoss.ironfox"        to listOf(
            "org.ironfoxoss.ironfox:id/mozac_browser_toolbar_url_view",
        ),
        "org.ironfoxoss.ironfox.nightly" to listOf(
            "org.ironfoxoss.ironfox.nightly:id/mozac_browser_toolbar_url_view",
        ),
        "com.sec.android.app.sbrowser"  to listOf(
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/url_bar",
        ),
        "com.microsoft.emmx"            to listOf("com.microsoft.emmx:id/url_bar"),
        "com.brave.browser"             to listOf("com.brave.browser:id/url_bar"),
        "com.opera.browser"             to listOf("com.opera.browser:id/url_field"),
        "com.opera.mini.native"         to listOf("com.opera.mini.native:id/url_field"),
        "com.duckduckgo.mobile.android" to listOf(
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
        ),
        // Fulguris — own WebView-based UI; address bar is fulguris.view.SearchView
        // (@id/search in layout/search.xml). Package = net.slions.fulguris
        // + .full/.lite + .fdroid/.playstore/.download build flavors.
        *listOf("full", "lite").flatMap { version ->
            listOf("fdroid", "playstore", "download").map { publisher ->
                "net.slions.fulguris.$version.$publisher".let { it to listOf("$it:id/search") }
            }
        }.toTypedArray(),
        // Chromium forks that keep Chrome's toolbar. An unverified ID is harmless:
        // extractUrl() falls back to the BFS scan when the fast path misses, so
        // being listed here is what matters — it's what marks the package a browser.
        *listOf(
            "com.microsoft.emmx.beta", "com.microsoft.emmx.dev", "com.microsoft.emmx.canary",
            "com.brave.browser_beta", "com.brave.browser_nightly",
            "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
            "com.kiwibrowser.browser",
            "org.bromite.bromite",
            "org.cromite.cromite",
            "org.chromium.chrome",          // ungoogled-chromium and plain Chromium builds
            "com.ecosia.android",
            "com.sec.android.app.sbrowser.beta",
        ).map { chromium(it) }.toTypedArray(),
        // Mozilla Components (Fenix/Focus) derivatives.
        *listOf(
            "org.mozilla.focus", "org.mozilla.klar",
            "io.github.forkmaintainers.iceraven",
            "us.spotco.fennec_dos",         // Mull
            "net.waterfox.android.release",
            "org.torproject.torbrowser",
        ).map { mozilla(it) }.toTypedArray(),
        "com.opera.browser.beta"        to listOf("com.opera.browser.beta:id/url_field"),
    )

    private fun chromium(pkg: String) = pkg to listOf("$pkg:id/url_bar")

    private fun mozilla(pkg: String) = pkg to listOf("$pkg:id/mozac_browser_toolbar_url_view")

    /** All package names we treat as browsers. */
    val browserPackages: Set<String> get() = urlBarIds.keys

    // Throttle: don't traverse the window tree more than once per 600ms per browser.
    // TYPE_WINDOW_CONTENT_CHANGED fires very frequently; without this we'd hammer the tree.
    private val lastExtractMs = mutableMapOf<String, Long>()
    private val THROTTLE_MS = 600L

    /**
     * Returns true if we should skip URL extraction for [pkg] right now (too soon
     * since the last extraction). Updates the timestamp when returning false.
     */
    fun isThrottled(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastExtractMs[pkg] ?: 0L
        if (now - last < THROTTLE_MS) return true
        lastExtractMs[pkg] = now
        return false
    }

    /** Reset throttle for a package so the next call always extracts immediately. */
    fun resetThrottle(pkg: String) { lastExtractMs.remove(pkg) }

    /**
     * Whether a given viewId resource name belongs to a URL bar in any
     * supported browser. Can be used as a fast pre-check on event.source.
     */
    fun isUrlBarViewId(viewId: String?): Boolean =
        viewId != null && urlBarIds.values.any { ids -> viewId in ids }

    /**
     * Reads the current URL from [packageName]'s URL bar by traversing the
     * active window tree. Returns null if the browser is not supported, the
     * node is not found, or the text is blank / doesn't look like a URL.
     *
     * Strategy:
     *  1. Try known view IDs (fast path).
     *  2. Fall back to a BFS scan for any EditText whose text looks like a URL
     *     (handles browsers with unknown or changed IDs).
     *
     * Must be called on the main thread (same thread that delivers accessibility events).
     */
    fun extractUrl(service: AccessibilityService, packageName: String): String? {
        if (packageName !in urlBarIds && packageName !in browserPackages) return null
        val root = service.rootInActiveWindow ?: return null
        return try {
            // Fast path: known view ID
            val ids = urlBarIds[packageName]
            if (ids != null) {
                for (id in ids) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) {
                        val text = nodes[0].text?.toString()
                        nodes.forEach { it.recycle() }
                        if (!text.isNullOrBlank() && looksLikeUrl(text)) return text
                    }
                }
            }
            // Slow fallback: BFS for any URL-like EditText node
            findUrlInTree(root)
        } finally {
            root.recycle()
        }
    }

    /** Extracts the lowercase hostname from a raw URL string. */
    fun extractHostname(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return try {
            val withScheme = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"
            java.net.URI(withScheme).host?.lowercase()
        } catch (_: Exception) {
            Regex("""(?:https?://)?(?:www\.)?([a-zA-Z0-9\-]+\.[a-zA-Z]{2,})""")
                .find(rawUrl)?.groupValues?.get(1)?.lowercase()
        }
    }

    /**
     * Splits a raw URL into (host, path), both lowercased, with the leading
     * "www." stripped from the host so it matches host-suffix patterns. Path
     * keeps its leading slash ("/" when absent). Returns null if no host.
     */
    fun extractHostAndPath(rawUrl: String?): Pair<String, String>? {
        if (rawUrl.isNullOrBlank()) return null
        return try {
            val withScheme = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"
            val uri = java.net.URI(withScheme)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val path = uri.path?.lowercase()?.ifEmpty { "/" } ?: "/"
            host to path
        } catch (_: Exception) {
            // Fall back to the hostname regex; path is unknown so treat as root.
            val host = extractHostname(rawUrl)?.removePrefix("www.") ?: return null
            host to "/"
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun looksLikeUrl(text: String): Boolean =
        text.contains('.') && !text.contains(' ') && text.length < 300

    /**
     * BFS through the accessibility tree looking for a text field that contains
     * something that looks like a URL. Recycles all traversed nodes.
     */
    private fun findUrlInTree(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = mutableListOf<AccessibilityNodeInfo>()
        queue.add(root)
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                visited.add(node)
                val text = node.text?.toString()
                if (!text.isNullOrBlank() &&
                    node.className?.contains("EditText") == true &&
                    looksLikeUrl(text)) {
                    return text
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } finally {
            // Recycle everything except root (caller recycles root)
            visited.drop(1).forEach { it.recycle() }
            queue.forEach { it.recycle() }
        }
        return null
    }
}
