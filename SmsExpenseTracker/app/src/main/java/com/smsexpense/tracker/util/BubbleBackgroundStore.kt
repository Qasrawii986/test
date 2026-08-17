package com.smsexpense.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Keeps the bubble's background image as a small private copy rather than a
 * `content://` URI.
 *
 * The overlay is drawn by a service that can start long after the picker
 * closed — and after a reboot — so a borrowed URI permission is exactly the
 * kind of thing that silently stops working. A file in `filesDir` needs no
 * permission and cannot be revoked.
 */
object BubbleBackgroundStore {

    /** Comfortably above the largest bubble on the densest screen. */
    private const val MAX_EDGE_PX = 512
    private const val PREFIX = "bubble_bg_"

    /**
     * Copies [source] into internal storage, downscaled and cropped square.
     * Returns the new file's absolute path, or null if the image could not be
     * read. The file name carries a timestamp so the new image is never
     * mistaken for a cached copy of the old one.
     */
    fun save(context: Context, source: Uri, now: Long = System.currentTimeMillis()): String? {
        val decoded = decodeDownscaled(context, source) ?: return null
        val square = cropToSquare(decoded)
        val target = File(context.filesDir, "$PREFIX$now.png")
        return try {
            target.outputStream().use { out ->
                square.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            clearAllExcept(context, target.name)
            target.absolutePath
        } catch (e: Exception) {
            AppLog.w("Could not store the bubble background", e)
            target.delete()
            null
        } finally {
            if (square !== decoded) square.recycle()
            decoded.recycle()
        }
    }

    /** Removes every stored background. */
    fun clear(context: Context) = clearAllExcept(context, keep = null)

    private fun clearAllExcept(context: Context, keep: String?) {
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith(PREFIX) && it.name != keep }
            ?.forEach { runCatching { it.delete() } }
    }

    /** Two-pass decode: read the bounds first so a 12MP photo is never fully loaded. */
    private fun decodeDownscaled(context: Context, source: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (largestEdge <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }
                    .first { largestEdge / it <= MAX_EDGE_PX }
            }
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    } catch (e: Exception) {
        AppLog.w("Could not read the picked image", e)
        null
    }

    /** The bubble is square, so store a centre crop instead of squashing the photo. */
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        if (edge <= 0 || (bitmap.width == edge && bitmap.height == edge)) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            (bitmap.width - edge) / 2,
            (bitmap.height - edge) / 2,
            edge,
            edge,
        )
    }
}
