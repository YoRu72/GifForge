package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class PlayMode(val label: String) { NORMAL("Normal"), REVERSE("Reverse"), PINGPONG("Ping-pong") }

fun PlayMode.labelRes(): Int = when (this) { PlayMode.NORMAL -> com.mediaforge.app.R.string.pm_normal; PlayMode.REVERSE -> com.mediaforge.app.R.string.pm_reverse; PlayMode.PINGPONG -> com.mediaforge.app.R.string.pm_pingpong }

/** Source frame index for every output frame. */
internal fun frameOrder(n: Int, mode: PlayMode): List<Int> = when {
    mode == PlayMode.REVERSE -> (n - 1 downTo 0).toList()
    mode == PlayMode.PINGPONG && n >= 3 -> (0 until n).toList() + (n - 2 downTo 1).toList()
    else -> (0 until n).toList()
}

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
    val elements: List<ShapeElement> = emptyList(),
    val topBar: CaptionBar? = null,
    val bottomBar: CaptionBar? = null,
    val maxWidth: Int = 0,            // 0 = keep size; otherwise shrink wider frames to this width
    val mode: PlayMode = PlayMode.NORMAL,
)

/**
 * Turns a raw decoded frame into the final frame: crop -> shapes/text (with opacity + blend over the
 * video) -> caption bars. Used by the exporter and by the single-frame "blended frame" preview.
 */
class FrameComposer(private val opts: GifOptions) {
    private var layers: List<RenderedLayer> = emptyList()
    private var lw = 0
    private var lh = 0
    private val hasLayers = opts.elements.isNotEmpty() || opts.overlays.any { it.text.isNotBlank() }

    /** Takes ownership of [raw] (may recycle it). The caller owns the returned bitmap. */
    fun compose(raw: Bitmap, tMs: Long = 0L): Bitmap {
        var bmp = raw
        opts.crop?.let { c ->
            val px = c.toPx(raw.width, raw.height)
            if (px[2] != raw.width || px[3] != raw.height) {
                bmp = Bitmap.createBitmap(raw, px[0], px[1], px[2], px[3])
                if (bmp !== raw) raw.recycle()
            }
        }
        if (hasLayers) {
            if (!bmp.isMutable) {
                val m = bmp.copy(Bitmap.Config.ARGB_8888, true)
                bmp.recycle()
                bmp = m
            }
            if (lw != bmp.width || lh != bmp.height) {
                release()
                layers = renderLayers(bmp.width, bmp.height, opts.overlays, opts.elements)
                lw = bmp.width
                lh = bmp.height
            }
            drawLayers(Canvas(bmp), layers, tMs)
        }
        if (opts.topBar != null || opts.bottomBar != null) {
            val composed = composeWithBars(bmp, opts.topBar, opts.bottomBar)
            if (composed !== bmp) { bmp.recycle(); bmp = composed }
        }
        return bmp
    }

    fun release() {
        layers.forEach { it.bmp.recycle() }
        layers = emptyList()
    }
}

/** One fully composed frame at [atMs] (what the GIF will contain), shrunk to [maxSide] for display. */
suspend fun renderStillFrame(context: Context, uri: Uri, opts: GifOptions, atMs: Long, maxSide: Int = 1280): Bitmap? =
    withContext(Dispatchers.Default) {
        val r = MediaMetadataRetriever()
        val composer = FrameComposer(opts)
        try {
            r.setDataSource(context, uri)
            val raw = r.getFrameAtTime(atMs.coerceAtLeast(0L) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return@withContext null
            val out = composer.compose(raw, atMs)
            val big = max(out.width, out.height)
            if (big > maxSide) {
                val k = maxSide.toFloat() / big
                val s = Bitmap.createScaledBitmap(out, (out.width * k).toInt().coerceAtLeast(1), (out.height * k).toInt().coerceAtLeast(1), true)
                if (s !== out) out.recycle()
                s
            } else out
        } catch (e: Exception) {
            null
        } finally {
            composer.release()
            r.release()
        }
    }

/**
 * Decodes frames in-process at the video's original size, composes them, and streams everything
 * straight into gifski.
 */
suspend fun encodeGif(
    context: Context,
    uri: Uri,
    out: File,
    opts: GifOptions,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.Default) {
    val retriever = MediaMetadataRetriever()
    val composer = FrameComposer(opts)
    var handle = 0L
    try {
        retriever.setDataSource(context, uri)

        val spanMs = (opts.endMs - opts.startMs).coerceAtLeast(1)
        val total = (spanMs / 1000.0 * opts.fps).toInt().coerceAtLeast(1)

        val seq = frameOrder(total, opts.mode)
        for ((k, i) in seq.withIndex()) {
            ensureActive()
            val tMs = opts.startMs + (i * 1000.0 / opts.fps).toLong()
            val raw = retriever.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw IllegalStateException("Could not decode frame at ${tMs}ms")

            val bmp = composer.compose(raw, tMs)
            if (handle == 0L) {
                // start once the final frame size is known; only ever shrink, never upscale
                val mw = if (opts.maxWidth > 0 && bmp.width > opts.maxWidth) opts.maxWidth else 0
                handle = GifskiNative.nativeStart(out.absolutePath, mw, 0, opts.quality, opts.fast, opts.loop)
                check(handle != 0L) { "Could not start encoder" }
            }
            val (w, h) = bmp.width to bmp.height
            val rgba = bitmapToRgba(bmp)
            bmp.recycle()
            val pts = k / opts.fps.toDouble() / opts.speed
            check(GifskiNative.nativeAddFrame(handle, k, rgba, w, h, pts)) { "Encoder rejected frame $k" }
            onProgress((k + 1f) / seq.size * 0.95f)
        }

        check(handle != 0L) { "No frames were encoded" }
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
        composer.release()
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
