package com.shokirjon.cartubedownloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.shokirjon.cartubedownloader.CarTubeApp
import com.shokirjon.cartubedownloader.R
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.DownloadTask
import com.shokirjon.cartubedownloader.diagnostics.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = application as CarTubeApp
    private var queueJob: Job? = null
    @Volatile private var activeId: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "CarTube downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_CANCEL, ACTION_PAUSE -> if (id != null) {
                if (activeId == id) app.extractor.cancel(id)
                scope.launch { app.downloads.state(id, if (intent.action == ACTION_PAUSE) TaskState.PAUSED else TaskState.CANCELLED) ; app.storage.cleanup(id) }
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, notification("Preparing queue", null, null))
                if (queueJob?.isActive != true) queueJob = scope.launch { processQueue() }
            }
        }
        return START_NOT_STICKY
    }
    private suspend fun processQueue() {
        try {
            val tasks = app.downloads.pending()
            for (task in tasks) {
                if (app.downloads.get(task.id)?.state !in listOf("PENDING", "QUEUED")) continue
                if (app.settings.current().wifiOnly && !onWifi()) {
                    app.downloads.state(task.id, TaskState.PAUSED, "Wi-Fi required. Resume after connecting.")
                    continue
                }
                activeId = task.id
                var attempt = 0
                while (true) {
                    try { processOne(task); break }
                    catch (e: EngineException) {
                        val current = app.downloads.get(task.id)?.state
                        if (current == "PAUSED" || current == "CANCELLED" || e.category == ErrorCategory.CANCELLED) break
                        if (e.category in setOf(ErrorCategory.NETWORK, ErrorCategory.RATE_LIMITED) && attempt < 2) {
                            attempt++
                            app.downloads.retry(task.id)
                            app.downloads.state(task.id, TaskState.QUEUED, "Temporary network error; retry $attempt/2")
                            delay(5_000L * (1 shl attempt))
                            continue
                        }
                        DiagnosticLog.record(this@DownloadService, "${e.category} ${task.id}", e.diagnostic ?: e.message)
                        app.downloads.state(task.id, TaskState.FAILED, "Failed", e.category, e.message)
                        break
                    } catch (e: Exception) {
                        val current = app.downloads.get(task.id)?.state
                        if (current != "PAUSED" && current != "CANCELLED") {
                            DiagnosticLog.record(this@DownloadService, "UNKNOWN ${task.id}", e.toString())
                            app.downloads.state(task.id, TaskState.FAILED, "Failed", ErrorCategory.UNKNOWN, e.message ?: "Unexpected download failure.")
                        }
                        break
                    } finally { app.storage.cleanup(task.id) }
                }
                activeId = null
            }
        } finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    private suspend fun processOne(task: DownloadTask) {
        app.downloads.state(task.id, TaskState.ANALYZING, "Refreshing source metadata")
        val info = app.extractor.extractInfo(task.canonicalUrl)
        if (app.downloads.get(task.id)?.state in listOf("PAUSED", "CANCELLED")) return
        app.storage.ensureSpace(info.size ?: task.totalBytes)
        app.downloads.state(task.id, TaskState.DOWNLOADING, "Downloading stream")
        val kind = OutputKind.valueOf(task.kind)
        val quality = if (kind == OutputKind.MP3) AudioQuality.valueOf(task.quality).kbps else VideoQuality.valueOf(task.quality).height
        var lastUpdate = 0L
        val file = app.extractor.download(task.id, task.canonicalUrl, kind, quality, app.storage.workingDir(task.id)) { p ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 750L || p.stage != null) {
                lastUpdate = now
                scope.launch {
                    app.downloads.progress(task.id, p)
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID,
                        notification(task.title, p.stage ?: "Downloading stream", p.percent?.toInt(), task.id))
                }
            }
        }
        if (app.downloads.get(task.id)?.state in listOf("PAUSED", "CANCELLED")) return
        app.downloads.state(task.id, TaskState.PROCESSING, "Validating ${kind.name} file")
        app.validator.validate(file, kind)
        val uri = app.storage.publish(file, kind, task.title)
        app.downloads.complete(task.id, uri.toString(), file.length())
    }
    private fun onWifi(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    private fun notification(title: String, stage: String?, percent: Int?, id: String? = activeId): Notification {
        val cancel = Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)
        val pending = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_cartube).setContentTitle(title.take(70))
            .setContentText(stage ?: "CarTube Downloader").setOngoing(true).setOnlyAlertOnce(true)
        if (id != null) builder.addAction(Notification.Action.Builder(null, "Cancel", pending).build())
        if (percent != null) builder.setProgress(100, percent.coerceIn(0, 100), false) else builder.setProgress(0, 0, true)
        return builder.build()
    }
    override fun onDestroy() { activeId?.let(app.extractor::cancel); scope.cancel(); super.onDestroy() }
    companion object {
        private const val CHANNEL = "cartube_downloads"
        private const val NOTIFICATION_ID = 23
        private const val EXTRA_ID = "task_id"
        private const val ACTION_START = "com.shokirjon.cartube.START"
        private const val ACTION_CANCEL = "com.shokirjon.cartube.CANCEL"
        private const val ACTION_PAUSE = "com.shokirjon.cartube.PAUSE"
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(ACTION_START)) }
        fun cancel(context: Context, id: String) { context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id)) }
        fun pause(context: Context, id: String) { context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_PAUSE).putExtra(EXTRA_ID, id)) }
    }
}
