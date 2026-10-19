package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shift/sync (AEG-C), Arabic tools (AR3/A25), Fix common errors with per-fix preview (SE2). */
class ToolsTest {
    private fun ev(s: Long, e: Long, t: String = "x", layer: Int = 0) = AssEvent(startMs = s, endMs = e, text = t, layer = layer)

    @Test fun shiftAndSync() {
        val list = listOf(ev(1000, 2000), ev(5000, 6000), ev(9000, 9500))
        val all = TimeOps.shift(list, setOf(0, 1, 2), 1500)
        assertEquals(listOf(2500L, 6500L, 10500L), all.map { it.startMs })
        val back = TimeOps.shift(list, setOf(0), -3000)
        assertEquals(0L, back[0].startMs)
        assertEquals(1500L, TimeOps.parseDelta("1.5"))
        assertEquals(-250L, TimeOps.parseDelta("-250ms"))
        val s = TimeOps.linearSync(list, 0, 2000, 2, 18000)!!
        assertEquals(2000L, s[0].startMs)
        assertEquals(18000L, s[2].startMs)
    }

    @Test fun arabicInsertAndIsolate() {
        assertEquals("ab‏c" to 3, ArabicTools.insert("abc", 2, 2, ArabicTools.RLM))
        assertEquals("aXc" to 2, ArabicTools.insert("abc", 2, 1, "X"))
        val (t, caret) = ArabicTools.isolate("مرحبا ABC", 6, 9, rtl = false)
        assertEquals("مرحبا ⁦ABC⁩", t)
        assertEquals(11, caret)
        assertEquals("⁧⁩" to 1, ArabicTools.isolate("", 0, 0, rtl = true))
    }

    @Test fun arabicDirectionToggle() {
        val t = "{\\an8}2024 سنة\\NOK تمام"
        val on = ArabicTools.toggleRtl(t)
        assertEquals("{\\an8}‏2024 سنة\\N‏OK تمام", on)
        assertTrue(ArabicTools.isForcedRtl(on))
        assertEquals(t, ArabicTools.toggleRtl(on))
        assertEquals("ab{\\i1}c", ArabicTools.stripControls("‏a⁧b⁩{\\i1}c‎"))
    }

    @Test fun arabicPunctuation() {
        assertEquals("نعم، لا؛ لماذا؟", ArabicTools.arabicPunctuation("نعم, لا; لماذا?"))
        assertEquals("قال «مرحبا» و1,000", ArabicTools.arabicPunctuation("قال \"مرحبا\" و1,000"))
        assertEquals("Yes, no?", ArabicTools.arabicPunctuation("Yes, no?")) // LTR lines untouched
        assertEquals("{\\pos(1,2)}نعم،\\Nلا", ArabicTools.arabicPunctuation("{\\pos(1,2)}نعم,\\Nلا")) // tags kept
        assertEquals("كيف؟", ArabicTools.arabicPunctuation("?كيف")) // visual-order punctuation moved to the end
    }

    @Test fun mojibakeRepair() {
        val good = listOf("مرحبا بالعالم", "كيف الحال؟")
        val utf8As1252 = good.map { String(it.toByteArray(Charsets.UTF_8), charset("windows-1252")) }
        val r1 = ArabicTools.guessRepair(utf8As1252)
        // windows-1252 has 5 holes, so some UTF-8 Arabic only survives a Latin-1 misread; either repair is fine if it restores the text
        if (r1 != null) assertEquals(good.filterIndexed { i, _ -> ArabicTools.repair(utf8As1252[i], r1) != null },
            utf8As1252.mapNotNull { ArabicTools.repair(it, r1) })
        val latin1 = good.map { String(it.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1) }
        val r2 = ArabicTools.guessRepair(latin1)!!
        assertEquals(good, latin1.map { ArabicTools.repair(it, r2) })
        val w1256As1252 = good.map { String(it.toByteArray(charset("windows-1256")), charset("windows-1252")) }
        val r3 = ArabicTools.guessRepair(w1256As1252)!!
        assertEquals("windows-1256", r3.real)
        assertEquals(good, w1256As1252.map { ArabicTools.repair(it, r3) })
        assertNull(ArabicTools.guessRepair(good)) // already fine
        assertNull(ArabicTools.guessRepair(listOf("plain english")))
        val f = SubFile.blank().copy(events = latin1.map { ev(0, 1, it) })
        val (fixed, n) = ArabicTools.repairFile(f, r2)
        assertEquals(2, n)
        assertEquals(good, fixed.events.map { it.text })
    }

    @Test fun fceTextRules() {
        val list = listOf(
            ev(0, 1000, "  hello   there \\N  i think i'm right  "),
            ev(1000, 2000, "{\\an8}"),
            ev(2000, 3000, "\\Nلماذا?\\N"),
            ev(3000, 4000, "{\\i1}  {\\i0}").copy(comment = true),
            ev(4000, 5000, "{\\p1}m 0 0 l 10 0 10 10{\\p0}"),
        )
        val all = FixRule.entries.toSet()
        val r = FixOps.run(list, all)
        assertEquals(4, r.events.size) // the empty line is removed, the comment and the drawing stay
        assertEquals("hello there\\NI think I'm right", r.events[0].text)
        assertEquals("لماذا؟", r.events[1].text)
        assertTrue(r.events[2].comment)
        assertTrue(r.fixes.any { it.rule == FixRule.EMPTY && it.after == null && it.line == 1 })
        // untick one fix: the result matches the preview without it
        val skip = r.fixes.filter { it.rule == FixRule.EMPTY && it.line == 1 }.map { it.key }.toSet()
        val r2 = FixOps.run(list, all, skip = skip)
        assertEquals(5, r2.events.size)
        assertEquals("{\\an8}", r2.events[1].text)
    }

    @Test fun fceTimingRules() {
        val o = FixOps.Options(minMs = 1000, maxMs = 8000, gapMs = 24, maxCps = 0.0)
        val list = listOf(
            ev(0, 2500),          // overlaps the next
            ev(2000, 2300),       // too short; next starts at 3000
            ev(3000, 20000),      // too long
            ev(20010, 21000),     // gap of 10 ms before it (after long fix the gap is large)
            ev(0, 30000, layer = 1), // other layer: not compared with layer 0
            ev(30000, 30500),
            ev(30500, 31000),     // starts exactly when the previous ends: fine
        )
        val r = FixOps.run(list, FixRule.entries.toSet(), o)
        val e = r.events
        assertEquals(1976L, e[0].endMs)
        assertEquals(2976L, e[1].endMs) // extended to 1000 ms but stops before the next line
        assertEquals(11000L, e[2].endMs)
        assertEquals(8000L, e[4].durationMs) // the layer 1 line is cut to 8 s
        assertEquals(30500L, e[5].endMs) // too short, but the next line starts at 30500: no room to extend, left alone
        assertFalse(r.fixes.any { it.line == 6 && it.rule == FixRule.OVERLAP })
        val same = listOf(ev(1000, 3000), ev(1000, 3000, "top")) // two speakers at once: not an overlap error
        assertTrue(FixOps.run(same, setOf(FixRule.OVERLAP), o).fixes.isEmpty())
    }
}
