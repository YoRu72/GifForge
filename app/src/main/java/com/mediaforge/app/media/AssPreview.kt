package com.mediaforge.app.media

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.AssLook
import com.mediaforge.app.subs.AssStyle
import com.mediaforge.app.subs.ResolvedLook
import com.mediaforge.app.subs.SubFile
import kotlin.math.ceil

/**
 * Draws subtitle lines the way the video export (libass) will: style font, size, colours, outline, shadow, blur,
 * scale, rotation, alignment, margins, \pos and the effect keyframes at the playhead. The caller gives a SOFTWARE
 * canvas (a bitmap), because blur masks are not reliable on hardware canvases before Android 9.
 * Text goes through the same Arabic-safe paint and layout as GIF text (no letter spacing on Arabic, physical alignment).
 */
object AssPreview {
    fun draw(
        canvas: Canvas,
        w: Int,
        h: Int,
        file: SubFile,
        shown: List<AssEvent>,
        tMs: Long,
        videoHeight: Int = 0,
        typefaceFor: (String, Boolean) -> Typeface,
    ) {
        val s = h.toFloat() / file.playResY.coerceAtLeast(1)
        // ScaledBorderAndShadow: no = outline, shadow and blur are in VIDEO pixels (libass). Scaling them by the script
        // resolution instead (old preview) made a 2 px outline on a 288-line script 4x too thick on a 1080p video.
        val bs = if (file.scaledBorderAndShadow || videoHeight <= 0) s else h.toFloat() / videoHeight
        for (e in shown.sortedBy { it.layer }) {
            val text = AssLook.plainText(e.text)
            if (text.isBlank()) continue
            val look = AssLook.resolve(file.style(e.style) ?: AssStyle(), e, tMs)
            drawOne(canvas, w, h, s, bs, file, text, look, typefaceFor)
        }
    }

    private fun drawOne(
        canvas: Canvas, w: Int, h: Int, s: Float, bs: Float, file: SubFile, text: String, l: ResolvedLook,
        typefaceFor: (String, Boolean) -> Typeface,
    ) {
        val alpha = (l.opacity * 255f).toInt().coerceIn(0, 255)
        if (alpha == 0) return

        val size = (l.size * s).coerceAtLeast(4f)
        val face = typefaceFor(l.fontName, l.bold)
        val paint = newTextPaint(text, face, size)
        // synthesise bold only when the font has no bold face of its own (libass does the same); a real bold face
        // plus fake bold drew every letter twice as heavy, which made the outline look like a thick smear
        paint.isFakeBoldText = l.bold && !face.isBold
        paint.textSkewX = if (l.italic) -0.25f else 0f
        // Letter spacing breaks Arabic joining (ligatures switch off), so it is only applied to other scripts.
        if (l.spacing != 0f && !hasArabic(text)) paint.letterSpacing = l.spacing * s / size // ar-ok

        val an = if (l.alignment in 1..9) l.alignment else 2
        val hz = (an - 1) % 3        // 0 left, 1 centre, 2 right
        val vt = (an - 1) / 3        // 0 bottom, 1 middle, 2 top
        val avail = ((file.playResX - l.marginL - l.marginR) * s).toInt().coerceAtLeast(40)
        val align = physicalAlignment(text, when (hz) { 0 -> ALIGN_LEFT; 2 -> ALIGN_RIGHT; else -> ALIGN_CENTER })

        // Measure with the full width, then shrink the box to the widest line so \pos and alignment anchor the text itself.
        var layout = buildLayout(text, paint, avail, align)
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = maxOf(widest, layout.getLineWidth(i))
        val boxW = (ceil(widest).toInt() + 2).coerceIn(1, avail)
        layout = buildLayout(text, paint, boxW, align)
        val bw = boxW.toFloat()
        val bh = layout.height.toFloat()

        val px = l.posX
        val py = l.posY
        val x0: Float
        val y0: Float
        if (px != null && py != null) {
            x0 = when (hz) { 0 -> px * s; 2 -> px * s - bw; else -> px * s - bw / 2f }
            y0 = when (vt) { 2 -> py * s; 1 -> py * s - bh / 2f; else -> py * s - bh }
        } else {
            val ml = l.marginL * s
            val mr = l.marginR * s
            val mv = l.marginV * s
            x0 = when (hz) { 0 -> ml; 2 -> w - mr - bw; else -> (w + ml - mr) / 2f - bw / 2f }
            y0 = when (vt) { 2 -> mv; 1 -> (h - bh) / 2f; else -> h - mv - bh }
        }
        // Rotation and scale pivot on the alignment point, like libass.
        val ax = x0 + when (hz) { 0 -> 0f; 2 -> bw; else -> bw / 2f }
        val ay = y0 + when (vt) { 2 -> 0f; 1 -> bh / 2f; else -> bh }

        val saved = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alpha)
        canvas.translate(ax, ay)
        if (l.rotation != 0f) canvas.rotate(-l.rotation)
        if (l.scaleX != 100f || l.scaleY != 100f) canvas.scale(l.scaleX / 100f, l.scaleY / 100f)
        canvas.translate(x0 - ax, y0 - ay)

        val border = (l.border * bs).coerceAtLeast(0f)
        // libass blurs the outline and the shadow; the fill is blurred only when there is no outline. Blurring the
        // fill as well (the old preview did) turned every line into a soft dark halo that looked like a drop shadow.
        val blurMask = if (l.blur > 0f) BlurMaskFilter(maxOf(l.blur * bs * 1.7f, 0.5f), BlurMaskFilter.Blur.NORMAL) else null
        val outlined = border > 0f && !l.boxed
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND

        if (l.boxed) {
            val pad = maxOf(border, 2f * bs)
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.outline; maskFilter = blurMask }
            canvas.drawRect(-pad, -pad, bw + pad, bh + pad, box)
        }
        if (l.shadow > 0f && Color.alpha(l.back) > 0) {
            canvas.save()
            canvas.translate(l.shadow * bs, l.shadow * bs)
            paint.color = l.back
            paint.maskFilter = blurMask
            paint.strokeWidth = border * 2f
            paint.style = if (outlined) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            layout.draw(canvas)
            canvas.restore()
        }
        if (outlined) {
            paint.maskFilter = blurMask
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border * 2f // centred on the glyph edge: the inner half is covered by the fill
            paint.color = l.outline
            layout.draw(canvas)
        }
        paint.maskFilter = if (outlined) null else blurMask
        paint.style = Paint.Style.FILL
        paint.color = l.primary
        layout.draw(canvas)
        paint.maskFilter = null
        canvas.restoreToCount(saved)
    }
}
