package com.shokirjon.cartubedownloader.core

object FormatSelector {
    // Prefer an already muxed H.264/AAC file on low-memory devices; merge only if unavailable.
    fun mp4(height: Int) = "best[ext=mp4][vcodec^=avc1][acodec^=mp4a][height<=$height]/bestvideo[ext=mp4][vcodec^=avc1][height<=$height]+bestaudio[ext=m4a]"
    fun compatible(formats: List<MediaFormat>, height: Int): List<MediaFormat> = formats.filter {
        it.ext.equals("mp4", true) && it.height != null && it.height <= height &&
            (it.vcodec?.startsWith("avc1", true) == true) && (it.acodec == null || it.acodec.startsWith("mp4a", true))
    }.sortedByDescending { it.height }
}
