package com.shokirjon.cartubedownloader.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.shokirjon.cartubedownloader.CarTubeApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var playerView: PlayerView
    private var sourceUri = ""
    private var video = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent.getStringExtra("uri").orEmpty()
        video = intent.getBooleanExtra("video", false)
        if (sourceUri.isBlank() || (video && !intent.getBooleanExtra("parkedConfirmed", false))) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(11, 21, 32)) }
        val header = TextView(this).apply {
            text = if (video) "Video — view only while safely parked" else "Music — background playback supported"
            textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; minHeight = 56.dp
        }
        root.addView(header)
        playerView = PlayerView(this).apply { resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; useController = true }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, click: () -> Unit) { controls.addView(Button(this).apply { text = label; minHeight = 52.dp; setOnClickListener { click() } }) }
        action("Previous") { controller?.seekToPreviousMediaItem() }
        action("Repeat") { controller?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF } }
        action("Shuffle") { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
        action("Next") { controller?.seekToNextMediaItem() }
        root.addView(controls)
        setContentView(root)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    controller = future.get().also { mediaController ->
                        playerView.player = mediaController
                        lifecycleScope.launch {
                            val app = application as CarTubeApp
                            val completed = app.downloads.tasks.first().filter { it.state == "COMPLETED" && it.fileUri != null && (it.kind == if (video) "MP4" else "MP3") }
                            val items = completed.mapNotNull { row -> row.fileUri?.let { MediaItem.Builder().setUri(it).setMediaId(row.id).setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(row.title).setArtist(row.channel).build()).build() } }
                            val index = completed.indexOfFirst { it.fileUri == sourceUri }.coerceAtLeast(0)
                            if (items.isEmpty()) mediaController.setMediaItem(MediaItem.fromUri(sourceUri)) else mediaController.setMediaItems(items, index, 0L)
                            val prefs = getSharedPreferences("playback", Context.MODE_PRIVATE)
                            if (!video && prefs.getString("uri", null) == sourceUri) mediaController.seekTo(prefs.getLong("position", 0L))
                            mediaController.prepare()
                            if (!video) mediaController.play()
                        }
                    }
                } catch (_: Exception) { header.text = "Unable to open this media file." }
            }, ContextCompat.getMainExecutor(this))
        }
    }
    override fun onStop() {
        controller?.let { c -> getSharedPreferences("playback", Context.MODE_PRIVATE).edit().putString("uri", sourceUri).putLong("position", c.currentPosition).apply() }
        super.onStop()
    }
    override fun onDestroy() {
        playerView.player = null
        controllerFuture?.let(MediaController::releaseFuture)
        super.onDestroy()
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    companion object {
        fun intent(context: Context, uri: String, video: Boolean, parkedConfirmed: Boolean = false) =
            Intent(context, PlayerActivity::class.java).putExtra("uri", uri).putExtra("video", video).putExtra("parkedConfirmed", parkedConfirmed)
    }
}
