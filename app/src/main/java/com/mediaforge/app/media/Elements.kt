package com.mediaforge.app.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ShapeKind(val label: String) {
    RECT("Rectangle"), ROUND("Rounded"), ELLIPSE("Ellipse"), LINE("Line"), ARROW("Arrow")
}

/** A vector shape layer. All geometry is relative to the (cropped) frame, so preview == export at any size. */
data class ShapeElement(
    val id: Long,
    val kind: ShapeKind,
    val cx: Float = 0.5f,            // centre, 0..1
    val cy: Float = 0.5f,
    val w: Float = 0.4f,             // width as fraction of frame width
    val h: Float = 0.2f,             // height as fraction of frame height (arrow head size for ARROW)
    val rotation: Float = 0f,        // degrees
    val filled: Boolean = true,
    val fill: Int = 0xFFF44336.toInt(),   // opaque RGB; opacity is fillAlpha
    val fillAlpha: Float = 1f,
    val stroke: Int = Color.WHITE,
    val strokePct: Float = 1f,       // outline thickness as % of frame height
    val opacity: Float = 1f,         // whole-layer opacity
    val blend: LayerBlend = LayerBlend.NORMAL,
    val timed: Boolean = false,      // show only between fromMs and toMs (source-video time)
    val fromMs: Long = 0L,
    val toMs: Long = 0L,
) {
    val isLine: Boolean get() = kind == ShapeKind.LINE || kind == ShapeKind.ARROW

    companion object {
        fun default(kind: ShapeKind, id: Long): ShapeElement = when (kind) {
            ShapeKind.LINE -> ShapeElement(id, kind, w = 0.5f, h = 0.05f, stroke = 0xFFF44336.toInt(), strokePct = 1.5f)
            ShapeKind.ARROW -> ShapeElement(id, kind, w = 0.5f, h = 0.1f, stroke = 0xFFF44336.toInt(), strokePct = 1.5f)
            ShapeKind.ELLIPSE -> ShapeElement(id, kind, w = 0.35f, h = 0.35f)
            else -> ShapeElement(id, kind)
        }
    }
}

fun normDeg(d: Float): Float = ((d + 180f) % 360f + 360f) % 360f - 180f

fun drawElements(canvas: Canvas, w: Int, h: Int, els: List<ShapeElement>) {
    for (e in els) {
        val cx = e.cx * w
        val cy = e.cy * h
        val hw = e.w * w / 2f
        val hh = e.h * h / 2f
        val rect = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
        val fillColor = (e.fill and 0x00FFFFFF) or ((e.fillAlpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24)
        val sw = e.strokePct / 100f * h
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fillColor }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = e.stroke
            strokeWidth = if (e.isLine) max(sw, 2f) else sw
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val drawOutline = !e.isLine && sw > 0.5f && Color.alpha(e.stroke) > 0
        canvas.save()
        canvas.rotate(e.rotation, cx, cy)
        when (e.kind) {
            ShapeKind.RECT -> {
                if (e.filled) canvas.drawRect(rect, fill)
                if (drawOutline) canvas.drawRect(rect, line)
            }
            ShapeKind.ROUND -> {
                val r = min(hw, hh) * 0.4f
                if (e.filled) canvas.drawRoundRect(rect, r, r, fill)
                if (drawOutline) canvas.drawRoundRect(rect, r, r, line)
            }
            ShapeKind.ELLIPSE -> {
                if (e.filled) canvas.drawOval(rect, fill)
                if (drawOutline) canvas.drawOval(rect, line)
            }
            ShapeKind.LINE -> canvas.drawLine(cx - hw, cy, cx + hw, cy, line)
            ShapeKind.ARROW -> {
                val headLen = max(line.strokeWidth * 3f, min(hw * 0.8f, hh * 2f))
                val half = headLen * 0.6f
                val tip = cx + hw
                canvas.drawLine(cx - hw, cy, tip - headLen * 0.8f, cy, line)
                val head = Path().apply {
                    moveTo(tip, cy)
                    lineTo(tip - headLen, cy - half)
                    lineTo(tip - headLen, cy + half)
                    close()
                }
                canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = e.stroke })
            }
        }
        canvas.restore()
    }
}
