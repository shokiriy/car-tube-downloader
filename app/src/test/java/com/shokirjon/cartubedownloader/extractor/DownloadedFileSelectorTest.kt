package com.shokirjon.cartubedownloader.extractor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadedFileSelectorTest {
    @Test fun selectsOnlyFinishedMp4AndNeverAnIntermediateVideoStream() {
        val directory = Files.createTempDirectory("cartube-output").toFile()
        try {
            File(directory, "abcdefghijk.f137.mp4").writeBytes(ByteArray(4096))
            File(directory, "abcdefghijk.f140.m4a").writeBytes(ByteArray(4096))
            assertNull(DownloadedFileSelector.finalFile(directory, "abcdefghijk", "mp4"))
            val finished = File(directory, "abcdefghijk.mp4").apply { writeBytes(ByteArray(4096)) }
            assertEquals(finished, DownloadedFileSelector.finalFile(directory, "abcdefghijk", "mp4"))
            finished.writeBytes(ByteArray(20))
            assertNull(DownloadedFileSelector.finalFile(directory, "abcdefghijk", "mp4"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun acceptsOnlyExactFinishedMp3() {
        val directory = Files.createTempDirectory("cartube-audio").toFile()
        try {
            File(directory, "abcdefghijk.m4a").writeBytes(ByteArray(4096))
            assertNull(DownloadedFileSelector.finalFile(directory, "abcdefghijk", "mp3"))
            val finished = File(directory, "abcdefghijk.mp3").apply { writeBytes(ByteArray(4096)) }
            assertEquals(finished, DownloadedFileSelector.finalFile(directory, "abcdefghijk", "mp3"))
        } finally { directory.deleteRecursively() }
    }
}
