package com.gifforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gifforge.app.media.CropRect

/** Where the media is actually drawn inside a letterboxed container. */
data class FitBox(val x: Float, val y: Float, val w: Float, val h: Float)

fun fitBox(w: Float, h: Float, contentW: Int, contentH: Int): FitBox {
    val aspect = contentW.toFloat() / contentH
    val dw: Float
    val dh: Float
    if (w / h > aspect) { dh = h; dw = h * aspect } else { dw = w; dh = w / aspect }
    return FitBox((w - dw) / 2f, (h - dh) / 2f, dw, dh)
}

private const val NONE = 0
private const val MOVE = 1
private const val TL = 2
private const val TR = 3
private const val BL = 4
private const val BR = 5

fun moveRect(r: CropRect, dx: Float, dy: Float): CropRect {
    val w = r.r - r.l
    val h = r.b - r.t
    val l = (r.l + dx).coerceIn(0f, 1f - w)
    val t = (r.t + dy).coerceIn(0f, 1f - h)
    return CropRect(l, t, l + w, t + h)
}

private fun resizeCorner(r: CropRect, corner: Int, dx: Float, dy: Float, aspect: Float?, cw: Int, ch: Int): CropRect {
    val minF = 0.05f
    val sx = if (corner == TL || corner == BL) -1f else 1f
    val sy = if (corner == TL || corner == TR) -1f else 1f
    val ax = if (sx > 0) r.l else r.r
    val ay = if (sy > 0) r.t else r.b
    val mx = (if (sx > 0) r.r else r.l) + dx
    val my = (if (sy > 0) r.b else r.t) + dy
    val maxW = if (sx > 0) 1f - ax else ax
    val maxH = if (sy > 0) 1f - ay else ay
    var w = (sx * (mx - ax)).coerceIn(minOf(minF, maxW), maxW)
    var h = (sy * (my - ay)).coerceIn(minOf(minF, maxH), maxH)
    if (aspect != null) {
        val k = cw / (aspect * ch) // hF = wF * k
        h = w * k
        if (h > maxH) { h = maxH; w = h / k }
        if (w > maxW) { w = maxW; h = w * k }
    }
    val x2 = ax + sx * w
    val y2 = ay + sy * h
    return CropRect(minOf(ax, x2), minOf(ay, y2), maxOf(ax, x2), maxOf(ay, y2))
}

/** Draggable crop box drawn over letterboxed media of size contentW x contentH. */
@Composable
fun CropOverlay(
    rect: CropRect,
    onRect: (CropRect) -> Unit,
    aspect: Float?,
    contentW: Int,
    contentH: Int,
    modifier: Modifier = Modifier,
) {
    val curRect by rememberUpdatedState(rect)
    val curCb by rememberUpdatedState(onRect)
    val curAspect by rememberUpdatedState(aspect)

    Canvas(
        modifier.fillMaxSize().pointerInput(contentW, contentH) {
            var mode = NONE
            var working = CropRect()
            val slop = 44.dp.toPx()
            detectDragGestures(
                onDragStart = { o ->
                    val fr = fitBox(size.width.toFloat(), size.height.toFloat(), contentW, contentH)
                    val r = curRect
                    working = r
                    val corners = listOf(
                        TL to Offset(fr.x + r.l * fr.w, fr.y + r.t * fr.h),
                        TR to Offset(fr.x + r.r * fr.w, fr.y + r.t * fr.h),
                        BL to Offset(fr.x + r.l * fr.w, fr.y + r.b * fr.h),
                        BR to Offset(fr.x + r.r * fr.w, fr.y + r.b * fr.h),
                    )
                    val hit = corners.minByOrNull { (it.second - o).getDistance() }
                    mode = if (hit != null && (hit.second - o).getDistance() <= slop) {
                        hit.first
                    } else {
                        val fx = (o.x - fr.x) / fr.w
                        val fy = (o.y - fr.y) / fr.h
                        if (fx in r.l..r.r && fy in r.t..r.b) MOVE else NONE
                    }
                },
                onDragEnd = { mode = NONE },
                onDragCancel = { mode = NONE },
            ) { change, drag ->
                if (mode == NONE) return@detectDragGestures
                change.consume()
                val fr = fitBox(size.width.toFloat(), size.height.toFloat(), contentW, contentH)
                val dx = drag.x / fr.w
                val dy = drag.y / fr.h
                working = if (mode == MOVE) moveRect(working, dx, dy)
                else resizeCorner(working, mode, dx, dy, curAspect, contentW, contentH)
                curCb(working)
            }
        },
    ) {
        val fr = fitBox(size.width, size.height, contentW, contentH)
        val l = fr.x + rect.l * fr.w
        val t = fr.y + rect.t * fr.h
        val r = fr.x + rect.r * fr.w
        val b = fr.y + rect.b * fr.h
        val dim = Color(0xB3000000)
        drawRect(dim, Offset(fr.x, fr.y), Size(fr.w, t - fr.y))
        drawRect(dim, Offset(fr.x, b), Size(fr.w, fr.y + fr.h - b))
        drawRect(dim, Offset(fr.x, t), Size(l - fr.x, b - t))
        drawRect(dim, Offset(r, t), Size(fr.x + fr.w - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        val thin = Stroke(1.dp.toPx())
        val faint = Color(0x80FFFFFF)
        for (i in 1..2) {
            val vx = l + (r - l) * i / 3f
            val hy = t + (b - t) * i / 3f
            drawLine(faint, Offset(vx, t), Offset(vx, b), thin.width)
            drawLine(faint, Offset(l, hy), Offset(r, hy), thin.width)
        }
        val hs = 7.dp.toPx()
        for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
            drawRect(Color.White, Offset(p.x - hs, p.y - hs), Size(hs * 2, hs * 2))
        }
    }
}
