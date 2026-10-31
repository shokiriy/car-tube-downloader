package com.shokirjon.cartubedownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shokirjon.cartubedownloader.core.OutputKind
import com.shokirjon.cartubedownloader.settings.SettingsStore
import com.shokirjon.cartubedownloader.storage.MediaValidator
import com.shokirjon.cartubedownloader.storage.StoragePublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyStorageInstrumentedTest {
    @Test
    fun android81PublishesPlayableMp4IntoPublicMovies() {
        assumeTrue(Build.VERSION.SDK_INT == 27)
        runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.WRITE_EXTERNAL_STORAGE}"
        )).bufferedReader().use { it.readText() }
        assertEquals(PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        val source = context.cacheDir.resolve("legacy-publish-fixture.mp4")
        var published: android.net.Uri? = null
        try {
            instrumentation.context.assets.open("media/synthetic-h264-aac.mp4").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            MediaValidator().validate(source, OutputKind.MP4)
            val uri = StoragePublisher(context, SettingsStore(context)).publish(source, OutputKind.MP4,
                "CarTube Android 8.1 storage test ${System.currentTimeMillis()}")
            published = uri
            assertEquals("content", uri.scheme)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            assertTrue("Published bytes must match the validated source", source.readBytes().contentEquals(bytes))
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).contains("/Movies/CarTube/"))
            }
        } finally {
            published?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
        }
        }
    }
}
