package com.mediaforge.app.subs

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Aegisub tools ported from its source (TypesettingTools/Aegisub, read s18w): Timing Post-Processor
 * (src/dialog_timing_processor.cpp), Select Lines (src/dialog_selection.cpp) and Resolution Resampler
 * (src/resolution_resampler.cpp). Same order of steps and the same default thresholds as Aegisub 3.4.
 * Pure functions on lists, so every tool is one undo step and is unit-tested.
 */
object TimingPost {
    /** Defaults = Aegisub's default_config.json "Timing Post Processor". */
    data class Settings(
        val leadInOn: Boolean = true, val leadIn: Long = 100,
        val leadOutOn: Boolean = true, val leadOut: Long = 350,
        val adjacentOn: Boolean = true, val adjacentGap: Long = 300, val adjacentOverlap: Long = 50, val bias: Double = 0.9,
        val keyframesOn: Boolean = true,
        val keyStartBefore: Long = 200, val keyStartAfter: Long = 150,
        val keyEndBefore: Long = 200, val keyEndAfter: Long = 250,
    )

    private fun collides(a: AssEvent, b: AssEvent) = if (a.startMs < b.startMs) b.startMs < a.endMs else a.startMs < b.endMs

    /**
     * [which] = the lines to process (dialogue lines only; comments are skipped like in Aegisub), [keyframes] = video
     * keyframe times in ms, sorted (empty = no snapping). Returns the new list.
     */
    fun process(events: List<AssEvent>, which: Set<Int>, s: Settings, keyframes: List<Long> = emptyList()): List<AssEvent> {
        val idx = which.filter { it in events.indices && !events[it].comment }.sortedWith(compareBy({ events[it].startMs }, { it }))
        if (idx.isEmpty()) return events
        val cur = events.toMutableList()
        fun e(k: Int) = cur[idx[k]]
        fun set(k: Int, v: AssEvent) { cur[idx[k]] = v }

        // 1. lead-in: start earlier, but never into a previous line it did not already overlap
        if (s.leadInOn && s.leadIn > 0) for (k in idx.indices) {
            var t = e(k).startMs - s.leadIn
            for (j in 0 until k) if (!collides(e(k), e(j))) t = maxOf(t, e(j).endMs)
            set(k, e(k).copy(startMs = t.coerceIn(0L, e(k).startMs)))
        }
        // lead-out: end later, but never into a following line it did not already overlap
        if (s.leadOutOn && s.leadOut > 0) for (k in idx.indices) {
            var t = e(k).endMs + s.leadOut
            for (j in k + 1 until idx.size) if (!collides(e(k), e(j))) t = minOf(t, e(j).startMs)
            set(k, e(k).copy(endMs = maxOf(t, e(k).endMs)))
        }
        // 2. make adjacent: small gaps and small overlaps become one shared boundary (bias 0.9 = closer to the next start)
        if (s.adjacentOn) for (k in 1 until idx.size) {
            val prev = e(k - 1)
            val c = e(k)
            val dist = c.startMs - prev.endMs
            if ((dist < 0 && -dist <= s.adjacentOverlap) || (dist > 0 && dist <= s.adjacentGap)) {
                val at = prev.endMs + (dist * s.bias).toLong()
                set(k - 1, prev.copy(endMs = maxOf(at, prev.startMs)))
                set(k, c.copy(startMs = minOf(at, c.endMs)))
            }
        }
        // 3. keyframe snapping (scene timing)
        if (s.keyframesOn && keyframes.isNotEmpty()) for (k in idx.indices) {
            var c = e(k)
            nearest(keyframes, c.startMs)?.let { kf ->
                if ((kf > c.startMs && kf - c.startMs <= s.keyStartBefore) || (kf < c.startMs && c.startMs - kf <= s.keyStartAfter)) c = c.copy(startMs = kf)
            }
            nearest(keyframes, c.endMs)?.let { kf ->
                if ((kf > c.endMs && kf - c.endMs <= s.keyEndBefore) || (kf < c.endMs && c.endMs - kf <= s.keyEndAfter)) c = c.copy(endMs = kf)
            }
            if (c.endMs > c.startMs) set(k, c)
        }
        return cur
    }

    /** The keyframe time closest to [t] (sorted list), or null when empty. */
    fun nearest(sorted: List<Long>, t: Long): Long? {
        if (sorted.isEmpty()) return null
        var i = sorted.binarySearch(t)
        if (i >= 0) return sorted[i]
        i = -i - 1
        val a = sorted.getOrNull(i - 1)
        val b = sorted.getOrNull(i)
        return when {
            a == null -> b
            b == null -> a
            t - a <= b - t -> a
            else -> b
        }
    }
}

/** Aegisub "Select Lines": match a field, then set / add to / take from / intersect with the current marks. */
object SelectLines {
    enum class Field { TEXT, STYLE, ACTOR, EFFECT }
    enum class Mode { CONTAINS, EXACT, REGEX }
    enum class Action { SET, ADD, SUBTRACT, INTERSECT }

    data class Spec(
        val query: String = "", val field: Field = Field.TEXT, val mode: Mode = Mode.CONTAINS, val matchCase: Boolean = false,
        val invert: Boolean = false, val dialogue: Boolean = true, val comments: Boolean = false,
    )

    private fun get(e: AssEvent, f: Field) = when (f) { Field.TEXT -> e.text; Field.STYLE -> e.style; Field.ACTOR -> e.actor; Field.EFFECT -> e.effect }

    /** Matching line indices; null when the regular expression is invalid. */
    fun matches(events: List<AssEvent>, s: Spec): Set<Int>? {
        val re: Regex? = if (s.mode == Mode.REGEX) try {
            Regex(s.query, if (s.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) { return null } else null
        val q = if (s.matchCase) s.query else s.query.lowercase(Locale.ROOT)
        return events.indices.filter { i ->
            val e = events[i]
            if (e.comment && !s.comments || !e.comment && !s.dialogue) return@filter false
            val v = get(e, s.field).let { if (s.matchCase) it else it.lowercase(Locale.ROOT) }
            val hit = when (s.mode) {
                Mode.CONTAINS -> v.contains(q)
                Mode.EXACT -> v == q
                Mode.REGEX -> re!!.containsMatchIn(get(e, s.field))
            }
            hit != s.invert
        }.toSet()
    }

    fun combine(old: Set<Int>, hits: Set<Int>, a: Action): Set<Int> = when (a) {
        Action.SET -> hits
        Action.ADD -> old + hits
        Action.SUBTRACT -> old - hits
        Action.INTERSECT -> old intersect hits
    }
}

/**
 * Aegisub "Resample Resolution" for the common case (no added borders, optional stretch): styles (size, outline,
 * shadow, spacing, margins, ScaleX for a new aspect), line margins and the tags \pos \move \org \clip \iclip (rect and
 * vector) \fs \bord \xbord \ybord \shad \xshad \yshad \fsp \pbo \fscx, drawings (\p), then PlayResX/Y.
 */
object Resample {
    private fun f(v: Double) = if (abs(v - v.roundToLong()) < 1e-6) v.roundToLong().toString() else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')

    fun resample(file: SubFile, destX: Int, destY: Int): SubFile {
        val sx = file.playResX.coerceAtLeast(1)
        val sy = file.playResY.coerceAtLeast(1)
        if (destX <= 0 || destY <= 0 || (destX == sx && destY == sy)) return file
        val rx = destX.toDouble() / sx
        val ry = destY.toDouble() / sy
        val oldAr = sx.toDouble() / sy
        val newAr = destX.toDouble() / destY
        val ar = if (abs(oldAr - newAr) / newAr > .01) newAr / oldAr else 1.0 // stretch mode (Aegisub's Stretch)
        val styles = file.styles.map { st ->
            st.copy(
                fontSize = (st.fontSize * ry).roundToInt().toFloat(), outlineWidth = (st.outlineWidth * ry).toFloat(),
                shadow = (st.shadow * ry).toFloat(), spacing = (st.spacing * ry).toFloat(), scaleX = (st.scaleX * ar).toFloat(),
                marginL = (st.marginL * rx).roundToInt(), marginR = (st.marginR * rx).roundToInt(), marginV = (st.marginV * ry).roundToInt(),
            )
        }
        val events = file.events.map { e ->
            if (e.comment && (e.effect.startsWith("template") || e.effect.startsWith("code"))) e
            else e.copy(
                text = tags(e.text, rx, ry, ar),
                marginL = if (e.marginL != 0) (e.marginL * rx).roundToInt() else 0,
                marginR = if (e.marginR != 0) (e.marginR * rx).roundToInt() else 0,
                marginV = if (e.marginV != 0) (e.marginV * ry).roundToInt() else 0,
            )
        }
        return file.copy(styles = styles, events = events).withInfo("PlayResX", destX.toString()).withInfo("PlayResY", destY.toString())
    }

    private val NUM = Regex("""-?\d+(?:\.\d+)?""")
    private val BLOCK = Regex("""\{[^}]*\}""")
    private val POS = Regex("""\\(pos|move|org)\(([^)]*)\)""")
    private val CLIP = Regex("""\\(i?clip)\(([^)]*)\)""")
    private val SIZE_Y = Regex("""\\(fs|bord|ybord|shad|yshad|fsp|pbo)(-?\d+(?:\.\d+)?)""")
    private val SIZE_X = Regex("""\\(xbord|xshad)(-?\d+(?:\.\d+)?)""")
    private val FSCX = Regex("""\\fscx(-?\d+(?:\.\d+)?)""")
    private val P_ON = Regex("""\\p([1-9]\d*)""")

    fun drawing(d: String, rx: Double, ry: Double): String {
        var x = true
        return d.trim().split(Regex("\\s+")).joinToString(" ") { tok ->
            val v = tok.toDoubleOrNull()
            when {
                v != null -> { val r = f(if (x) v * rx else v * ry); x = !x; r }
                tok.length == 1 && tok.lowercase() in listOf("m", "n", "l", "b", "s", "p", "c") -> { x = true; tok }
                else -> tok
            }
        }
    }

    private fun scaleBlock(b: String, rx: Double, ry: Double, ar: Double): String {
        var s = POS.replace(b) { m ->
            val parts = m.groupValues[2].split(',')
            // \move(x1,y1,x2,y2[,t1,t2]): only the first four are coordinates
            val out = parts.mapIndexed { i, p -> val v = p.trim().toDoubleOrNull(); if (v == null || i >= 4) p.trim() else f(if (i % 2 == 0) v * rx else v * ry) }
            "\\" + m.groupValues[1] + "(" + out.joinToString(",") + ")"
        }
        s = CLIP.replace(s) { m ->
            val inner = m.groupValues[2]
            val parts = inner.split(',')
            val body = if (parts.size == 4 && parts.all { NUM.matches(it.trim()) }) {
                parts.mapIndexed { i, p -> f(p.trim().toDouble() * if (i % 2 == 0) rx else ry) }.joinToString(",")
            } else if (parts.size == 2 && NUM.matches(parts[0].trim())) {
                // vector clip with a scale: coordinates are in 2^(scale-1) units, same scaling applies
                parts[0].trim() + "," + drawing(parts[1], rx, ry)
            } else drawing(inner, rx, ry)
            "\\" + m.groupValues[1] + "(" + body + ")"
        }
        s = SIZE_Y.replace(s) { m -> "\\" + m.groupValues[1] + f(m.groupValues[2].toDouble() * ry) }
        s = SIZE_X.replace(s) { m -> "\\" + m.groupValues[1] + f(m.groupValues[2].toDouble() * rx) }
        if (ar != 1.0) s = FSCX.replace(s) { m -> "\\fscx" + f(m.groupValues[1].toDouble() * ar) }
        return s
    }

    /** Scales override blocks and the drawings that follow \p1.. until \p0. */
    fun tags(text: String, rx: Double, ry: Double, ar: Double = 1.0): String {
        val out = StringBuilder()
        var last = 0
        var drawingOn = false
        for (m in BLOCK.findAll(text)) {
            val plain = text.substring(last, m.range.first)
            out.append(if (drawingOn) drawing(plain, rx / ar, ry) else plain)
            val blk = scaleBlock(m.value, rx, ry, ar)
            out.append(blk)
            P_ON.findAll(blk).lastOrNull()?.let { drawingOn = true }
            if (blk.contains("\\p0")) drawingOn = false
            last = m.range.last + 1
        }
        val tail = text.substring(last)
        out.append(if (drawingOn) drawing(tail, rx / ar, ry) else tail)
        return out.toString()
    }

    /** Aegisub uses the geometric mean when x and y scale differently (sizes that are neither x nor y). */
    fun mean(rx: Double, ry: Double) = if (rx == ry) rx else sqrt(rx * ry)
}
