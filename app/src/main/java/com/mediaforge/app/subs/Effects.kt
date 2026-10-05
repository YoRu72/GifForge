package com.mediaforge.app.subs

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How a segment moves from one keyframe to the next. */
enum class Curve(val code: String, val accel: Float) {
    LINEAR("L", 1f), EASE_IN("I", 2f), EASE_OUT("O", 0.5f), HOLD("H", 1f);

    fun ease(u: Float): Float = when (this) {
        LINEAR -> u
        EASE_IN -> u * u
        EASE_OUT -> sqrt(u)
        HOLD -> 0f
    }

    companion object {
        fun of(code: String): Curve = entries.firstOrNull { it.code == code } ?: LINEAR
    }
}

/** Properties that can be animated over the life of a line. Opacity is shown as 0..100 % (100 = fully visible). */
enum class EffectProp(val id: String, val min: Float, val max: Float, val def: Float) {
    OPACITY("opacity", 0f, 100f, 100f),
    BLUR("blur", 0f, 20f, 0f),
    BORDER("bord", 0f, 20f, 2f),
    SHADOW("shad", 0f, 20f, 0f),
    SCALE("scale", 0f, 300f, 100f),
    ROTATION("rot", -360f, 360f, 0f),
    SPACING("spacing", -10f, 30f, 0f);

    /** The ASS override tag that sets this property to [v]. */
    fun tag(v: Float): String = when (this) {
        OPACITY -> "\\alpha&H%02X&".format(Locale.US, ((100f - v.coerceIn(0f, 100f)) * 2.55f).roundToInt())
        BLUR -> "\\blur" + num(v)
        BORDER -> "\\bord" + num(v)
        SHADOW -> "\\shad" + num(v)
        SCALE -> "\\fscx" + num(v) + "\\fscy" + num(v)
        ROTATION -> "\\frz" + num(v)
        SPACING -> "\\fsp" + num(v)
    }

    companion object {
        fun of(id: String): EffectProp? = entries.firstOrNull { it.id == id }
    }
}

/** [timeMs] counts from the start of the line. [curve] shapes the segment that leaves this keyframe. */
data class Keyframe(val timeMs: Long, val value: Float, val curve: Curve = Curve.LINEAR)

data class Track(val prop: EffectProp, val keys: List<Keyframe>) {
    /** Value at [t]: held before the first and after the last keyframe, shaped by the curve in between. */
    fun valueAt(t: Long): Float {
        if (keys.isEmpty()) return prop.def
        if (t <= keys.first().timeMs) return keys.first().value
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (t < b.timeMs) {
                if (a.curve == Curve.HOLD || b.timeMs <= a.timeMs) return a.value
                val u = (t - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
                return a.value + (b.value - a.value) * a.curve.ease(u)
            }
        }
        return keys.last().value
    }
}

private fun num(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')

/**
 * Keyframe effects <-> standard ASS. A track becomes one override block at the start of the line:
 *   {\alpha&H00&\t(0,500,1,\alpha&HFF&) MF1;opacity;0:100:L,500:0:L}
 * The tags are plain ASS (Aegisub and libass play them as they are). The text after the tags has no
 * backslash, so renderers ignore it; MediaForge reads it back to rebuild the editable keyframes.
 */
object Effects {
    private const val MARK = "MF1"
    private val BLOCK = Regex("""\{[^}]*MF1;[^}]*\}""")
    private val TRACK = Regex("""MF1;([a-z]+);([^\s}]+)""")

    fun compile(tracks: List<Track>): String {
        val live = tracks.filter { it.keys.isNotEmpty() }
        if (live.isEmpty()) return ""
        val tags = StringBuilder()
        val marks = StringBuilder()
        for (tr in live) {
            val k = tr.keys.sortedBy { it.timeMs }
            tags.append(tr.prop.tag(k[0].value))
            for (i in 0 until k.size - 1) {
                val a = k[i]
                val b = k[i + 1]
                if (b.timeMs <= a.timeMs) continue
                if (a.curve == Curve.HOLD) {
                    if (a.value != b.value) tags.append("\\t(${b.timeMs},${b.timeMs + 1},${tr.prop.tag(b.value)})")
                } else if (a.value != b.value) {
                    tags.append("\\t(${a.timeMs},${b.timeMs},${num(a.curve.accel)},${tr.prop.tag(b.value)})")
                }
            }
            marks.append(' ').append(MARK).append(';').append(tr.prop.id).append(';')
                .append(k.joinToString(",") { "${it.timeMs}:${num(it.value)}:${it.curve.code}" })
        }
        return "{$tags$marks}"
    }

    /** Keyframe tracks stored in [text] (empty when none). */
    fun parse(text: String): List<Track> {
        val block = BLOCK.find(text)?.value ?: return emptyList()
        return TRACK.findAll(block).mapNotNull { m ->
            val prop = EffectProp.of(m.groupValues[1]) ?: return@mapNotNull null
            val keys = m.groupValues[2].split(',').mapNotNull { s ->
                val p = s.split(':')
                val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val v = p.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                Keyframe(t, v, Curve.of(p.getOrNull(2) ?: "L"))
            }
            if (keys.isEmpty()) null else Track(prop, keys)
        }.toList()
    }

    fun strip(text: String): String = BLOCK.replaceFirst(text, "")

    /** Text with the effect block replaced by one built from [tracks] (removed when there are none). */
    fun apply(text: String, tracks: List<Track>): String = compile(tracks) + strip(text)

    // ---------------------------------------------------------------- presets (opacity)

    fun fadeIn(durMs: Long): Track {
        val d = minOf(500L, durMs / 2).coerceAtLeast(1L)
        return Track(EffectProp.OPACITY, listOf(Keyframe(0, 0f), Keyframe(d, 100f)))
    }

    fun fadeOut(durMs: Long): Track {
        val d = minOf(500L, durMs / 2).coerceAtLeast(1L)
        return Track(EffectProp.OPACITY, listOf(Keyframe(durMs - d, 100f), Keyframe(durMs, 0f)))
    }

    fun fadeBoth(durMs: Long): Track {
        val d = minOf(300L, durMs / 3).coerceAtLeast(1L)
        return Track(EffectProp.OPACITY, listOf(Keyframe(0, 0f), Keyframe(d, 100f), Keyframe(durMs - d, 100f), Keyframe(durMs, 0f)))
    }

    // ---------------------------------------------------------------- presets (FX1 part 1)
    /** Ready-made animations. Each is a list of tracks; applying one replaces the tracks of the same properties and keeps the others. */
    enum class Preset { POP, ZOOM_IN, ZOOM_OUT, PULSE, BLUR_IN, BLUR_OUT, GLOW, SPIN, SHAKE, FLICKER }

    fun preset(p: Preset, durMs: Long): List<Track> {
        val d = durMs.coerceAtLeast(2L)
        val a = minOf(400L, d / 2).coerceAtLeast(1L)   // intro length
        fun k(t: Long, v: Float, c: Curve = Curve.LINEAR) = Keyframe(t.coerceIn(0L, d), v, c)
        val fadeIn = Track(EffectProp.OPACITY, listOf(k(0, 0f), k(a, 100f)))
        return when (p) {
            Preset.POP -> listOf(Track(EffectProp.SCALE, listOf(k(0, 40f, Curve.EASE_OUT), k(a * 3 / 4, 115f), k(a, 100f))), Track(EffectProp.OPACITY, listOf(k(0, 0f), k(a / 2, 100f))))
            Preset.ZOOM_IN -> listOf(Track(EffectProp.SCALE, listOf(k(0, 70f, Curve.EASE_OUT), k(a, 100f))), fadeIn)
            Preset.ZOOM_OUT -> listOf(Track(EffectProp.SCALE, listOf(k(d - a, 100f, Curve.EASE_IN), k(d, 140f))), Track(EffectProp.OPACITY, listOf(k(d - a, 100f), k(d, 0f))))
            Preset.PULSE -> {
                val beat = 700L; val ks = mutableListOf<Keyframe>(); var t = 0L
                while (t < d) { ks += k(t, 100f); ks += k(t + beat / 2, 108f); t += beat }
                ks += k(d, 100f); listOf(Track(EffectProp.SCALE, ks.distinctBy { it.timeMs }))
            }
            Preset.BLUR_IN -> listOf(Track(EffectProp.BLUR, listOf(k(0, 12f, Curve.EASE_OUT), k(a, 0f))), fadeIn)
            Preset.BLUR_OUT -> listOf(Track(EffectProp.BLUR, listOf(k(d - a, 0f, Curve.EASE_IN), k(d, 12f))), Track(EffectProp.OPACITY, listOf(k(d - a, 100f), k(d, 0f))))
            Preset.GLOW -> listOf(Track(EffectProp.BLUR, listOf(k(0, 2f), k(d / 2, 6f), k(d, 2f))), Track(EffectProp.BORDER, listOf(k(0, 3f), k(d / 2, 6f), k(d, 3f))))
            Preset.SPIN -> listOf(Track(EffectProp.ROTATION, listOf(k(0, 90f, Curve.EASE_OUT), k(a, 0f))), Track(EffectProp.SCALE, listOf(k(0, 50f, Curve.EASE_OUT), k(a, 100f))), fadeIn)
            Preset.SHAKE -> {
                val len = minOf(d, 700L); val ks = mutableListOf(k(0, 0f)); var t = 60L; var s = -3f
                while (t < len) { ks += k(t, s); s = -s; t += 60L }
                ks += k(len, 0f); listOf(Track(EffectProp.ROTATION, ks.distinctBy { it.timeMs }))
            }
            Preset.FLICKER -> listOf(Track(EffectProp.OPACITY, listOf(k(0, 100f, Curve.HOLD), k(90, 25f, Curve.HOLD), k(170, 100f, Curve.HOLD), k(250, 15f, Curve.HOLD), k(330, 100f)).filter { it.timeMs <= d }.distinctBy { it.timeMs }))
        }
    }

    /** [tracks] with the preset's tracks put in place of any of the same property. */
    fun withPreset(tracks: List<Track>, p: Preset, durMs: Long): List<Track> {
        val add = preset(p, durMs)
        return tracks.filter { t -> add.none { it.prop == t.prop } } + add
    }

    fun neutral(prop: EffectProp, durMs: Long): Track =
        if (prop == EffectProp.OPACITY) fadeIn(durMs)
        else (if (prop == EffectProp.BLUR) 3f else prop.def).let { v -> Track(prop, listOf(Keyframe(0, v), Keyframe(durMs, v))) }
}
