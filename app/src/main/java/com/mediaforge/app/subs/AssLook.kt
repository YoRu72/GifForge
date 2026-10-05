package com.mediaforge.app.subs

import java.util.Locale

/** Everything the preview needs to draw one line: style, override tags and effect keyframes already applied. */
data class ResolvedLook(
    val fontName: String,
    val size: Float,
    val bold: Boolean,
    val italic: Boolean,
    val primary: Int,
    val outline: Int,
    val back: Int,
    val alignment: Int,      // numpad 1..9
    val posX: Float?,        // script pixels, null = placed by alignment and margins
    val posY: Float?,
    val border: Float,
    val shadow: Float,
    val blur: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val spacing: Float,
    val opacity: Float,      // 0..1
    val marginL: Int,
    val marginR: Int,
    val marginV: Int,
    val boxed: Boolean,
)

/**
 * Reads and writes the "look" of a subtitle line (font, size, bold, italic, alignment, position, colours) as plain ASS
 * override tags in the first block of the line, so Aegisub and libass (the video export) see exactly the same thing.
 * Effect keyframes (Effects.kt) are read here too, so the preview can show blur, border, shadow, scale, rotation and fades.
 */
object AssLook {
    private val BLOCK = Regex("""\{[^}]*\}""")
    private val LEAD_FX = Regex("""^\{[^}]*MF1;[^}]*\}""")
    private val LEAD_BLOCK = Regex("""^\{([^}]*)\}""")
    private val TRANSFORM = Regex("""\\t\((?:[^()]|\([^)]*\))*\)""")

    private val FN = Regex("""\\fn([^\\]*)""")
    private val FS = Regex("""\\fs(\d+(?:\.\d+)?)""")
    private val B = Regex("""\\b(\d+)""")
    private val I = Regex("""\\i(\d)""")
    private val AN = Regex("""\\an(\d)""")
    private val POS = Regex("""\\pos\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)""")
    private val MOVE = Regex("""\\move\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*(?:,\s*(-?\d+)\s*,\s*(-?\d+)\s*)?\)""")
    private val C1 = Regex("""\\1?c&H([0-9A-Fa-f]{1,8})&?""")
    private val C3 = Regex("""\\3c&H([0-9A-Fa-f]{1,8})&?""")
    private val C4 = Regex("""\\4c&H([0-9A-Fa-f]{1,8})&?""")
    private val BORD = Regex("""\\bord(\d+(?:\.\d+)?)""")
    private val SHAD = Regex("""\\shad(\d+(?:\.\d+)?)""")
    private val BLUR = Regex("""\\(?:blur|be)(\d+(?:\.\d+)?)""")
    private val FSCX = Regex("""\\fscx(\d+(?:\.\d+)?)""")
    private val FSCY = Regex("""\\fscy(\d+(?:\.\d+)?)""")
    private val FRZ = Regex("""\\frz?(-?\d+(?:\.\d+)?)""")
    private val FSP = Regex("""\\fsp(-?\d+(?:\.\d+)?)""")
    private val ALPHA = Regex("""\\alpha&H([0-9A-Fa-f]{1,2})&?""")

    /** Resolves the look of [e] at [tMs] (absolute video time). */
    fun resolve(style: AssStyle, e: AssEvent, tMs: Long): ResolvedLook {
        val tags = BLOCK.findAll(e.text)
            .joinToString("") { it.value.substring(1, it.value.length - 1) }
            .let { TRANSFORM.replace(it, "") }
        fun num(re: Regex): Float? = re.find(tags)?.groupValues?.get(1)?.toFloatOrNull()
        fun col(re: Regex): Int? = re.find(tags)?.groupValues?.get(1)?.let { parseAssColor("&H$it") }

        val t = (tMs - e.startMs).coerceAtLeast(0L)
        val fx = Effects.parse(e.text).associate { it.prop to it.valueAt(t) }
        val tagOpacity = ALPHA.find(tags)?.groupValues?.get(1)?.toIntOrNull(16)?.let { 1f - it / 255f }
        val pos = POS.find(tags)
        // \move(x1,y1,x2,y2[,t1,t2]): the position at time t (whole line when no times are given)
        val mv = if (pos == null) MOVE.find(tags) else null
        var mx: Float? = null; var my: Float? = null
        if (mv != null) {
            val g = mv.groupValues; val x1 = g[1].toFloat(); val y1 = g[2].toFloat(); val x2 = g[3].toFloat(); val y2 = g[4].toFloat()
            val t1 = g[5].toLongOrNull() ?: 0L; val t2 = g[6].toLongOrNull() ?: (e.endMs - e.startMs)
            val u = if (t2 <= t1) 1f else ((t - t1).toFloat() / (t2 - t1)).coerceIn(0f, 1f)
            mx = x1 + (x2 - x1) * u; my = y1 + (y2 - y1) * u
        }
        val an = AN.find(tags)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..9 } ?: style.alignment.takeIf { it in 1..9 } ?: 2

        return ResolvedLook(
            fontName = FN.find(tags)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: style.fontName,
            size = num(FS) ?: style.fontSize,
            bold = B.find(tags)?.groupValues?.get(1)?.let { it != "0" } ?: style.bold,
            italic = I.find(tags)?.groupValues?.get(1)?.let { it != "0" } ?: style.italic,
            primary = col(C1) ?: style.primary,
            outline = col(C3) ?: style.outline,
            back = col(C4) ?: style.back,
            alignment = an,
            posX = pos?.groupValues?.get(1)?.toFloatOrNull() ?: mx,
            posY = pos?.groupValues?.get(2)?.toFloatOrNull() ?: my,
            border = fx[EffectProp.BORDER] ?: num(BORD) ?: style.outlineWidth,
            shadow = fx[EffectProp.SHADOW] ?: num(SHAD) ?: style.shadow,
            blur = fx[EffectProp.BLUR] ?: num(BLUR) ?: 0f,
            scaleX = fx[EffectProp.SCALE] ?: num(FSCX) ?: style.scaleX,
            scaleY = fx[EffectProp.SCALE] ?: num(FSCY) ?: style.scaleY,
            rotation = fx[EffectProp.ROTATION] ?: num(FRZ) ?: style.angle,
            spacing = fx[EffectProp.SPACING] ?: num(FSP) ?: style.spacing,
            opacity = ((fx[EffectProp.OPACITY]?.div(100f)) ?: tagOpacity ?: 1f).coerceIn(0f, 1f),
            marginL = if (e.marginL != 0) e.marginL else style.marginL,
            marginR = if (e.marginR != 0) e.marginR else style.marginR,
            marginV = if (e.marginV != 0) e.marginV else style.marginV,
            boxed = style.borderStyle == 3,
        )
    }

    /** The line as plain text: override blocks removed, ASS breaks turned into real ones. */
    fun plainText(s: String): String =
        BLOCK.replace(s, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")

    /** ASS colour tag value for an Android ARGB int: `&HBBGGRR&`. */
    fun colorTag(argb: Int): String =
        "&H%02X%02X%02X&".format(Locale.US, argb and 0xFF, (argb shr 8) and 0xFF, (argb shr 16) and 0xFF)

    // ---------------------------------------------------------------- writing

    private fun removal(name: String): Regex = when (name) {
        "fn" -> Regex("""\\fn[^\\}]*""")
        "fs" -> Regex("""\\fs(?=[\d.])[\d.]*""")
        "b" -> Regex("""\\b(?=\d)\d*""")
        "i" -> Regex("""\\i(?=\d)\d*""")
        "an" -> Regex("""\\an\d""")
        "pos", "move" -> Regex("""\\(?:pos|move)\([^)]*\)""")   // a line has one or the other
        "c" -> Regex("""\\1?c&H[0-9A-Fa-f]*&?""")
        "3c" -> Regex("""\\3c&H[0-9A-Fa-f]*&?""")
        else -> Regex("""\\$name(?![A-Za-z])[^\\}]*""")
    }

    /**
     * Sets (or, with a null [value], removes) one override tag in the line's look block. [name] is one of
     * fn, fs, b, i, an, pos, c, 3c; [value] is what follows the name, e.g. "Arial", "48", "1", "8", "(640,360)".
     * The look block comes right after the effect block and is created when missing; other blocks are untouched.
     */
    fun setTag(text: String, name: String, value: String?): String {
        val fx = LEAD_FX.find(text)?.value ?: ""
        val rest = text.substring(fx.length)
        val m = LEAD_BLOCK.find(rest)
        val inner = m?.groupValues?.get(1) ?: ""
        val after = if (m != null) rest.substring(m.value.length) else rest
        var cleaned = removal(name).replace(inner, "")
        if (value != null) cleaned += "\\" + name + value
        return fx + (if (cleaned.isEmpty()) "" else "{$cleaned}") + after
    }

    /** Where the line sits without \pos/\move: the alignment point from style/margins, in script pixels. */
    fun anchor(file: SubFile, style: AssStyle, e: AssEvent): Pair<Float, Float> {
        val l = resolve(style, e, e.startMs)
        if (l.posX != null && l.posY != null) return l.posX to l.posY
        val w = file.playResX.toFloat(); val h = file.playResY.toFloat()
        val hz = (l.alignment - 1) % 3; val vt = (l.alignment - 1) / 3
        val x = when (hz) { 0 -> l.marginL.toFloat(); 2 -> w - l.marginR; else -> (w + l.marginL - l.marginR) / 2f }
        val y = when (vt) { 2 -> l.marginV.toFloat(); 1 -> h / 2f; else -> h - l.marginV }
        return x to y
    }

    enum class Slide { IN_LEFT, IN_RIGHT, IN_BELOW, IN_ABOVE, OUT_LEFT, OUT_RIGHT }

    /** Writes a \move that slides the line in or out (distance: 15 % of the width / 12 % of the height). Replaces any \pos. */
    fun slide(file: SubFile, e: AssEvent, kind: Slide): String {
        val (x, y) = anchor(file, file.style(e.style) ?: AssStyle(), e)
        val dx = file.playResX * 0.15f; val dy = file.playResY * 0.12f
        val d = (e.endMs - e.startMs).coerceAtLeast(2L); val a = minOf(400L, d / 2).coerceAtLeast(1L)
        fun n(v: Float) = Math.round(v).toString()
        val v = when (kind) {
            Slide.IN_LEFT -> "(${n(x - dx)},${n(y)},${n(x)},${n(y)},0,$a)"
            Slide.IN_RIGHT -> "(${n(x + dx)},${n(y)},${n(x)},${n(y)},0,$a)"
            Slide.IN_BELOW -> "(${n(x)},${n(y + dy)},${n(x)},${n(y)},0,$a)"
            Slide.IN_ABOVE -> "(${n(x)},${n(y - dy)},${n(x)},${n(y)},0,$a)"
            Slide.OUT_LEFT -> "(${n(x)},${n(y)},${n(x - dx)},${n(y)},${d - a},$d)"
            Slide.OUT_RIGHT -> "(${n(x)},${n(y)},${n(x + dx)},${n(y)},${d - a},$d)"
        }
        return setTag(e.text, "move", v)
    }

    private val LOOK_TAGS = listOf("fn", "fs", "b", "i", "an", "pos", "c", "3c")

    /** Removes every look tag the Look panel writes. */
    fun clearLook(text: String): String = LOOK_TAGS.fold(text) { acc, n -> setTag(acc, n, null) }
}
