package com.shokirjon.cartubedownloader.ui

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Uses the same Media3 stack as saved-file playback, with only one local player at a time. */
internal class LocalVideoPreview(private val context: Context) {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null
    private var currentUri: Uri? = null

    fun attach(parent: ViewGroup, uri: Uri) {
        val playback = player ?: ExoPlayer.Builder(context).build().also { player = it }
        val controls = view ?: PlayerView(context).apply { useController = true; player = playback }.also { view = it }
        (controls.parent as? ViewGroup)?.removeView(controls)
        parent.removeAllViews()
        parent.addView(controls, ViewGroup.LayoutParams(-1, -1))
        if (currentUri != uri) {
            currentUri = uri
            playback.setMediaItem(MediaItem.fromUri(uri))
            playback.prepare()
            playback.playWhenReady = true
        }
    }

    fun close() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it); it.player = null }
        player?.release()
        player = null
        view = null
        currentUri = null
    }
}
