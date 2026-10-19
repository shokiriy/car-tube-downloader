package com.shokirjon.cartubedownloader

import android.app.Application
import com.shokirjon.cartubedownloader.db.CarTubeDatabase
import com.shokirjon.cartubedownloader.download.DownloadRepository
import com.shokirjon.cartubedownloader.extractor.YtDlpExtractor
import com.shokirjon.cartubedownloader.settings.SettingsStore
import com.shokirjon.cartubedownloader.storage.MediaValidator
import com.shokirjon.cartubedownloader.storage.StoragePublisher
import com.shokirjon.cartubedownloader.playlist.PlaylistRepository
import com.shokirjon.cartubedownloader.playlist.PlaylistScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CarTubeApp : Application() {
    val db by lazy { CarTubeDatabase.get(this) }
    val dao by lazy { db.dao() }
    val settings by lazy { SettingsStore(this) }
    val extractor by lazy { YtDlpExtractor(this) }
    val downloads by lazy { DownloadRepository(dao) }
    val storage by lazy { StoragePublisher(this, settings) }
    val validator by lazy { MediaValidator() }
    val playlists by lazy { PlaylistRepository(dao, extractor, downloads, settings) }
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            downloads.recover()
            PlaylistScheduler.schedule(this@CarTubeApp)
        }
    }
}
