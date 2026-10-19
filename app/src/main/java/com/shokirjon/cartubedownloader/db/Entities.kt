package com.shokirjon.cartubedownloader.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "download_tasks", indices = [Index("canonicalUrl"), Index("queueOrder")])
data class DownloadTask(
    @PrimaryKey val id: String,
    val originalUrl: String,
    val canonicalUrl: String,
    val videoId: String?,
    val playlistId: String?,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val duration: Long?,
    val kind: String,
    val quality: String,
    val state: String,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speed: Long?,
    val etaSeconds: Long?,
    val stage: String?,
    val fileUri: String?,
    val errorCategory: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val queueOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "tracked_playlists")
data class TrackedPlaylist(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val interval: String,
    val wifiOnly: Boolean,
    val autoAdd: Boolean,
    val outputKind: String,
    val quality: String,
    val lastChecked: Long?,
    val knownCount: Int,
    val newCount: Int,
    val totalCount: Int? = null,
    @ColumnInfo(defaultValue = "0") val remoteOffset: Int = 0
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "videoId"], indices = [Index("videoId")])
data class PlaylistEntry(
    val playlistId: String,
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val position: Int,
    val selected: Boolean,
    val queued: Boolean,
    val firstSeen: Long,
    val duration: Long? = null
)
