package com.shokirjon.cartubedownloader.core

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun watchAndTrackingNormalization() {
        val v = YouTubeUrlParser.parse("https://m.youtube.com/watch?v=abcdefghijk&utm_source=share") as YouTubeTarget.Video
        assertEquals("abcdefghijk", v.id)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", v.canonicalUrl)
    }
    @Test fun shortAndSharedLinks() {
        assertEquals("abcdefghijk", (YouTubeUrlParser.parse("youtu.be/abcdefghijk?t=4") as YouTubeTarget.Video).id)
        assertEquals("abcdefghijk", (YouTubeUrlParser.parse("https://www.youtube.com/shorts/abcdefghijk") as YouTubeTarget.Video).id)
    }
    @Test fun rejectUntrustedAndMalformed() {
        listOf("https://youtube.com.evil.test/watch?v=abcdefghijk", "https://www.youtube.com/watch?v=short", "file:///tmp/x", "https://x@youtubE.com/watch?v=abcdefghijk").forEach {
            assertThrows(IllegalArgumentException::class.java) { YouTubeUrlParser.parse(it) }
        }
    }
    @Test fun playlistParsingAndBatchDedup() {
        val p = YouTubeUrlParser.parse("https://www.youtube.com/playlist?list=PLabcdefghij&utm=1") as YouTubeTarget.Playlist
        assertEquals("PLabcdefghij", p.id)
        val list = YouTubeUrlParser.parseMany("https://youtu.be/abcdefghijk\nhttps://www.youtube.com/watch?v=abcdefghijk&si=xyz\n${p.canonicalUrl}")
        assertEquals(2, list.size)
        assertTrue(list[1] is YouTubeTarget.Playlist)
    }
    @Test fun sanitizeTraversalAndControl() {
        assertEquals("a b c", FileNames.sanitize("../a/b:c"))
        assertEquals("CarTube media", FileNames.sanitize(".*?"))
        assertTrue(FileNames.sanitize("x".repeat(200)).length <= 120)
    }
    @Test fun compatibleMp4Only() {
        val formats = listOf(
            MediaFormat("a", "mp4", 480, "avc1.42", "mp4a.40", 12),
            MediaFormat("b", "webm", 480, "vp9", "opus", 12),
            MediaFormat("c", "mp4", 720, "avc1.64", "mp4a.40", 12),
            MediaFormat("d", "mp4", 360, "hev1", "mp4a", 12))
        assertEquals(listOf("a"), FormatSelector.compatible(formats, 480).map { it.id })
        assertTrue(FormatSelector.mp4(480).contains("[height<=480]"))
    }
    @Test fun unsupportedAbiAndStorageReserve() {
        assertFalse(RuntimeChecks.supportsAny(arrayOf("x86", "x86_64")))
        assertTrue(RuntimeChecks.supportsAny(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(RuntimeChecks.hasWorkingSpace(1_073_741_824L, 500L * 1024 * 1024, 1_073_741_824L))
        assertTrue(RuntimeChecks.hasWorkingSpace(4_000_000_000L, 500L * 1024 * 1024, 1_073_741_824L))
    }
    @Test fun progressBytesSpeedAndEta() {
        val p = ProgressParser.parse("[download]  12.5% of 4.00MiB at 512.0KiB/s ETA 00:07")
        assertEquals(12.5f, p.percent!!, 0.01f)
        assertEquals(4194304L, p.total)
        assertEquals(524288L, p.downloaded)
        assertEquals(524288L, p.speed)
        assertEquals(7L, p.etaSeconds)
        assertNotNull(ProgressParser.parse("[ExtractAudio] ffmpeg").stage)
    }
}
