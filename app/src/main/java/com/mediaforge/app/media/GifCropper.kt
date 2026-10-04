package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GifMeta(val width: Int, val height: Int, val frames: Int, val delaysCs: IntArray = IntArray(0))

/** Start time of [frame] in ms (frame == frames gives the total duration). */
fun GifMeta.timeAtMs(frame: Int): Long {
    var t = 0L
    for (i in 0 until frame.coerceIn(0, delaysCs.size)) t += delaysCs[i] * 10L
    return t
}

fun copyUriToCache(ctx: Context, uri: Uri, name: String): File? {
    return try {
        val f = File(ctx.cacheDir, name)
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f
    } catch (e: Exception) {
        null
    }
}

suspend fun readGifMeta(f: File): GifMeta? = withContext(Dispatchers.IO) {
    GifskiNative.nativeGifInfo(f.absolutePath)?.let {
        GifMeta(it[0], it[1], it[2], GifskiNative.nativeGifDelays(f.absolutePath) ?: IntArray(0))
    }
}

/** Still of one GIF frame, shrunk to [maxSide] for the trim previews. */
suspend fun readGifFrame(f: File, meta: GifMeta, index: Int, maxSide: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
    val bytes = GifskiNative.nativeGifFrame(f.absolutePath, index) ?: return@withContext null
    if (bytes.size != meta.width * meta.height * 4) return@withContext null
    val bmp = Bitmap.createBitmap(meta.width, meta.height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
    val big = maxOf(meta.width, meta.height)
    if (big <= maxSide) bmp else {
        val k = maxSide.toFloat() / big
        val s = Bitmap.createScaledBitmap(bmp, (meta.width * k).toInt().coerceAtLeast(1), (meta.height * k).toInt().coerceAtLeast(1), true)
        if (s !== bmp) bmp.recycle()
        s
    }
}

/** Crops an existing GIF at its original quality/size (only the crop area is kept). */
suspend fun cropGif(
    src: File,
    out: File,
    rect: CropRect,
    meta: GifMeta,
    quality: Int,
    startFrame: Int,
    endFrameExclusive: Int,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.Default) {
    val px = rect.toPx(meta.width, meta.height)
    coroutineScope {
        val poller = launch {
            while (isActive) {
                onProgress(GifskiNative.nativeCropProgress() / 100f)
                delay(100)
            }
        }
        try {
            val err = GifskiNative.nativeCropGif(
                src.absolutePath, out.absolutePath, px[0], px[1], px[2], px[3], quality, false, startFrame, endFrameExclusive,
            )
            if (err == null) Result.success(out) else { out.delete(); Result.failure(Exception(err)) }
        } finally {
            poller.cancel()
        }
    }
}
