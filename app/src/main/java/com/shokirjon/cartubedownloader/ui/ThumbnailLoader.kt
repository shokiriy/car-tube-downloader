package com.shokirjon.cartubedownloader.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.ImageView
import android.util.LruCache
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal object ThumbnailCandidates {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    fun urls(source: String?, videoId: String?): List<String> = buildList {
        if (source != null && source.startsWith("https://") && runCatching { URL(source).host.isNotBlank() }.getOrDefault(false)) add(source)
        if (videoId != null && idPattern.matches(videoId)) {
            add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
        }
    }.distinct()
}

/** Small, bounded image loader. A recycled row cancels its request and can never receive another row's bitmap. */
internal class ThumbnailLoader(context: Context) {
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val disk = context.cacheDir.resolve("thumbnails").apply { mkdirs() }
    private val slots = Semaphore(2)
    private val requests = mutableMapOf<ImageView, Job>()

    fun bind(owner: LifecycleOwner, view: ImageView, source: String?, videoId: String?) {
        val urls = ThumbnailCandidates.urls(source, videoId)
        val key = urls.joinToString("|")
        if (view.tag == key && (requests[view]?.isActive == true || cache.get(key) != null)) return
        unbind(view)
        view.setImageDrawable(null)
        view.setBackgroundColor(Color.rgb(31, 53, 63))
        if (urls.isEmpty()) return
        cache.get(key)?.let { view.setImageBitmap(it); return }
        view.tag = key
        requests[view] = owner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                slots.withPermit { urls.firstNotNullOfOrNull { url -> load(url) } }
            }
            if (view.tag == key && bitmap != null) {
                cache.put(key, bitmap)
                view.setImageBitmap(bitmap)
            }
            requests.remove(view)
        }
    }

    fun unbind(view: ImageView) {
        requests.remove(view)?.cancel()
        view.tag = null
        view.setImageDrawable(null)
    }

    private fun load(url: String): Bitmap? = runCatching {
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = disk.resolve(name)
        if (file.isFile) decode(file.readBytes())?.let { return@runCatching it }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/jpeg,image/webp,image/png")
            connection.connect()
            if (connection.responseCode !in 200..299 || connection.contentType?.startsWith("image/") != true ||
                connection.contentLengthLong > 1_500_000) return@runCatching null
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (bytes.size() + n > 1_500_000) return@runCatching null
                    bytes.write(buffer, 0, n)
                }
            }
            val raw = bytes.toByteArray()
            val bitmap = decode(raw) ?: return@runCatching null
            if (disk.listFiles()?.sumOf { it.length() } ?: 0L > 24L * 1024 * 1024)
                disk.listFiles()?.sortedBy { it.lastModified() }?.take(40)?.forEach { it.delete() }
            file.writeBytes(raw)
            bitmap
        } finally { connection.disconnect() }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        var sample = 1
        while (bounds.outWidth / sample > 240 || bounds.outHeight / sample > 150) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
