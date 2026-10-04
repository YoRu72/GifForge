package com.mediaforge.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.StaticLayout
import kotlin.math.roundToInt

/** A meme-style strip added above or below the video. Height is a % of the (cropped) video height. */
data class CaptionBar(
    val text: String = "Caption",
    val heightPct: Float = 20f,
    val bg: Int = Color.WHITE,
    val color: Int = Color.BLACK,
    val fontPath: String? = null,
    val fontVars: String? = null,
    val bold: Boolean = true,
    val align: Int = ALIGN_CENTER,
)

fun barHeightPx(videoH: Int, bar: CaptionBar?): Int =
    if (bar == null) 0 else (videoH * bar.heightPct / 100f).roundToInt().coerceAtLeast(1)

private fun drawBar(c: Canvas, bar: CaptionBar, y: Float, w: Int, h: Int) {
    c.drawRect(0f, y, w.toFloat(), y + h, Paint().apply { color = bar.bg })
    if (bar.text.isBlank()) return
    val padX = w * 0.04f
    val maxW = (w - 2 * padX).toInt().coerceAtLeast(1)
    val maxH = (h * 0.84f).coerceAtLeast(1f)
    val align = physicalAlignment(bar.text, bar.align)
    val paint = newTextPaint(bar.text, FontStore.typeface(bar.fontPath, bar.bold, bar.fontVars), h * 0.7f).apply {
        color = bar.color
    }
    // shrink the text until it fits inside the bar
    var size = h * 0.7f
    var layout: StaticLayout
    while (true) {
        paint.textSize = size
        layout = buildLayout(bar.text, paint, maxW, align)
        if (layout.height <= maxH || size <= 6f) break
        size *= 0.92f
    }
    c.save()
    c.translate(padX, y + (h - layout.height) / 2f)
    layout.draw(c)
    c.restore()
}

/** Transparent bitmap of size w x (videoH + bars) with only the bars painted - used by the live preview. */
fun renderBarsBitmap(w: Int, videoH: Int, top: CaptionBar?, bottom: CaptionBar?): Bitmap? {
    if (top == null && bottom == null) return null
    val tH = barHeightPx(videoH, top)
    val bH = barHeightPx(videoH, bottom)
    val bmp = Bitmap.createBitmap(w, videoH + tH + bH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (top != null) drawBar(c, top, 0f, w, tH)
    if (bottom != null) drawBar(c, bottom, (tH + videoH).toFloat(), w, bH)
    return bmp
}

/** Final export frame: bars + video. Returns [src] itself when there are no bars (caller recycles src otherwise). */
fun composeWithBars(src: Bitmap, top: CaptionBar?, bottom: CaptionBar?): Bitmap {
    if (top == null && bottom == null) return src
    val tH = barHeightPx(src.height, top)
    val bH = barHeightPx(src.height, bottom)
    val out = Bitmap.createBitmap(src.width, src.height + tH + bH, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(src, 0f, tH.toFloat(), null)
    if (top != null) drawBar(c, top, 0f, src.width, tH)
    if (bottom != null) drawBar(c, bottom, (tH + src.height).toFloat(), src.width, bH)
    return out
}
