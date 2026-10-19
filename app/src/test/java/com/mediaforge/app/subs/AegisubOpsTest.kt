package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Timing Post-Processor, Select Lines and the resampler, checked against Aegisub's behaviour. */
class AegisubOpsTest {
    private fun ev(s: Long, e: Long, t: String = "x", style: String = "Default", comment: Boolean = false) =
        AssEvent(startMs = s, endMs = e, text = t, style = style, comment = comment)

    @Test fun leadInOutStopAtNeighbours() {
        val list = listOf(ev(1000, 2000), ev(2050, 3000), ev(10000, 11000))
        val s = TimingPost.Settings(adjacentOn = false, keyframesOn = false)
        val r = TimingPost.process(list, list.indices.toSet(), s)
        assertEquals(900L, r[0].startMs)
        assertEquals(2000L, r[1].startMs) // lead-in stops at the end of the previous line
        assertEquals(2000L, r[0].endMs)   // lead-out stops at the (already led-in) start of the next line, as in Aegisub
        assertEquals(3350L, r[1].endMs)
        assertEquals(11350L, r[2].endMs)
    }

    @Test fun adjacentUsesBias() {
        val list = listOf(ev(1000, 2000), ev(2200, 3000), ev(5000, 6000))
        val s = TimingPost.Settings(leadInOn = false, leadOutOn = false, keyframesOn = false)
        val r = TimingPost.process(list, list.indices.toSet(), s)
        assertEquals(2180L, r[0].endMs) // 2000 + 200 * 0.9
        assertEquals(2180L, r[1].startMs)
        assertEquals(5000L, r[2].startMs) // 2000 ms gap: too far
        val over = TimingPost.process(listOf(ev(0, 1040), ev(1000, 2000)), setOf(0, 1), s)
        assertEquals(over[0].endMs, over[1].startMs)
    }

    @Test fun keyframeSnap() {
        val s = TimingPost.Settings(leadInOn = false, leadOutOn = false, adjacentOn = false)
        val r = TimingPost.process(listOf(ev(1100, 2900)), setOf(0), s, listOf(0L, 1000L, 3000L, 9000L))
        assertEquals(1000L, r[0].startMs) // keyframe 100 ms before the start (<= 150)
        assertEquals(3000L, r[0].endMs)   // keyframe 100 ms after the end (<= 200)
        assertEquals(1000L, TimingPost.nearest(listOf(0L, 1000L, 3000L), 1900L))
        assertNull(TimingPost.nearest(emptyList(), 5L))
    }

    @Test fun commentsAndOtherStylesUntouched() {
        val list = listOf(ev(1000, 2000, comment = true), ev(1000, 2000, style = "Sign"))
        val r = TimingPost.process(list, setOf(0), TimingPost.Settings())
        assertEquals(list, r)
    }

    @Test fun selectLines() {
        val list = listOf(ev(0, 1, "Hello"), ev(0, 1, "hello world", style = "Sign"), ev(0, 1, "bye", comment = true))
        assertEquals(setOf(0, 1), SelectLines.matches(list, SelectLines.Spec("hello")))
        assertEquals(setOf(1), SelectLines.matches(list, SelectLines.Spec("Sign", field = SelectLines.Field.STYLE, mode = SelectLines.Mode.EXACT)))
        assertEquals(setOf(2), SelectLines.matches(list, SelectLines.Spec("^b", mode = SelectLines.Mode.REGEX, dialogue = false, comments = true)))
        assertEquals(emptySet<Int>(), SelectLines.matches(list, SelectLines.Spec("hello", invert = true)))
        assertNull(SelectLines.matches(list, SelectLines.Spec("(", mode = SelectLines.Mode.REGEX)))
        assertEquals(setOf(1), SelectLines.combine(setOf(0, 1), setOf(0), SelectLines.Action.SUBTRACT))
        assertEquals(setOf(0), SelectLines.combine(setOf(0, 1), setOf(0, 2), SelectLines.Action.INTERSECT))
    }

    @Test fun resample() {
        val f = SubFile.blank(640, 360).copy(
            styles = listOf(AssStyle(fontSize = 20f, outlineWidth = 1f, marginV = 10)),
            events = listOf(ev(0, 1, "{\\pos(320,180)\\fs30\\bord2\\clip(0,0,320,180)\\move(0,0,100,50,0,500)}A{\\p1}m 0 0 l 10 10{\\p0}")),
        )
        val r = Resample.resample(f, 1280, 720)
        assertEquals(1280, r.playResX)
        assertEquals(40f, r.styles[0].fontSize, 0f)
        assertEquals(2f, r.styles[0].outlineWidth, 0f)
        assertEquals(20, r.styles[0].marginV)
        assertEquals("{\\pos(640,360)\\fs60\\bord4\\clip(0,0,640,360)\\move(0,0,200,100,0,500)}A{\\p1}m 0 0 l 20 20{\\p0}", r.events[0].text)
        assertEquals(f, Resample.resample(f, 640, 360))
    }
}
