package com.shokirjon.cartubedownloader

import android.content.Intent
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shokirjon.cartubedownloader.db.PlaylistEntry
import com.shokirjon.cartubedownloader.db.TrackedPlaylist
import com.shokirjon.cartubedownloader.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an Android emulator/device; CI currently compiles this APK but has no emulator to execute it. */
@RunWith(AndroidJUnit4::class)
class PlaylistScrollInstrumentedTest {
    @Test fun checkboxAtItem80KeepsFirstVisibleItemAndOffset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext as CarTubeApp
        val id = "PLscroll_test_200"
        runBlocking {
            context.dao.upsertPlaylist(TrackedPlaylist(id, "https://www.youtube.com/playlist?list=$id", "Scroll test", "MANUAL",
                true, false, "MP3", "K192", 1L, 220, 0, 220, 220))
            (0 until 220).forEach { i ->
                val video = "v${i.toString().padStart(10, '0')}"
                context.dao.insertItem(PlaylistEntry(id, video, "Video $i", "https://www.youtube.com/watch?v=$video",
                    null, i, false, false, i.toLong()))
            }
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)) as MainActivity
        try {
            var list: ListView? = null
            repeat(40) {
                instrumentation.runOnMainSync {
                    val mode = MainActivity::class.java.getDeclaredField("mode").apply { isAccessible = true }
                    val playlist = MainActivity::class.java.getDeclaredField("playlistId").apply { isAccessible = true }
                    mode.setInt(activity, 1)
                    playlist.set(activity, id)
                    MainActivity::class.java.getDeclaredMethod("renderWorkspace", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, false)
                    list = MainActivity::class.java.getDeclaredField("playlistList").apply { isAccessible = true }.get(activity) as? ListView
                }
                if (list != null && list!!.count > 0) return@repeat
                Thread.sleep(100)
            }
            assertTrue("Playlist did not display", list != null)
            repeat(9) {
                val current = list!!.count
                if (current < 96) {
                    instrumentation.runOnMainSync { list!!.setSelection((current - 5).coerceAtLeast(0)) }
                    Thread.sleep(200)
                }
            }
            assertTrue("Automatic paging stopped before item 80", list!!.count >= 96)
            instrumentation.runOnMainSync { list!!.setSelectionFromTop(80, 8) }
            instrumentation.waitForIdleSync()
            val snapshot = IntArray(4)
            instrumentation.runOnMainSync {
                val view = list!!
                snapshot[0] = view.firstVisiblePosition
                snapshot[1] = view.getChildAt(0).top
                val row = view.getChildAt(0) as LinearLayout
                val checkbox = ((row.getChildAt(0) as LinearLayout).getChildAt(0) as CheckBox)
                checkbox.performClick()
            }
            Thread.sleep(350)
            instrumentation.runOnMainSync {
                assertEquals(snapshot[0], list!!.firstVisiblePosition)
                assertTrue(kotlin.math.abs(snapshot[1] - list!!.getChildAt(0).top) <= 2)
                val row = list!!.getChildAt(0) as LinearLayout
                assertTrue(((row.getChildAt(0) as LinearLayout).getChildAt(0) as CheckBox).isChecked)
                MainActivity::class.java.getDeclaredField("parkedForPreview").apply { isAccessible = true }
                    .setBoolean(activity, true)
                val frame = (row.getChildAt(0) as LinearLayout).getChildAt(1) as FrameLayout
                frame.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val row = list!!.getChildAt(0) as LinearLayout
                val frame = (row.getChildAt(0) as LinearLayout).getChildAt(1) as FrameLayout
                assertEquals("Preview replaces the thumbnail; no second video pane", 3, row.childCount)
                assertTrue("Embedded player must meet YouTube's minimum size", frame.height >= activity.resources.displayMetrics.density * 200)
                assertEquals(snapshot[0], list!!.firstVisiblePosition)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
