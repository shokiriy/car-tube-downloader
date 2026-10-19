package com.shokirjon.cartubedownloader.storage

import android.media.MediaExtractor
import android.media.MediaFormat
import com.shokirjon.cartubedownloader.core.EngineException
import com.shokirjon.cartubedownloader.core.ErrorCategory
import com.shokirjon.cartubedownloader.core.OutputKind
import java.io.File
import java.nio.ByteBuffer

/** Verify the finished file with Android's own demuxer before making it visible in MediaStore. */
class MediaValidator {
    fun validate(file: File, kind: OutputKind) {
        if (!file.isFile || file.length() < 1024L)
            throw EngineException(ErrorCategory.CONVERSION, "The output file is empty or incomplete.")
        val extension = if (kind == OutputKind.MP3) "mp3" else "mp4"
        if (!file.extension.equals(extension, true))
            throw EngineException(ErrorCategory.CONVERSION, "The output filename is not .$extension.")
        if (kind == OutputKind.MP4) {
            val header = ByteArray(8)
            try {
                file.inputStream().use {
                    if (it.read(header) != 8 || String(header, 4, 4, Charsets.US_ASCII) != "ftyp")
                        throw EngineException(ErrorCategory.CONVERSION, "The output is not an MP4 container.")
                }
            } catch (e: EngineException) { throw e }
            catch (e: Exception) { throw EngineException(ErrorCategory.FILE_WRITE, "Could not read the output file.", e.toString(), e) }
        }
        val extractor = MediaExtractor()
        var stage = "opening file"
        try {
            extractor.setDataSource(file.absolutePath)
            val tracks = mutableListOf<String>()
            for (index in 0 until extractor.trackCount) {
                stage = "reading track $index"
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                tracks.add(mime)
                if (mime == "audio/mpeg" || mime == "audio/mp4a-latm" || mime == "video/avc") {
                    stage = "reading sample $index ($mime)"
                    extractor.selectTrack(index)
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    if (extractor.readSampleData(ByteBuffer.allocate(4096), 0) <= 0)
                        throw EngineException(ErrorCategory.CONVERSION, "The output contains an empty media track.", "track=$mime, bytes=${file.length()}")
                    extractor.unselectTrack(index)
                }
            }
            val valid = if (kind == OutputKind.MP3) "audio/mpeg" in tracks
                else "video/avc" in tracks && "audio/mp4a-latm" in tracks
            if (!valid) throw EngineException(ErrorCategory.CODEC,
                if (kind == OutputKind.MP3) "The output is not a playable MP3."
                else "The MP4 requires H.264 video and AAC audio.", "tracks=$tracks, bytes=${file.length()}")
        } catch (e: EngineException) { throw e }
        catch (e: Exception) {
            val structure = if (kind == OutputKind.MP4) runCatching { Mp4Atoms.describe(file) }.getOrDefault("atom inspection failed") else "mp3"
            throw EngineException(ErrorCategory.CONVERSION,
                "Android could not open the finished media file.",
                "stage=$stage; ${e.javaClass.simpleName}: ${e.message}; bytes=${file.length()}; $structure", e)
        }
        finally { extractor.release() }
    }
}
