package com.gifforge.app.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GifMeta(val width: Int, val height: Int, val frames: Int)

fun copyUriToCache(ctx: Context, uri: Uri, name: String): File? = try {
    val f = File(ctx.cacheDir, name)
    ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
        ?: return null
    f
} catch (e: Exception) {
    null
}

suspend fun readGifMeta(f: File): GifMeta? = withContext(Dispatchers.IO) {
    GifskiNative.nativeGifInfo(f.absolutePath)?.let { GifMeta(it[0], it[1], it[2]) }
}

/** Crops an existing GIF at its original quality/size (only the crop area is kept). */
suspend fun cropGif(
    src: File,
    out: File,
    rect: CropRect,
    meta: GifMeta,
    quality: Int,
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
                src.absolutePath, out.absolutePath, px[0], px[1], px[2], px[3], quality, false,
            )
            if (err == null) Result.success(out) else { out.delete(); Result.failure(Exception(err)) }
        } finally {
            poller.cancel()
        }
    }
}
