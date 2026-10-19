package com.mediaforge.app.subs

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * MOT1/MOT2/MOT4 motion tracking. Pure Kotlin, so it is unit-tested on synthetic frames:
 *  - [Gray] is one video frame as 8-bit luma at a reduced working size;
 *  - [PointTracker] follows a patch from frame to frame (normalised cross-correlation in a search window, coarse
 *    then fine, sub-pixel peak, slowly refreshed template, confidence per frame, stops when the object is lost);
 *  - [MotionApply] turns a path into ASS the way Aegisub-Motion does (one line per frame with \pos) or into fewer
 *    lines with \move segments chosen by an error limit (Ramer-Douglas-Peucker).
 * Positions are kept in script pixels (PlayRes) so the result is resolution independent.
 */
class Gray(val w: Int, val h: Int, val px: ByteArray) {
    operator fun get(x: Int, y: Int): Int = px[y * w + x].toInt() and 0xFF
}

/** One tracked position. [tMs] counts from the line start; [conf] is the match score (1 = identical patch). */
data class TrackPt(val tMs: Long, val x: Float, val y: Float, val conf: Float = 1f)

class PointTracker(
    private val half: Int = 12,          // template = (2*half+1)^2 pixels
    private val radius: Int = 24,        // search radius in pixels per frame
    private val minConf: Float = 0.55f,  // below this the object counts as lost
    private val refresh: Float = 0.15f,  // template refresh rate (follows slow changes of light and shape)
) {
    private var tmpl = FloatArray(0)
    private var tx = 0f
    private var ty = 0f

    /** Starts at (x, y) in [f]. */
    fun start(f: Gray, x: Float, y: Float) {
        tx = x.coerceIn(half.toFloat(), (f.w - half - 1).toFloat())
        ty = y.coerceIn(half.toFloat(), (f.h - half - 1).toFloat())
        tmpl = patch(f, tx.roundToInt(), ty.roundToInt())
    }

    private fun patch(f: Gray, cx: Int, cy: Int): FloatArray {
        val n = 2 * half + 1
        val out = FloatArray(n * n)
        var i = 0
        for (y in cy - half..cy + half) for (x in cx - half..cx + half) out[i++] = f[x.coerceIn(0, f.w - 1), y.coerceIn(0, f.h - 1)].toFloat()
        return out
    }

    /** Normalised cross-correlation of the template with [f] centred at (cx, cy); -1..1. */
    private fun ncc(f: Gray, cx: Int, cy: Int, step: Int = 1): Float {
        var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0; var n = 0
        val size = 2 * half + 1
        var dy = -half
        while (dy <= half) {
            var dx = -half
            val yy = cy + dy
            while (dx <= half) {
                val a = tmpl[(dy + half) * size + (dx + half)].toDouble()
                val b = f[(cx + dx).coerceIn(0, f.w - 1), yy.coerceIn(0, f.h - 1)].toDouble()
                sa += a; sb += b; saa += a * a; sbb += b * b; sab += a * b; n++
                dx += step
            }
            dy += step
        }
        val cov = sab - sa * sb / n
        val va = saa - sa * sa / n
        val vb = sbb - sb * sb / n
        if (va < 1e-6 || vb < 1e-6) return if (va < 1e-6 && vb < 1e-6) 1f else 0f
        return (cov / sqrt(va * vb)).toFloat()
    }

    /** Finds the patch in the next frame; returns the new position and its confidence. */
    fun next(f: Gray): Triple<Float, Float, Float> {
        val px = tx.roundToInt()
        val py = ty.roundToInt()
        // exhaustive search in the window: (2*radius+1)^2 positions x template pixels, a few ms per frame at working size
        var best = -2f; var bx = px; var by = py
        for (yy in py - radius..py + radius) for (xx in px - radius..px + radius) {
            val sc = ncc(f, xx, yy)
            if (sc > best) { best = sc; bx = xx; by = yy }
        }
        // sub-pixel: parabola through the neighbours
        fun sub(m: Float, c: Float, p: Float): Float { val d = m - 2 * c + p; return if (abs(d) < 1e-6f) 0f else (0.5f * (m - p) / d).coerceIn(-0.5f, 0.5f) }
        val sx = sub(ncc(f, bx - 1, by), best, ncc(f, bx + 1, by))
        val sy = sub(ncc(f, bx, by - 1), best, ncc(f, bx, by + 1))
        tx = (bx + sx).coerceIn(half.toFloat(), (f.w - half - 1).toFloat())
        ty = (by + sy).coerceIn(half.toFloat(), (f.h - half - 1).toFloat())
        if (best >= minConf) {
            val fresh = patch(f, bx, by)
            for (i in tmpl.indices) tmpl[i] = tmpl[i] * (1 - refresh) + fresh[i] * refresh
        }
        return Triple(tx, ty, best)
    }

    val lostBelow: Float get() = minConf
}

object MotionApply {
    /** Ramer-Douglas-Peucker on (t, x, y): keeps the points needed so no dropped point is farther than [tol] px from its segment. */
    fun simplify(p: List<TrackPt>, tol: Float): List<TrackPt> {
        if (p.size <= 2) return p
        val keep = BooleanArray(p.size); keep[0] = true; keep[p.size - 1] = true
        fun rec(a: Int, b: Int) {
            if (b <= a + 1) return
            var worst = -1f; var wi = -1
            val ta = p[a].tMs; val tb = p[b].tMs
            for (i in a + 1 until b) {
                // where the straight move would be at this time
                val u = if (tb == ta) 0f else (p[i].tMs - ta).toFloat() / (tb - ta)
                val ex = p[a].x + (p[b].x - p[a].x) * u
                val ey = p[a].y + (p[b].y - p[a].y) * u
                val d = hypot(p[i].x - ex, p[i].y - ey)
                if (d > worst) { worst = d; wi = i }
            }
            if (worst > tol) { keep[wi] = true; rec(a, wi); rec(wi, b) }
        }
        rec(0, p.size - 1)
        return p.filterIndexed { i, _ -> keep[i] }
    }

    /** Linear position at [t] between keypoints (manual path, MOT1). */
    fun at(keys: List<TrackPt>, t: Long): Pair<Float, Float> {
        if (keys.isEmpty()) return 0f to 0f
        if (t <= keys.first().tMs) return keys.first().x to keys.first().y
        for (i in 0 until keys.size - 1) {
            val a = keys[i]; val b = keys[i + 1]
            if (t <= b.tMs) {
                val u = if (b.tMs == a.tMs) 1f else (t - a.tMs).toFloat() / (b.tMs - a.tMs)
                return a.x + (b.x - a.x) * u to a.y + (b.y - a.y) * u
            }
        }
        return keys.last().x to keys.last().y
    }

    private fun n(v: Float) = if (abs(v - v.roundToInt()) < 0.005f) v.roundToInt().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private val POSMOVE = Regex("""\\(?:pos|move)\([^)]*\)""")

    /** [text] with its \pos/\move replaced by [tag] (first override block after the effect block). */
    fun withPosition(text: String, tag: String): String {
        val fx = Regex("""^\{[^}]*MF1;[^}]*\}""").find(text)?.value.orEmpty()
        val rest = text.substring(fx.length).replace(POSMOVE, "").replace("{}", "")
        return if (rest.startsWith("{")) fx + "{" + tag + rest.substring(1) else "$fx{$tag}$rest"
    }

    /** The line's effect keyframes cut to the piece [fromMs, fromMs+durMs) of the line, so an animation runs on across pieces. */
    private fun piece(text: String, fromMs: Long, durMs: Long): String {
        val tracks = Effects.parse(text)
        if (tracks.isEmpty()) return text
        return Effects.apply(text, tracks.map { Effects.slice(it, fromMs, durMs) })
    }

    /**
     * Applies a path to [e]. [anchorX]/[anchorY] = where the text sits now (script px); the text follows the path
     * RELATIVE to the first tracked point, so it keeps its distance from the object.
     *  perFrame = true: one line per path point (frame-exact, Aegisub-Motion style);
     *  perFrame = false: fewer lines, each a \move segment (path simplified with [tol] px).
     * Effect keyframes (MF1) are cut per piece ([Effects.slice]), so a fade or blur keeps running across the pieces.
     */
    fun apply(e: AssEvent, path: List<TrackPt>, anchorX: Float, anchorY: Float, perFrame: Boolean, tol: Float = 1.5f): List<AssEvent> {
        val pts = path.filter { it.tMs in 0..e.durationMs }.sortedBy { it.tMs }
        if (pts.isEmpty()) return listOf(e)
        val ox = anchorX - pts.first().x
        val oy = anchorY - pts.first().y
        if (perFrame) {
            return pts.mapIndexed { i, p ->
                val s = e.startMs + p.tMs
                val end = if (i + 1 < pts.size) e.startMs + pts[i + 1].tMs else e.endMs
                e.copy(startMs = s, endMs = maxOf(end, s + 1), text = withPosition(piece(e.text, p.tMs, end - s), "\\pos(${n(p.x + ox)},${n(p.y + oy)})"))
            }.filter { it.endMs > it.startMs }
        }
        val keys = simplify(pts, tol).let { k -> if (k.last().tMs < e.durationMs) k + k.last().copy(tMs = e.durationMs) else k }
        if (keys.size == 1) return listOf(e.copy(text = withPosition(e.text, "\\pos(${n(keys[0].x + ox)},${n(keys[0].y + oy)})")))
        return (0 until keys.size - 1).map { i ->
            val a = keys[i]; val b = keys[i + 1]
            val s = e.startMs + a.tMs
            e.copy(
                startMs = s, endMs = maxOf(e.startMs + b.tMs, s + 1),
                text = withPosition(piece(e.text, a.tMs, b.tMs - a.tMs), "\\move(${n(a.x + ox)},${n(a.y + oy)},${n(b.x + ox)},${n(b.y + oy)})"),
            )
        }
    }
}
