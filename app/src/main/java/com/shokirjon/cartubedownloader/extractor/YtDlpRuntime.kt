package com.shokirjon.cartubedownloader.extractor

import android.content.Context
import android.os.Build
import com.shokirjon.cartubedownloader.core.EngineException
import com.shokirjon.cartubedownloader.core.ErrorCategory
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.zip.ZipInputStream

class YtDlpRuntime(private val context: Context) {
    private val revision = "2026.08.19-ejs0.8.0"
    @Synchronized fun initialize() {
        if (!com.shokirjon.cartubedownloader.core.RuntimeChecks.supportsAny(Build.SUPPORTED_ABIS)) {
            throw EngineException(ErrorCategory.UNSUPPORTED_ABI, "This APK requires ARMv7 or ARM64.")
        }
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        listOf("libpython.so", "libpython.zip.so", "libqjs.so", "libffmpeg.so", "libffmpeg.zip.so", "libffprobe.so").forEach { name ->
            if (!File(nativeDir, name).isFile) throw EngineException(ErrorCategory.ENGINE, "Native runtime missing: $name")
        }
        val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)
        if (prefs.getString("revision", null) != revision) {
            File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}").delete()
        }
        try {
            YoutubeDL.init(context)
            FFmpeg.init(context)
            installEjs()
            prefs.edit().putString("revision", revision).apply()
        } catch (e: EngineException) { throw e }
        catch (e: Exception) { throw EngineException(ErrorCategory.ENGINE, "The bundled media engine could not start.", e.toString(), e) }
    }
    private fun installEjs() {
        val site = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages/python/usr/lib/python3.12/site-packages")
        val marker = File(site, "yt_dlp_ejs-0.8.0.dist-info/METADATA")
        if (marker.isFile) return
        if (!site.isDirectory) throw EngineException(ErrorCategory.EJS_MISSING, "Embedded Python packages are unavailable.")
        ZipInputStream(context.assets.open("engine/yt_dlp_ejs-0.8.0-py3-none-any.whl")).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!name.startsWith("yt_dlp_ejs/") && !name.startsWith("yt_dlp_ejs-0.8.0.dist-info/")) { zip.closeEntry(); continue }
                val target = File(site, name)
                if (!target.canonicalPath.startsWith(site.canonicalPath + File.separator)) throw EngineException(ErrorCategory.EJS_MISSING, "Invalid bundled EJS path.")
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
        if (!marker.isFile) throw EngineException(ErrorCategory.EJS_MISSING, "The bundled EJS component did not install.")
    }
    fun version(): String = revision
}
