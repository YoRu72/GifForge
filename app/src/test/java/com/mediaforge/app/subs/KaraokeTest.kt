package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Karaoke model ported from Aegisub (libaegisub/ass/karaoke.cpp). */
class KaraokeTest {
    @Test fun parseAndBuild() {
        val t = "{\\an8}{\\k20}ka{\\kf30}ra{\\K50}o{\\ko10\\i1}ke"
        val k = Karaoke.parse(t)
        assertEquals("{\\an8}", k.prefix)
        assertEquals(listOf("ka", "ra", "o", "{\\i1}ke"), k.syls.map { it.text })
        assertEquals(listOf(0L, 200L, 500L, 1000L), k.syls.map { it.startMs })
        assertEquals(listOf("\\k", "\\kf", "\\kf", "\\ko"), k.syls.map { it.tag })
        assertEquals("{\\an8}{\\k20}ka{\\kf30}ra{\\kf50}o{\\ko10}{\\i1}ke", Karaoke.build(k))
        assertEquals(k, Karaoke.parse(Karaoke.build(k)))
        assertFalse(Karaoke.parse("plain {\\i1}text").isKaraoke)
        assertTrue(Karaoke.hasKaraoke(t))
    }

    @Test fun effectBlockStaysFirst() {
        val fx = Effects.compile(listOf(Effects.fadeIn(1000)))
        val k = Karaoke.parse(fx + "{\\k50}a{\\k50}b")
        assertEquals(fx, k.prefix)
        assertTrue(Karaoke.build(k).startsWith(fx))
    }

    @Test fun autoSplitAndNormalize() {
        val e = AssEvent(startMs = 1000, endMs = 3000, text = "one two three")
        val k = Karaoke.parse(Karaoke.fromPlain(e))
        assertEquals(listOf("one ", "two ", "three"), k.syls.map { it.text })
        assertEquals(2000L, k.syls.sumOf { it.durMs })
        val n = Karaoke.normalize(listOf(Syl(0, 500), Syl(500, 500)), 1500)
        assertEquals(1000L, n.last().durMs)
        val cut = Karaoke.normalize(listOf(Syl(0, 500), Syl(500, 500), Syl(1000, 500)), 700)
        assertEquals(listOf(500L, 200L, 0L), cut.map { it.durMs })
    }

    @Test fun splitMergeSetStart() {
        val one = listOf(Syl(0, 1000, "\\k", "abcd"))
        val s = Karaoke.split(one, 0, 1) // "a" + "bcd": 750 ms go to bcd
        assertEquals(listOf(250L, 750L), s.map { it.durMs })
        assertEquals(250L, s[1].startMs)
        assertEquals(one.first().copy(), Karaoke.merge(s, 1).first())
        val moved = Karaoke.setStart(s, 1, 400)
        assertEquals(listOf(400L, 600L), moved.map { it.durMs })
        assertEquals(listOf(500L, 500L), Karaoke.nudge(s, 0, 250).map { it.durMs })
        assertEquals(-1, Karaoke.current(s, -1))
        assertEquals(1, Karaoke.current(s, 300))
        assertEquals(2, Karaoke.current(s, 2000))
    }

    @Test fun arabicSplitRules() {
        val w = "كتب الولد"
        assertFalse(Karaoke.canSplit(w, 1)) // inside a joined word: ك|تب
        assertTrue(Karaoke.canSplit(w, 4))  // after the space
        assertTrue(Karaoke.canSplit("دار", 1)) // after د (does not join forward)
        assertFalse(Karaoke.canSplit("كَتب", 1)) // between a letter and its mark
        assertTrue(Karaoke.canSplit("karaoke", 2))
        assertFalse(Karaoke.canSplit("{\\i1}ab", 2)) // inside a tag
    }

    @Test fun splitAndJoinLines() {
        val e = AssEvent(startMs = 1000, endMs = 2000, text = "{\\k30}ka{\\k70}ra")
        val parts = Karaoke.splitLine(e)
        assertEquals(listOf(1000L to 1300L, 1300L to 2000L), parts.map { it.startMs to it.endMs })
        assertEquals(listOf("ka", "ra"), parts.map { it.text })
        val joined = Karaoke.joinLines(parts)
        assertEquals("{\\k30}ka {\\k70}ra", joined.text)
        assertEquals(2000L, joined.endMs)
        val gap = Karaoke.joinLines(listOf(AssEvent(startMs = 0, endMs = 500, text = "a"), AssEvent(startMs = 800, endMs = 1000, text = "b")))
        assertEquals("{\\k50}a {\\k30}{\\k20}b", gap.text)
    }
}
