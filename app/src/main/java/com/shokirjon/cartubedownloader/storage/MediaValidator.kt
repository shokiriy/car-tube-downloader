package com.shokirjon.cartubedownloader.storage

import android.content.Context
import com.shokirjon.cartubedownloader.core.EngineException
import com.shokirjon.cartubedownloader.core.ErrorCategory
import com.shokirjon.cartubedownloader.core.OutputKind
import java.io.File
import java.util.concurrent.TimeUnit

class MediaValidator(private val context: Context) {
    fun validate(file: File, kind: OutputKind) {
        if (!file.isFile || file.length() < 1024L) throw EngineException(ErrorCategory.CONVERSION, "The output file is empty or incomplete.")
        val ffprobe = File(context.applicationInfo.nativeLibraryDir, "libffprobe.so")
        if (!ffprobe.isFile) throw EngineException(ErrorCategory.CONVERSION, "FFprobe is missing from this APK.")
        val p = try {
            ProcessBuilder(ffprobe.absolutePath, "-v", "error", "-show_entries", "format=format_name", "-show_entries", "stream=codec_type,codec_name", "-of", "default=noprint_wrappers=1", file.absolutePath)
                .redirectErrorStream(true).apply { environment()["LD_LIBRARY_PATH"] = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath }.start()
        } catch (e: Exception) { throw EngineException(ErrorCategory.CONVERSION, "FFprobe could not start.", e.toString(), e) }
        if (!p.waitFor(25, TimeUnit.SECONDS)) { p.destroyForcibly(); throw EngineException(ErrorCategory.CONVERSION, "Media validation timed out.") }
        val output = p.inputStream.bufferedReader().readText().take(3000)
        if (p.exitValue() != 0) throw EngineException(ErrorCategory.CONVERSION, "The media file is not playable.", output)
        if (kind == OutputKind.MP3 && (!output.contains("format_name=mp3") || !output.contains("codec_name=mp3") || !output.contains("codec_type=audio")))
            throw EngineException(ErrorCategory.CONVERSION, "The output is not a real MP3 file.", output)
        if (kind == OutputKind.MP4 && (!output.contains("mp4") || !output.contains("codec_type=video") || !output.contains("codec_name=h264") || !output.contains("codec_type=audio") || !output.contains("codec_name=aac")))
            throw EngineException(ErrorCategory.CODEC, "The output is not an Android-compatible H.264 MP4.", output)
    }
}
