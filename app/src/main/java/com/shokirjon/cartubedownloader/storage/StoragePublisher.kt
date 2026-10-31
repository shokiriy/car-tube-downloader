package com.shokirjon.cartubedownloader.storage

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.settings.SettingsStore
import java.io.File

class StoragePublisher(private val context: Context, private val settings: SettingsStore) {
    private val root = File(context.cacheDir, "cartube")
    fun workingDir(id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return File(root, id).also { it.mkdirs() }
    }
    fun cleanup(id: String) {
        if (id.matches(Regex("[0-9a-f-]{36}"))) File(root, id).deleteRecursively()
    }
    suspend fun ensureSpace(estimatedBytes: Long?) {
        val stat = StatFs(context.cacheDir.absolutePath)
        val available = stat.availableBytes
        val reserve = settings.current().reserveBytes
        if (!RuntimeChecks.hasWorkingSpace(available, estimatedBytes, reserve)) throw EngineException(ErrorCategory.STORAGE_FULL,
            "Not enough temporary space. Keep at least 1 GB free after reserving space for download and conversion.")
    }
    suspend fun publish(file: File, kind: OutputKind, title: String): Uri {
        val prefs = settings.current()
        val mime = if (kind == OutputKind.MP3) "audio/mpeg" else "video/mp4"
        val base = FileNames.sanitize(title)
        val extension = if (kind == OutputKind.MP3) "mp3" else "mp4"
        val folder = prefs.folderUri
        if (folder != null) {
            try {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder)) ?: throw IllegalStateException("Folder unavailable")
                if (!tree.canWrite()) throw SecurityException("Folder is read-only")
                val name = uniqueName(base, extension) { tree.findFile(it) != null }
                val doc = tree.createFile(mime, name) ?: throw IllegalStateException("Could not create destination")
                try {
                    context.contentResolver.openOutputStream(doc.uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output, 64 * 1024) } }
                        ?: throw IllegalStateException("Could not open destination")
                    return doc.uri
                } catch (e: Exception) { doc.delete(); throw e }
            } catch (e: SecurityException) { throw EngineException(ErrorCategory.STORAGE_PERMISSION, "External folder access was denied. Select the folder again.", e.toString(), e) }
            catch (e: Exception) { throw EngineException(ErrorCategory.FILE_WRITE, "Could not save to the selected folder.", e.toString(), e) }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return publishLegacy(file, kind, base, extension, mime, prefs.reserveBytes)
        }
        val resolver = context.contentResolver
        val directory = if (kind == OutputKind.MP3) "${Environment.DIRECTORY_MUSIC}/CarTube/" else "${Environment.DIRECTORY_MOVIES}/CarTube/"
        val collection = if (kind == OutputKind.MP3) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                         else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = uniqueName(base, extension) { candidate ->
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(candidate, directory), null)?.use { it.moveToFirst() } ?: false
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw EngineException(ErrorCategory.FILE_WRITE, "MediaStore could not create the file.")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output, 64 * 1024) } }
                ?: throw IllegalStateException("Could not write MediaStore file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw EngineException(ErrorCategory.FILE_WRITE, "Could not save the completed media.", e.toString(), e)
        }
    }

    /** Android 8.1/9 have neither RELATIVE_PATH nor IS_PENDING. Publish a validated
     * file into public Music/Movies with the pre-Q storage permission, then index it.
     * The returned content URI keeps Library playback and deletion working on both OS paths.
     */
    private fun publishLegacy(file: File, kind: OutputKind, base: String, extension: String, mime: String, reserveBytes: Long): Uri {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            throw EngineException(ErrorCategory.STORAGE_PERMISSION,
                "Allow file storage access for Car Tube, or choose a USB/SD folder in Settings.")
        val publicFolder = if (kind == OutputKind.MP3) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val directory = File(Environment.getExternalStoragePublicDirectory(publicFolder), "CarTube")
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IllegalStateException(
                "Cannot create ${directory.absolutePath}; externalState=${Environment.getExternalStorageState()}, " +
                    "parentExists=${directory.parentFile?.exists()}, parentWritable=${directory.parentFile?.canWrite()}")
            if (StatFs(directory.absolutePath).availableBytes < file.length() + reserveBytes)
                throw EngineException(ErrorCategory.STORAGE_FULL, "Not enough space in the destination. Free up at least 1 GB.")
            val name = uniqueName(base, extension) { File(directory, it).exists() }
            val target = File(directory, name)
            if (!target.createNewFile()) throw IllegalStateException("Destination name is already in use")
            try {
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
                if (target.length() != file.length()) throw IllegalStateException("Destination copy is incomplete")
                val resolver = context.contentResolver
                val collection = if (kind == OutputKind.MP3) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val existingId = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA}=?", arrayOf(target.absolutePath), null)?.use {
                    if (it.moveToFirst()) it.getLong(0) else null
                }
                if (existingId != null) return ContentUris.withAppendedId(collection, existingId)
                return resolver.insert(collection, ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, target.absolutePath)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.SIZE, target.length())
                }) ?: throw IllegalStateException("Could not index the completed file")
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        } catch (e: EngineException) { throw e }
        catch (e: SecurityException) {
            throw EngineException(ErrorCategory.STORAGE_PERMISSION, "Allow file storage access or choose a USB/SD folder.", e.toString(), e)
        } catch (e: Exception) {
            throw EngineException(ErrorCategory.FILE_WRITE, "Could not save to Music/CarTube or Movies/CarTube.", e.toString(), e)
        }
    }
    private fun uniqueName(base: String, extension: String, exists: (String) -> Boolean): String {
        for (n in 0..999) {
            val name = "$base${if (n == 0) "" else " ($n)"}.$extension"
            if (!exists(name)) return name
        }
        throw EngineException(ErrorCategory.FILE_WRITE, "Too many files with this name.")
    }
}
