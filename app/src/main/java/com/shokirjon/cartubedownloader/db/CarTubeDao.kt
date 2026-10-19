package com.shokirjon.cartubedownloader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarTubeDao {
    @Query("SELECT * FROM download_tasks ORDER BY queueOrder, createdAt") fun tasks(): Flow<List<DownloadTask>>
    @Query("SELECT * FROM download_tasks ORDER BY queueOrder, createdAt") suspend fun allTasks(): List<DownloadTask>
    @Query("SELECT * FROM download_tasks WHERE state IN ('PENDING','QUEUED') ORDER BY queueOrder, createdAt") suspend fun pending(): List<DownloadTask>
    @Query("SELECT * FROM download_tasks WHERE id=:id LIMIT 1") suspend fun task(id: String): DownloadTask?
    @Query("SELECT * FROM download_tasks WHERE canonicalUrl=:url AND kind=:kind AND quality=:quality ORDER BY createdAt DESC LIMIT 1") suspend fun latest(url: String, kind: String, quality: String): DownloadTask?
    @Query("SELECT COALESCE(MAX(queueOrder),-1)+1 FROM download_tasks") suspend fun nextOrder(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(task: DownloadTask): Long
    @Update suspend fun update(task: DownloadTask)
    @Query("DELETE FROM download_tasks WHERE id=:id") suspend fun remove(id: String)
    @Query("DELETE FROM download_tasks WHERE state='COMPLETED'") suspend fun clearCompleted()
    @Query("DELETE FROM download_tasks WHERE state='FAILED'") suspend fun clearFailed()
    @Query("UPDATE download_tasks SET state='INTERRUPTED', errorCategory='INTERRUPTED', errorMessage='App process stopped. Resume to retry this item safely.', updatedAt=:now WHERE state IN ('ANALYZING','DOWNLOADING','PROCESSING')") suspend fun interruptActive(now: Long)

    @Query("SELECT * FROM tracked_playlists ORDER BY title") fun playlists(): Flow<List<TrackedPlaylist>>
    @Query("SELECT * FROM tracked_playlists WHERE id=:id LIMIT 1") suspend fun playlist(id: String): TrackedPlaylist?
    @Query("SELECT * FROM tracked_playlists WHERE interval!='MANUAL'") suspend fun scheduledPlaylists(): List<TrackedPlaylist>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlaylist(playlist: TrackedPlaylist)
    @Query("DELETE FROM tracked_playlists WHERE id=:id") suspend fun removePlaylist(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItem(item: PlaylistEntry): Long
    @Query("SELECT * FROM playlist_items WHERE playlistId=:id ORDER BY position LIMIT :limit OFFSET :offset") suspend fun playlistPage(id: String, limit: Int, offset: Int): List<PlaylistEntry>
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId=:id") suspend fun playlistCount(id: String): Int
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId=:id AND selected=1") suspend fun selectedCount(id: String): Int
    @Query("SELECT * FROM playlist_items WHERE playlistId=:id AND selected=1 AND queued=0 ORDER BY position") suspend fun selectedUnqueued(id: String): List<PlaylistEntry>
    @Query("SELECT * FROM playlist_items WHERE playlistId=:id ORDER BY firstSeen DESC, position DESC LIMIT :limit") suspend fun newestItems(id: String, limit: Int): List<PlaylistEntry>
    @Query("UPDATE playlist_items SET selected=:selected WHERE playlistId=:id") suspend fun selectAll(id: String, selected: Boolean)
    @Query("UPDATE playlist_items SET selected=:selected WHERE playlistId=:id AND videoId=:videoId") suspend fun selectItem(id: String, videoId: String, selected: Boolean)
    @Query("UPDATE playlist_items SET queued=1 WHERE playlistId=:id AND videoId=:videoId") suspend fun markQueued(id: String, videoId: String)
}
