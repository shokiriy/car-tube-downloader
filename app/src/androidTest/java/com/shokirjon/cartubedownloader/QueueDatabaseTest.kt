package com.shokirjon.cartubedownloader

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shokirjon.cartubedownloader.core.*
import com.shokirjon.cartubedownloader.db.CarTubeDatabase
import com.shokirjon.cartubedownloader.download.DownloadRepository
import com.shokirjon.cartubedownloader.download.EnqueueResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueDatabaseTest {
    private lateinit var db: CarTubeDatabase
    private lateinit var repo: DownloadRepository
    private fun info(id: String) = MediaInfo(id, "Public example", "Test creator", 9, null,
        "https://www.youtube.com/watch?v=$id", emptyList(), 1024L)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CarTubeDatabase::class.java).build()
        repo = DownloadRepository(db.dao())
    }
    @After fun close() { db.close() }
    @Test fun duplicateQueueAndRetry() = runBlocking {
        val a = info("abcdefghijk")
        val first = repo.enqueue(a, a.url, OutputKind.MP3, "K192") as EnqueueResult.Added
        assertTrue(repo.enqueue(a, a.url, OutputKind.MP3, "K192") is EnqueueResult.Duplicate)
        repo.state(first.task.id, TaskState.FAILED, error = "Network error")
        repo.retry(first.task.id)
        assertEquals("PENDING", repo.get(first.task.id)?.state)
        assertEquals(1, repo.get(first.task.id)?.retryCount)
    }
    @Test fun interruptionAndOrdering() = runBlocking {
        val first = repo.enqueue(info("abcdefghijk"), "https://youtu.be/abcdefghijk", OutputKind.MP4, "P480") as EnqueueResult.Added
        val second = repo.enqueue(info("123456789ab"), "https://youtu.be/123456789ab", OutputKind.MP3, "K192") as EnqueueResult.Added
        repo.move(second.task.id, -1)
        assertEquals(second.task.id, db.dao().allTasks().first().id)
        repo.state(first.task.id, TaskState.DOWNLOADING)
        repo.recover()
        assertEquals("INTERRUPTED", repo.get(first.task.id)?.state)
    }
    @Test fun cancellationDoesNotBecomeProgress() = runBlocking {
        val row = repo.enqueue(info("abcdefghijk"), "https://youtu.be/abcdefghijk", OutputKind.MP3, "K192") as EnqueueResult.Added
        repo.state(row.task.id, TaskState.CANCELLED)
        repo.progress(row.task.id, TransferProgress(82f, 500, 600, 100, 1, null))
        assertEquals(0f, repo.get(row.task.id)!!.progress)
    }
}
