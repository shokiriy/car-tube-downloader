package com.shokirjon.cartubedownloader.ui

import org.junit.Assert.*
import org.junit.Test

class ThumbnailCandidatesTest {
    @Test fun extractedHttpsUrlIsFirstWithValidatedFallbacks() {
        val urls = ThumbnailCandidates.urls("https://i.ytimg.com/vi/M7lc1UVf-VE/mqdefault.jpg", "M7lc1UVf-VE")
        assertEquals("https://i.ytimg.com/vi/M7lc1UVf-VE/mqdefault.jpg", urls.first())
        assertEquals(2, urls.size)
        assertTrue(urls[1].endsWith("hqdefault.jpg"))
    }
    @Test fun missingOrInsecureUrlFallsBackToValidatedVideoId() {
        assertEquals(2, ThumbnailCandidates.urls(null, "M7lc1UVf-VE").size)
        assertTrue(ThumbnailCandidates.urls("http://example.org/x.jpg", "M7lc1UVf-VE").all { it.startsWith("https://i.ytimg.com/") })
        assertTrue(ThumbnailCandidates.urls("javascript:alert(1)", "invalid").isEmpty())
    }
}
