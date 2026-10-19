package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.mediaforge.app.subs.Gray
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Frames for motion tracking (MOT2/MOT7): decoded once, scaled down to a working width and turned into luma. On
 * Android 9+ frames come in order from getFramesAtIndex (the decoder runs forward, no seek per frame); older phones
 * and odd files fall back to one exact seek per frame.
 */
object FrameGrabber {
    fun luma(b: Bitmap): Gray {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            out[i] = ((((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8).toByte()
        }
        return Gray(w, h, out)
    }

    private fun scaled(b: Bitmap, workW: Int): Bitmap {
        if (b.width <= workW) return if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
        val h = (b.height * workW.toFloat() / b.width).roundToInt().coerceAtLeast(2)
        return Bitmap.createScaledBitmap(b, workW, h, true).also { if (it !== b) b.recycle() }
    }

    /** One frame at [tMs] (exact frame, scaled to [workW] wide), or null. */
    fun frameAt(ctx: Context, uri: Uri, tMs: Long, workW: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.getFrameAtTime(tMs.coerceAtLeast(0L) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { scaled(it, workW) }
        } catch (e: Exception) { null } finally { r.release() }
    }

    /**
     * Calls [onFrame] for every frame from [startMs] to [endMs] (frame times in ms from [startMs]). [onFrame] returns
     * false to stop (cancel or lost track). Returns the number of frames delivered.
     */
    fun frames(ctx: Context, uri: Uri, startMs: Long, endMs: Long, fps: Float, workW: Int, onFrame: (tMs: Long, f: Gray, bmp: Bitmap) -> Boolean): Int {
        val rate = if (fps in 5f..240f) fps else 25f
        val first = (startMs * rate / 1000f).roundToInt()
        val last = (endMs * rate / 1000f).roundToInt().coerceAtLeast(first)
        val r = MediaMetadataRetriever()
        var n = 0
        try {
            r.setDataSource(ctx, uri)
            val count = if (Build.VERSION.SDK_INT >= 28) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0 else 0
            var i = first
            while (i <= last) {
                val chunk = minOf(4, last - i + 1)
                val bmps: List<Bitmap> = try {
                    if (Build.VERSION.SDK_INT >= 28 && count > 0 && i + chunk <= count) {
                        val p = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
                        r.getFramesAtIndex(i, chunk, p)
                    } else emptyList()
                } catch (e: Exception) { emptyList() }
                val list = bmps.ifEmpty {
                    (0 until chunk).mapNotNull { k -> r.getFrameAtTime(((i + k) * 1000.0 / rate * 1000).roundToLong(), MediaMetadataRetriever.OPTION_CLOSEST) }
                }
                if (list.isEmpty()) break
                for ((k, b) in list.withIndex()) {
                    val sb = scaled(b, workW)
                    val t = ((i + k) * 1000.0 / rate).roundToLong() - startMs
                    n++
                    if (!onFrame(t.coerceAtLeast(0L), luma(sb), sb)) return n
                }
                i += chunk
            }
        } catch (e: Exception) {
            // unreadable file: n frames were delivered
        } finally {
            r.release()
        }
        return n
    }
}
