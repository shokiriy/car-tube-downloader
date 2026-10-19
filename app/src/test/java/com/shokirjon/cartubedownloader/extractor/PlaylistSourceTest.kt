package com.shokirjon.cartubedownloader.extractor

import com.shokirjon.cartubedownloader.core.YouTubeTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSourceTest {
    @Test fun mixUsesVideoWatchPageWithPlaylistContext() {
        val mix = YouTubeTarget.Playlist("RDabcdefghijk", "https://www.youtube.com/playlist?list=RDabcdefghijk")
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&list=RDabcdefghijk", PlaylistSource.forExtractor(mix))
    }

    @Test fun normalPlaylistAndUnknownMixStayCanonical() {
        val regular = YouTubeTarget.Playlist("PLabcdefghij", "https://www.youtube.com/playlist?list=PLabcdefghij")
        val unknown = YouTubeTarget.Playlist("RDMMabcdefghijk", "https://www.youtube.com/playlist?list=RDMMabcdefghijk")
        assertEquals(regular.canonicalUrl, PlaylistSource.forExtractor(regular))
        assertEquals(unknown.canonicalUrl, PlaylistSource.forExtractor(unknown))
    }
}
