package com.mediaforge.app.media

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import kotlin.math.roundToInt

/** How a layer mixes with the video under it. Entries with minSdk 29 need Android 10+. */
enum class LayerBlend(val label: String, val minSdk: Int = 1) {
    NORMAL("Normal"), MULTIPLY("Multiply"), SCREEN("Screen"), OVERLAY("Overlay"),
    DARKEN("Darken"), LIGHTEN("Lighten"), ADD("Add"),
    DIFFERENCE("Difference", 29), EXCLUSION("Exclusion", 29),
    COLOR_DODGE("Color dodge", 29), COLOR_BURN("Color burn", 29),
    HARD_LIGHT("Hard light", 29), SOFT_LIGHT("Soft light", 29),
    HUE("Hue", 29), SATURATION("Saturation", 29), COLOR("Color", 29), LUMINOSITY("Luminosity", 29);

    val available: Boolean get() = Build.VERSION.SDK_INT >= minSdk
}

fun Paint.applyBlend(b: LayerBlend) {
    if (b == LayerBlend.NORMAL) return
    if (Build.VERSION.SDK_INT >= 29) {
        blendMode = when (b) {
            LayerBlend.MULTIPLY -> BlendMode.MULTIPLY
            LayerBlend.SCREEN -> BlendMode.SCREEN
            LayerBlend.OVERLAY -> BlendMode.OVERLAY
            LayerBlend.DARKEN -> BlendMode.DARKEN
            LayerBlend.LIGHTEN -> BlendMode.LIGHTEN
            LayerBlend.ADD -> BlendMode.PLUS
            LayerBlend.DIFFERENCE -> BlendMode.DIFFERENCE
            LayerBlend.EXCLUSION -> BlendMode.EXCLUSION
            LayerBlend.COLOR_DODGE -> BlendMode.COLOR_DODGE
            LayerBlend.COLOR_BURN -> BlendMode.COLOR_BURN
            LayerBlend.HARD_LIGHT -> BlendMode.HARD_LIGHT
            LayerBlend.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
            LayerBlend.HUE -> BlendMode.HUE
            LayerBlend.SATURATION -> BlendMode.SATURATION
            LayerBlend.COLOR -> BlendMode.COLOR
            LayerBlend.LUMINOSITY -> BlendMode.LUMINOSITY
            else -> BlendMode.SRC_OVER
        }
    } else {
        val m = when (b) {
            LayerBlend.MULTIPLY -> PorterDuff.Mode.MULTIPLY
            LayerBlend.SCREEN -> PorterDuff.Mode.SCREEN
            LayerBlend.OVERLAY -> PorterDuff.Mode.OVERLAY
            LayerBlend.DARKEN -> PorterDuff.Mode.DARKEN
            LayerBlend.LIGHTEN -> PorterDuff.Mode.LIGHTEN
            LayerBlend.ADD -> PorterDuff.Mode.ADD
            else -> null
        }
        if (m != null) xfermode = PorterDuffXfermode(m)
    }
}

/** One transparent full-frame bitmap plus how it is laid over the video. */
class RenderedLayer(
    val bmp: Bitmap,
    val opacity: Float,
    val blend: LayerBlend,
    val fromMs: Long = 0L,
    val toMs: Long = Long.MAX_VALUE,
) {
    fun visibleAt(tMs: Long) = tMs >= fromMs && tMs < toMs
}

private fun window(timed: Boolean, from: Long, until: Long): Pair<Long, Long> =
    if (timed) from to until else 0L to Long.MAX_VALUE

/**
 * Draws shapes (bottom) then text (top). Consecutive plain layers share one bitmap; a layer with a
 * blend mode gets its own bitmap so it can be blended against the video later.
 */
fun renderLayers(w: Int, h: Int, overlays: List<TextOverlay>, elements: List<ShapeElement>): List<RenderedLayer> {
    val out = ArrayList<RenderedLayer>()
    var sharedCanvas: Canvas? = null
    var sharedFrom = 0L
    var sharedUntil = Long.MAX_VALUE

    fun add(opacity: Float, blend: LayerBlend, from: Long, until: Long, draw: (Canvas) -> Unit) {
        if (blend == LayerBlend.NORMAL || !blend.available) {
            // plain layers share a bitmap only when they appear/disappear at the same times
            val c = sharedCanvas?.takeIf { sharedFrom == from && sharedUntil == until } ?: run {
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                out += RenderedLayer(b, 1f, LayerBlend.NORMAL, from, until)
                sharedFrom = from
                sharedUntil = until
                Canvas(b).also { sharedCanvas = it }
            }
            if (opacity >= 0.999f) {
                draw(c)
            } else {
                val sc = c.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (opacity.coerceIn(0f, 1f) * 255).roundToInt())
                draw(c)
                c.restoreToCount(sc)
            }
        } else {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(b))
            out += RenderedLayer(b, opacity, blend, from, until)
            sharedCanvas = null // later plain layers must sit above this one
        }
    }

    for (e in elements) {
        val (f, u) = window(e.timed, e.fromMs, e.toMs)
        add(e.opacity, e.blend, f, u) { drawElements(it, w, h, listOf(e)) }
    }
    for (o in overlays) if (o.text.isNotBlank()) {
        val (f, u) = window(o.timed, o.fromMs, o.toMs)
        add(o.opacity, o.blend, f, u) { drawOverlays(it, w, h, listOf(o)) }
    }
    return out
}

/** [tMs] = source-video time of this frame; null draws every layer regardless of timing. */
fun drawLayers(c: Canvas, layers: List<RenderedLayer>, tMs: Long? = null) {
    for (l in layers) {
        if (tMs != null && !l.visibleAt(tMs)) continue
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (l.opacity.coerceIn(0f, 1f) * 255).roundToInt()
            applyBlend(l.blend)
        }
        c.drawBitmap(l.bmp, 0f, 0f, p)
    }
}

/** String resource of a blend mode's name (labels above stay as the English fallback). */
fun LayerBlend.labelRes(): Int = when (this) {
    LayerBlend.NORMAL -> com.mediaforge.app.R.string.blend_normal
    LayerBlend.MULTIPLY -> com.mediaforge.app.R.string.blend_multiply
    LayerBlend.SCREEN -> com.mediaforge.app.R.string.blend_screen
    LayerBlend.OVERLAY -> com.mediaforge.app.R.string.blend_overlay
    LayerBlend.DARKEN -> com.mediaforge.app.R.string.blend_darken
    LayerBlend.LIGHTEN -> com.mediaforge.app.R.string.blend_lighten
    LayerBlend.ADD -> com.mediaforge.app.R.string.blend_add
    LayerBlend.DIFFERENCE -> com.mediaforge.app.R.string.blend_difference
    LayerBlend.EXCLUSION -> com.mediaforge.app.R.string.blend_exclusion
    LayerBlend.COLOR_DODGE -> com.mediaforge.app.R.string.blend_color_dodge
    LayerBlend.COLOR_BURN -> com.mediaforge.app.R.string.blend_color_burn
    LayerBlend.HARD_LIGHT -> com.mediaforge.app.R.string.blend_hard_light
    LayerBlend.SOFT_LIGHT -> com.mediaforge.app.R.string.blend_soft_light
    LayerBlend.HUE -> com.mediaforge.app.R.string.blend_hue
    LayerBlend.SATURATION -> com.mediaforge.app.R.string.blend_saturation
    LayerBlend.COLOR -> com.mediaforge.app.R.string.blend_color
    LayerBlend.LUMINOSITY -> com.mediaforge.app.R.string.blend_luminosity
}
