package com.defang.launcher.util

import com.defang.launcher.util.BrowserUrlExtractor.Companion.urlFromBarText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlExtractorTest {

    @Test
    fun plainUrlPassesThrough() {
        assertEquals("example.com", urlFromBarText("example.com"))
        assertEquals("https://example.com/a/b", urlFromBarText("https://example.com/a/b"))
    }

    @Test
    fun chromeInsecureSuffixIsDropped() {
        // Seen on an S22 (Norwegian locale) for a page that failed to load
        assertEquals(
            "pornhub.invalid",
            urlFromBarText("pornhub.invalid. Tilkoblingen er ikke sikker"),
        )
        assertEquals("example.com/path", urlFromBarText("example.com/path. Not secure"))
    }

    @Test
    fun fenixComposeContentDescIsParsed() {
        assertEquals(
            "pornhub.invalid",
            urlFromBarText(" pornhub.invalid. Søk eller skriv inn adresse"),
        )
    }

    @Test
    fun searchTextIsNotAUrl() {
        assertNull(urlFromBarText("how to fix a.b problems"))
        assertNull(urlFromBarText("hello world"))
        assertNull(urlFromBarText("nodot"))
        assertNull(urlFromBarText(""))
        assertNull(urlFromBarText(null))
    }
}
