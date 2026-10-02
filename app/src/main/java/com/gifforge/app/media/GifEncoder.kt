package com.gifforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class GifOptions(
    val startMs: Long,
    val endMs: Long,
    val fps: Int = 15,
    val quality: Int = 90,
    val fast: Boolean = false,
    val loop: Boolean = true,
    val speed: Float = 1f,
    val overlays: List<TextOverlay> = emptyList(),
    val crop: CropRect? = null,
)

/**
 * Decodes frames in-process at the video's original size, burns in text overlays,
 * and streams everything straight into gifski.
 */
suspend fun encodeGif(
    context: Context,
    uri: Uri,
    out: File,
    opts: GifOptions,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.Default) {
    val retriever = MediaMetadataRetriever()
    var handle = 0L
    var overlayBmp: Bitmap? = null
    var ow = 0
    var oh = 0
    try {
        retriever.setDataSource(context, uri)

        val spanMs = (opts.endMs - opts.startMs).coerceAtLeast(1)
        val total = (spanMs / 1000.0 * opts.fps).toInt().coerceAtLeast(1)

        handle = GifskiNative.nativeStart(out.absolutePath, 0, 0, opts.quality, opts.fast, opts.loop)
        check(handle != 0L) { "Could not start encoder" }

        for (i in 0 until total) {
            ensureActive()
            val tMs = opts.startMs + (i * 1000.0 / opts.fps).toLong()
            val raw = retriever.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw IllegalStateException("Could not decode frame at ${tMs}ms")

            var bmp = raw
            opts.crop?.let { c ->
                val px = c.toPx(raw.width, raw.height)
                if (px[2] != raw.width || px[3] != raw.height) {
                    bmp = Bitmap.createBitmap(raw, px[0], px[1], px[2], px[3])
                    if (bmp !== raw) raw.recycle()
                }
            }
            if (opts.overlays.isNotEmpty()) {
                if (!bmp.isMutable) {
                    val m = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    bmp.recycle()
                    bmp = m
                }
                if (overlayBmp == null || ow != bmp.width || oh != bmp.height) {
                    overlayBmp?.recycle()
                    overlayBmp = renderOverlayBitmap(bmp.width, bmp.height, opts.overlays)
                    ow = bmp.width
                    oh = bmp.height
                }
                overlayBmp?.let { Canvas(bmp).drawBitmap(it, 0f, 0f, null) }
            }

            val (w, h) = bmp.width to bmp.height
            val rgba = bitmapToRgba(bmp)
            bmp.recycle()
            val pts = i / opts.fps.toDouble() / opts.speed
            check(GifskiNative.nativeAddFrame(handle, i, rgba, w, h, pts)) { "Encoder rejected frame $i" }
            onProgress((i + 1f) / total * 0.95f)
        }

        val ok = GifskiNative.nativeFinish(handle)
        handle = 0L
        check(ok) { "GIF encoding failed" }
        onProgress(1f)
        Result.success(out)
    } catch (e: Throwable) {
        if (handle != 0L) GifskiNative.nativeCancel(handle)
        out.delete()
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    } finally {
        overlayBmp?.recycle()
        retriever.release()
    }
}

private fun bitmapToRgba(b: Bitmap): ByteArray {
    val n = b.width * b.height
    val px = IntArray(n)
    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    val out = ByteArray(n * 4)
    var o = 0
    for (p in px) {
        out[o++] = (p shr 16).toByte()
        out[o++] = (p shr 8).toByte()
        out[o++] = p.toByte()
        out[o++] = (p ushr 24).toByte()
    }
    return out
}
