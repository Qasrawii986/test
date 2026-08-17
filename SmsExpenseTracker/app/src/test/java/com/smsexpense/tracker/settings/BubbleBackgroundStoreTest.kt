package com.smsexpense.tracker.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.util.BubbleBackgroundStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The background is copied into private storage rather than referenced by URI,
 * because the overlay is drawn by a service that may start after a reboot.
 */
@RunWith(RobolectricTestRunner::class)
class BubbleBackgroundStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val sources = mutableListOf<File>()

    @Before
    fun setUp() {
        BubbleBackgroundStore.clear(context)
    }

    @After
    fun tearDown() {
        BubbleBackgroundStore.clear(context)
        sources.forEach { it.delete() }
    }

    /** Writes a real PNG so the store's two-pass decode has something to read. */
    private fun sourceImage(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("source", ".png").also { sources += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun sizeOf(path: String): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun `the image is copied into app storage`() {
        val path = BubbleBackgroundStore.save(context, sourceImage(200, 200), now = 1L)

        assertNotNull(path)
        val stored = File(path!!)
        assertTrue(stored.exists())
        // Inside filesDir, so no permission is ever needed to read it back.
        assertTrue(stored.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun `a large photo is downscaled instead of stored whole`() {
        val path = BubbleBackgroundStore.save(context, sourceImage(4000, 3000), now = 1L)!!

        val (width, height) = sizeOf(path)
        assertTrue("stored $width x $height", maxOf(width, height) <= 512)
    }

    @Test
    fun `a rectangular photo is centre-cropped square for a round bubble`() {
        val path = BubbleBackgroundStore.save(context, sourceImage(800, 400), now = 1L)!!

        val (width, height) = sizeOf(path)
        assertEquals(width, height)
    }

    @Test
    fun `choosing a new image replaces the old file and changes the path`() {
        val first = BubbleBackgroundStore.save(context, sourceImage(200, 200), now = 1L)!!
        val second = BubbleBackgroundStore.save(context, sourceImage(300, 300), now = 2L)!!

        // A new name means the cached decode of the old path is never reused.
        assertFalse(first == second)
        assertFalse("the previous background should be deleted", File(first).exists())
        assertTrue(File(second).exists())
    }

    @Test
    fun `clearing removes the stored file`() {
        val path = BubbleBackgroundStore.save(context, sourceImage(200, 200), now = 1L)!!

        BubbleBackgroundStore.clear(context)

        assertFalse(File(path).exists())
    }

    @Test
    fun `an unreadable source returns null rather than throwing`() {
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.png"))
        assertNull(BubbleBackgroundStore.save(context, missing, now = 1L))
    }
}
