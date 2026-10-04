package com.mediaforge.app.media

import kotlin.math.roundToInt

/** Crop area as fractions (0..1) of the source frame, so it works at any resolution. */
data class CropRect(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val isFull: Boolean get() = l <= 0.001f && t <= 0.001f && r >= 0.999f && b >= 0.999f

    /** [x, y, width, height] in pixels for a source of the given size. */
    fun toPx(w: Int, h: Int): IntArray {
        val x = (l * w).roundToInt().coerceIn(0, (w - 1).coerceAtLeast(0))
        val y = (t * h).roundToInt().coerceIn(0, (h - 1).coerceAtLeast(0))
        val cw = ((r - l) * w).roundToInt().coerceIn(1, (w - x).coerceAtLeast(1))
        val ch = ((b - t) * h).roundToInt().coerceIn(1, (h - y).coerceAtLeast(1))
        return intArrayOf(x, y, cw, ch)
    }
}
