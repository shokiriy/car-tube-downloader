package com.shokirjon.cartubedownloader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail

/** Exactly one on-demand official IFrame player; moving its view between inline and dialog preserves playback. */
internal class YouTubePreview(private val context: Context, private val onUnavailable: (String?, Int?) -> Unit) {
    private var view: WebView? = null
    // YouTube requires the installed app ID as the WebView Referer, not youtube.com.
    private val appOrigin = "https://${context.packageName.lowercase()}"
    var videoId: String? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(parent: ViewGroup, id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        val player = view ?: WebView(context).also { web ->
            web.setBackgroundColor(Color.BLACK)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.mediaPlaybackRequiresUserGesture = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.message().startsWith("CAR_TUBE_EMBED_ERROR:")) {
                        val code = message.message().substringAfter(':').toIntOrNull()
                        val failedVideo = videoId
                        web.post { onUnavailable(failedVideo, code) }
                    }
                    return super.onConsoleMessage(message)
                }
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty()
                    return request.isForMainFrame && host !in setOf("www.youtube.com", "youtube.com", "www.youtube-nocookie.com")
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    this@YouTubePreview.view = null
                    val failedVideo = videoId
                    videoId = null
                    onUnavailable(failedVideo, null)
                    return true
                }
            }
            view = web
        }
        (player.parent as? ViewGroup)?.removeView(player)
        parent.removeAllViews()
        parent.addView(player, ViewGroup.LayoutParams(-1, -1))
        if (videoId != id) {
            videoId = id
            val baseUrl = "$appOrigin/"
            player.loadDataWithBaseURL(baseUrl, html(id), "text/html", "UTF-8", baseUrl)
        } else player.onResume()
    }

    fun pause() {
        view?.evaluateJavascript("if(window.player && player.pauseVideo) player.pauseVideo();", null)
        view?.onPause()
    }

    fun close() {
        val old = view ?: return
        (old.parent as? ViewGroup)?.removeView(old)
        old.stopLoading()
        old.loadUrl("about:blank")
        old.destroy()
        view = null
        videoId = null
    }

    private fun html(id: String) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"></head>
      <body style="background:#000;margin:0"><div id="player"></div>
      <script src="https://www.youtube.com/iframe_api"></script><script>
      var player;
      function onYouTubeIframeAPIReady() {
        player = new YT.Player('player', {width:'100%',height:'100%',videoId:'$id',
          playerVars:{playsinline:1,controls:1,origin:'$appOrigin'},
          events:{onReady:function(e){e.target.playVideo()},onError:function(e){console.log('CAR_TUBE_EMBED_ERROR:'+e.data)}}});
      }
      </script><style>html,body,#player{width:100%;height:100%;overflow:hidden}</style></body></html>"""
}
