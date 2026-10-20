package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Test

class NormaliseTest {
    @Test fun arabicNormalisation() {
        assertEquals("{\\fnX}الامل ياتي", ArabicTools.normalizeLetters("{\\fnX}الأمل يأتي"))
        assertEquals("علي", ArabicTools.normalizeLetters("على")) // alef maqsura -> yeh (search normalisation)
        assertEquals("كتب", ArabicTools.stripTashkeel("كَتَبَ"))
        assertEquals("كتب", ArabicTools.stripTatweel("كـتـب"))
        assertEquals("كَتَب", ArabicTools.stripTatweel("كَتَـب"))
    }

    @Test fun fixRulesOffByDefaultStillRun() {
        val list = listOf(AssEvent(startMs = 0, endMs = 1000, text = "أَهـلًا"))
        val r = FixOps.run(list, setOf(FixRule.AR_TATWEEL, FixRule.AR_TASHKEEL, FixRule.AR_LETTERS))
        assertEquals("اهلا", r.events[0].text)
        assertEquals(3, r.fixes.size)
    }

    @Test fun pasteOver() {
        val a = AssEvent(startMs = 0, endMs = 1000, text = "a", style = "S1", actor = "X")
        val b = AssEvent(startMs = 500, endMs = 2000, text = "b", style = "S2")
        val r = PasteOver.apply(a, b, setOf(PasteOver.Field.TEXT, PasteOver.Field.STYLE))
        assertEquals("b", r.text); assertEquals("S2", r.style); assertEquals(0L, r.startMs); assertEquals("X", r.actor)
        val list = PasteOver.applyAll(listOf(a, a, a), listOf(2, 0), listOf(b), setOf(PasteOver.Field.START))
        assertEquals(listOf(0L, 0L, 500L), list.map { it.startMs })
    }
}
