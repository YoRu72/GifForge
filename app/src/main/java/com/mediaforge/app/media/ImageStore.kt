package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.caverock.androidsvg.SVG
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** An imported picture: where it was stored and its width / height. */
class ImportedImage(val path: String, val width: Int, val height: Int) {
    val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

/**
 * A20.a: imports PNG, JPG, WebP, GIF (first frame), BMP, HEIC/AVIF (Android 9+) and SVG as picture layers.
 * The picture is decoded once, limited to [MAX_SIDE] px, and stored in app storage (PNG when it has
 * transparency, JPEG otherwise), so the layer survives the source file moving. SVG is rasterised at import.
 */
object ImageStore {
    private const val MAX_SIDE = 2048
    private const val SVG_SIDE = 1024

    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, v: Bitmap) = v.byteCount
    }

    fun bitmap(path: String): Bitmap? = cache.get(path) ?: runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.also { cache.put(path, it) }

    private fun isSvg(ctx: Context, uri: Uri): Boolean {
        val type = ctx.contentResolver.getType(uri).orEmpty()
        if (type.contains("svg", true) || uri.toString().substringBefore('?').endsWith(".svg", true)) return true
        val head = ctx.contentResolver.openInputStream(uri)?.use { s -> ByteArray(1024).let { b -> String(b, 0, s.read(b).coerceAtLeast(0), Charsets.UTF_8) } } ?: return false
        return head.contains("<svg", true)
    }

    private fun readSvg(ctx: Context, uri: Uri): Bitmap? {
        val svg = ctx.contentResolver.openInputStream(uri)?.use { SVG.getFromInputStream(it) } ?: return null
        var w = svg.documentWidth
        var h = svg.documentHeight
        if (w <= 0f || h <= 0f) {
            val vb = svg.documentViewBox
            if (vb != null && vb.width() > 0f && vb.height() > 0f) { w = vb.width(); h = vb.height() } else { w = 512f; h = 512f }
        }
        val k = SVG_SIDE / max(w, h)
        val bw = (w * k).roundToInt().coerceAtLeast(1)
        val bh = (h * k).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        svg.setDocumentWidth(bw.toFloat())
        svg.setDocumentHeight(bh.toFloat())
        svg.renderToCanvas(Canvas(bmp))
        return bmp
    }

    private fun readRaster(ctx: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // a software bitmap can be drawn and saved from any thread
                val big = max(info.size.width, info.size.height)
                if (big > MAX_SIDE) { val k = MAX_SIDE.toFloat() / big; dec.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt()) }
            }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = ctx.contentResolver.openInputStream(uri)?.use {
            when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        } ?: 0f
        if (rot == 0f) return raw
        val m = android.graphics.Matrix().apply { postRotate(rot) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /** Decodes [uri] and stores it; null when the file is not a picture this device can read. Call off the main thread. */
    fun import(ctx: Context, uri: Uri): ImportedImage? = try {
        val bmp = (if (isSvg(ctx, uri)) readSvg(ctx, uri) else readRaster(ctx, uri)) ?: return null
        val dir = File(ctx.filesDir, "images").apply { mkdirs() }
        val png = bmp.hasAlpha()
        val f = File(dir, "img_" + System.nanoTime() + (if (png) ".png" else ".jpg"))
        f.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        cache.put(f.absolutePath, bmp)
        ImportedImage(f.absolutePath, bmp.width, bmp.height)
    } catch (e: Throwable) { null }
}
