package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Motion tracking on synthetic frames: a textured patch moving over a noisy background. */
class MotionTest {
    private val bg = Random(7).let { r -> ByteArray(200 * 150) { (r.nextInt(60) + 40).toByte() } }
    private val obj = Random(3).let { r -> Array(16) { IntArray(16) { r.nextInt(120) + 120 } } }

    private fun frame(ox: Float, oy: Float): Gray {
        val px = bg.copyOf()
        for (y in 0 until 16) for (x in 0 until 16) {
            val xx = (ox + x).toInt(); val yy = (oy + y).toInt()
            if (xx in 0 until 200 && yy in 0 until 150) px[yy * 200 + xx] = obj[y][x].toByte()
        }
        return Gray(200, 150, px)
    }

    @Test fun followsAMovingPatch() {
        val t = PointTracker(half = 7, radius = 12)
        t.start(frame(40f, 30f), 48f, 38f)
        var last = Triple(0f, 0f, 0f)
        for (i in 1..30) last = t.next(frame(40f + 3 * i, 30f + 2 * i))
        assertTrue("x ${last.first}", abs(last.first - (48f + 90f)) <= 1f)
        assertTrue("y ${last.second} conf ${last.third}", last.third > 0.9f && abs(last.second - (38f + 60f)) <= 1f)
    }

    @Test fun lostWhenTheObjectLeaves() {
        val t = PointTracker(half = 7, radius = 8)
        t.start(frame(40f, 30f), 48f, 38f)
        val r = t.next(Gray(200, 150, bg.copyOf()))
        assertTrue("conf ${r.third}", r.third < t.lostBelow)
    }

    @Test fun simplifyAndApply() {
        val line = (0..10).map { TrackPt(it * 100L, 100f + it * 10, 50f) }
        assertEquals(2, MotionApply.simplify(line, 1f).size)
        val bent = line.take(6) + (6..10).map { TrackPt(it * 100L, 150f, 50f + (it - 5) * 10) }
        assertEquals(3, MotionApply.simplify(bent, 1f).size)

        val e = AssEvent(startMs = 1000, endMs = 2000, text = "{\\an5\\pos(10,10)}Hi")
        val frames = MotionApply.apply(e, line.take(4), 300f, 200f, perFrame = true)
        assertEquals(4, frames.size)
        assertEquals("{\\pos(300,200)\\an5}Hi", frames[0].text)
        assertEquals("{\\pos(330,200)\\an5}Hi", frames[3].text)
        assertEquals(1300L, frames[3].startMs)
        assertEquals(2000L, frames[3].endMs)

        val moves = MotionApply.apply(e, line, 300f, 200f, perFrame = false)
        assertEquals(1, moves.size)
        assertEquals("{\\move(300,200,400,200)\\an5}Hi", moves[0].text)
        assertEquals(50f, MotionApply.at(listOf(TrackPt(0, 0f, 0f), TrackPt(100, 100f, 0f)), 50).first, 0.01f)
    }

    @Test fun effectsRunAcrossPieces() {
        val e = AssEvent(startMs = 0, endMs = 1000, text = Effects.apply("Hi", listOf(Effects.fadeIn(1000))))
        val pts = listOf(TrackPt(0, 0f, 0f), TrackPt(250, 10f, 0f), TrackPt(500, 30f, 0f), TrackPt(750, 60f, 0f))
        val parts = MotionApply.apply(e, pts, 0f, 0f, perFrame = true)
        val second = Effects.parse(parts[1].text).single()
        assertEquals(second.valueAt(0), Effects.parse(e.text).single().valueAt(250), 0.01f)
    }
}
