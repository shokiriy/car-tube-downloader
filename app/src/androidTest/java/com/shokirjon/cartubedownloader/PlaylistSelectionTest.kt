package com.shokirjon.cartubedownloader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.CarTubeDatabase
import com.shokirjon.cartubedownloader.db.PlaylistEntry
import com.shokirjon.cartubedownloader.db.TrackedPlaylist
import com.shokirjon.cartubedownloader.download.DownloadRepository
import com.shokirjon.cartubedownloader.extractor.MediaExtractor
import com.shokirjon.cartubedownloader.playlist.PlaylistRepository
import com.shokirjon.cartubedownloader.settings.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSelectionTest {
    private val extractor = object : MediaExtractor {
        override suspend fun extractInfo(url: String): MediaInfo = error("Not used in selection")
        override suspend fun extractPlaylist(url: String, offset: Int, limit: Int): PlaylistPage = error("Not used in selection")
        override suspend fun download(taskId: String, url: String, kind: OutputKind, quality: Int,
            workingDirectory: File, onProgress: (TransferProgress) -> Unit): File = error("Not used in selection")
        override fun cancel(taskId: String) = Unit
        override suspend fun checkEngineHealth(): EngineHealth = error("Not used in selection")
    }

    @Test fun onlyNewExcludesOldAndAlreadyQueuedItems() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, CarTubeDatabase::class.java).build()
        try {
            val dao = database.dao()
            val id = "PL1234567890"
            dao.upsertPlaylist(TrackedPlaylist(id, "https://www.youtube.com/playlist?list=$id", "Test", "MANUAL",
                true, false, "MP3", "K192", 300L, 3, 2))
            dao.insertItem(PlaylistEntry(id, "old12345678", "Old", "https://youtu.be/old12345678", null, 0, true, false, 100L))
            dao.insertItem(PlaylistEntry(id, "new12345678", "New", "https://youtu.be/new12345678", null, 1, false, false, 200L))
            dao.insertItem(PlaylistEntry(id, "new87654321", "Queued", "https://youtu.be/new87654321", null, 2, true, true, 201L))
            val repo = PlaylistRepository(dao, extractor, DownloadRepository(dao), SettingsStore(context))
            assertEquals(1, repo.selectOnlyNew(id))
            val rows = dao.playlistPage(id, 10, 0)
            assertFalse(rows[0].selected)
            assertTrue(rows[1].selected)
            assertFalse(rows[2].selected)
            assertEquals(1, repo.selectedCount(id))
        } finally { database.close() }
    }

    @Test fun initialPageAndScrollFetchMoreWithoutLosingSelection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, CarTubeDatabase::class.java).build()
        try {
            val calls = mutableListOf<Int>()
            val source = object : MediaExtractor by extractor {
                override suspend fun extractPlaylist(url: String, offset: Int, limit: Int): PlaylistPage {
                    calls += offset
                    val items = (offset until minOf(offset + limit, 120)).map { i ->
                        val video = "v${i.toString().padStart(10, '0')}"
                        PlaylistItem(video, "Video $i", "https://www.youtube.com/watch?v=$video", null, 60L)
                    }
                    return PlaylistPage("PLabcdefghij", "120 videos", 120, items)
                }
            }
            val dao = database.dao()
            val repo = PlaylistRepository(dao, source, DownloadRepository(dao), SettingsStore(context))
            val playlist = repo.add("https://www.youtube.com/playlist?list=PLabcdefghij")
            repo.sync(playlist.id, initialPageOnly = true)
            assertEquals(listOf(0), calls)
            assertEquals(50, dao.playlistCount(playlist.id))
            val first = repo.page(playlist.id, 0, 24)
            repo.select(playlist.id, first[10].videoId, true)
            val second = repo.page(playlist.id, 48, 24)
            assertEquals(24, second.size)
            assertEquals(listOf(0, 50), calls)
            repo.select(playlist.id, second[15].videoId, true)
            assertEquals(2, repo.selectedCount(playlist.id))
            assertEquals(120, dao.playlist(playlist.id)?.totalCount)
            val last = repo.page(playlist.id, 96, 24)
            assertEquals(24, last.size)
            assertEquals(listOf(0, 50, 100), calls)
            assertEquals(120, dao.playlistCount(playlist.id))
            assertTrue(repo.page(playlist.id, 120, 24).isEmpty())
        } finally { database.close() }
    }

    @Test fun queuedVideoKeepsPlaylistIdentityAndClearsSelection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, CarTubeDatabase::class.java).build()
        try {
            val dao = database.dao()
            val id = "PLabcdefghij"
            val video = "M7lc1UVf-VE"
            dao.upsertPlaylist(TrackedPlaylist(id, "https://www.youtube.com/playlist?list=$id", "Playlist", "MANUAL",
                true, false, "MP4", "P480", 1L, 1, 0))
            dao.insertItem(PlaylistEntry(id, video, "Example", "https://www.youtube.com/watch?v=$video", null,
                0, true, false, 1L))
            val source = object : MediaExtractor by extractor {
                override suspend fun extractInfo(url: String) = MediaInfo(video, "Example", "Channel", 60, null, url,
                    emptyList(), null)
            }
            val repo = PlaylistRepository(dao, source, DownloadRepository(dao), SettingsStore(context))
            val result = repo.queueSelected(id, OutputKind.MP4, "P480")
            assertEquals(1 to 0, result)
            assertEquals(id, dao.allTasks().single().playlistId)
            assertEquals(0, dao.selectedCount(id))
            assertTrue(dao.playlistItem(id, video)!!.queued)
        } finally { database.close() }
    }
}
