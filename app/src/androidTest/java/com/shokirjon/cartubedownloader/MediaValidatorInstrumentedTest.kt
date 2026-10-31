package com.shokirjon.cartubedownloader

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteBuffer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shokirjon.cartubedownloader.core.OutputKind
import com.shokirjon.cartubedownloader.storage.MediaValidator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaValidatorInstrumentedTest {
    @Test fun acceptsMp4WithKeyframeLargerThanFourKilobytes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = instrumentation.targetContext.cacheDir.resolve("synthetic-h264-aac.mp4")
        try {
            instrumentation.context.assets.open("media/synthetic-h264-aac.mp4").use { source ->
                output.outputStream().use { destination -> source.copyTo(destination) }
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val videoIndex = (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "video/avc"
                }
                extractor.selectTrack(videoIndex)
                val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) extractor.sampleSize
                    else extractor.readSampleData(ByteBuffer.allocate(64 * 1024), 0).toLong()
                assertTrue("Fixture must exercise the former 4 KiB buffer limit", bytes > 4096L)
            } finally { extractor.release() }
            MediaValidator().validate(output, OutputKind.MP4)
        } finally { output.delete() }
    }
}
