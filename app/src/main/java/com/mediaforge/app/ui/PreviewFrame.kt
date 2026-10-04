package com.mediaforge.app.ui

import com.mediaforge.app.media.CropRect

/**
 * Where the FULL source frame sits inside the preview box. Without bars this is a plain fit.
 * With caption bars the cropped video + bars are fitted together, so the preview matches the export.
 */
internal fun previewFrame(
    bw: Float, bh: Float, vw: Int, vh: Int, crop: CropRect, topPct: Float, botPct: Float,
): FitBox {
    if (topPct <= 0f && botPct <= 0f) return fitBox(bw, bh, vw, vh)
    val c = crop.toPx(vw, vh)
    val cw = c[2].toFloat()
    val ch = c[3].toFloat()
    val compH = ch * (1f + (topPct + botPct) / 100f)
    val s = minOf(bw / cw, bh / compH)
    val ox = (bw - cw * s) / 2f
    val oy = (bh - compH * s) / 2f
    val vx = ox
    val vy = oy + ch * topPct / 100f * s
    return FitBox(vx - c[0] * s, vy - c[1] * s, vw * s, vh * s)
}
