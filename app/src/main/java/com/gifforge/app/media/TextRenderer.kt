package com.gifforge.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.max

const val ALIGN_LEFT = 0
const val ALIGN_CENTER = 1
const val ALIGN_RIGHT = 2

/** All sizes are relative to the frame, so preview and full-size export look identical. */
data class TextOverlay(
    val id: Long,
    val text: String = "Your text",
    val fontPath: String? = null,
    val bold: Boolean = true,
    val sizePct: Float = 8f,          // text height as % of frame height
    val color: Int = Color.WHITE,
    val strokeColor: Int = Color.BLACK,
    val strokeRatio: Float = 0.10f,   // outline thickness as fraction of text size
    val align: Int = ALIGN_CENTER,
    val posX: Float = 0.5f,           // center of text block, 0..1
    val posY: Float = 0.85f,
)

fun drawOverlays(canvas: Canvas, w: Int, h: Int, overlays: List<TextOverlay>) {
    for (o in overlays) {
        if (o.text.isBlank()) continue
        val size = o.sizePct / 100f * h
        if (size < 1f) continue
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = FontStore.typeface(o.fontPath, o.bold)
            strokeJoin = Paint.Join.ROUND
        }
        val align = when (o.align) {
            ALIGN_LEFT -> Layout.Alignment.ALIGN_NORMAL
            ALIGN_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val maxW = (w * 0.94f).toInt().coerceAtLeast(1)
        val probe = StaticLayout.Builder.obtain(o.text, 0, o.text.length, paint, maxW)
            .setAlignment(align).build()
        var widest = 0f
        for (i in 0 until probe.lineCount) widest = max(widest, probe.getLineWidth(i))
        val boxW = (ceil(widest).toInt() + 2).coerceIn(1, maxW)
        val layout = StaticLayout.Builder.obtain(o.text, 0, o.text.length, paint, boxW)
            .setAlignment(align).build()

        val left = (o.posX * w - boxW / 2f).coerceIn(0f, max(0f, (w - boxW).toFloat()))
        val top = (o.posY * h - layout.height / 2f).coerceIn(0f, max(0f, (h - layout.height).toFloat()))

        canvas.save()
        canvas.translate(left, top)
        if (o.strokeRatio > 0f && Color.alpha(o.strokeColor) > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * o.strokeRatio * 2f // half is covered by the fill
            paint.color = o.strokeColor
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = o.color
        layout.draw(canvas)
        canvas.restore()
    }
}

/** Transparent bitmap holding all overlays, or null if nothing visible. */
fun renderOverlayBitmap(w: Int, h: Int, overlays: List<TextOverlay>): Bitmap? {
    if (overlays.none { it.text.isNotBlank() }) return null
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    drawOverlays(Canvas(bmp), w, h, overlays)
    return bmp
}
