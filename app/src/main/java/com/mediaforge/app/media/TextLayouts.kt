package com.mediaforge.app.media

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/** True when the first strong character is right-to-left (Arabic, Hebrew, ...). */
internal fun isRtlText(s: CharSequence): Boolean = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(s, 0, s.length)

internal fun hasArabic(s: CharSequence): Boolean {
    for (ch in s) if (ch.code in 0x0600..0x06FF || ch.code in 0x0750..0x077F || ch.code in 0x08A0..0x08FF ||
        ch.code in 0xFB50..0xFDFF || ch.code in 0xFE70..0xFEFF) return true
    return false
}

/**
 * Left / Center / Right are PHYSICAL choices. Android's NORMAL/OPPOSITE follow the paragraph direction,
 * so on Arabic text "Left" used to come out on the right. Map by direction here, once, for text layers and bars.
 */
internal fun physicalAlignment(text: CharSequence, align: Int): Layout.Alignment {
    val rtl = isRtlText(text)
    return when (align) {
        ALIGN_LEFT -> if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        ALIGN_RIGHT -> if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_CENTER
    }
}

/** Paint for Arabic-safe drawing: no letter spacing (it breaks joining), full-height lines so marks are not clipped. */
internal fun newTextPaint(text: CharSequence, tf: Typeface, size: Float): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = tf
        letterSpacing = 0f
        if (hasArabic(text)) isElegantTextHeight = true
    }

internal fun buildLayout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout {
    val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(align)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
    if (Build.VERSION.SDK_INT >= 28) b.setUseLineSpacingFromFallbacks(true)
    return b.build()
}
