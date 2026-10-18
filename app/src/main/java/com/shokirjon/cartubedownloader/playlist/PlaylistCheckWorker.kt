package com.shokirjon.cartubedownloader.playlist

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shokirjon.cartubedownloader.CarTubeApp
import com.shokirjon.cartubedownloader.R
import com.shokirjon.cartubedownloader.download.DownloadService
import java.util.concurrent.TimeUnit

object PlaylistScheduler {
    private const val NAME = "cartube_playlist_checks"
    suspend fun schedule(context: Context) {
        val app = context.applicationContext as CarTubeApp
        val manager = WorkManager.getInstance(context)
        if (app.dao.scheduledPlaylists().isEmpty()) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<PlaylistCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class PlaylistCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CarTubeApp
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        var discovered = 0
        for (playlist in app.dao.scheduledPlaylists()) {
            if (playlist.wifiOnly && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) continue
            val interval = if (playlist.interval == "WEEKLY") TimeUnit.DAYS.toMillis(7) else TimeUnit.DAYS.toMillis(1)
            if (playlist.lastChecked != null && System.currentTimeMillis() - playlist.lastChecked < interval) continue
            try { discovered += app.playlists.sync(playlist.id) } catch (_: Exception) { return Result.retry() }
            if (playlist.autoAdd) runCatching { DownloadService.start(applicationContext) }
        }
        if (discovered > 0 && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("cartube_playlists", "Playlist updates", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(29, Notification.Builder(applicationContext, "cartube_playlists").setSmallIcon(R.drawable.ic_cartube)
                .setContentTitle("$discovered new playlist items").setContentText("Open CarTube to review them.").build())
        }
        return Result.success()
    }
}
