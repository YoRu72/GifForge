package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFiltersTest {
    @Test fun cleanTags() {
        assertEquals("{\\b1\\i1}x", ExportFilters.cleanTags("{\\b1}{\\i1}x"))
        assertEquals("{\\b1\\c&HFF0000&}x", ExportFilters.cleanTags("{\\c&H00FF00&\\b1\\c&HFF0000&}x")) // earlier \c overridden
        assertEquals("x", ExportFilters.cleanTags("{}x"))
        assertEquals("{\\k20}a{\\k30}b", ExportFilters.cleanTags("{\\k20}a{\\k30}b"))
        assertEquals("{\\t(0,500,\\fs40)\\fs20}x", ExportFilters.cleanTags("{\\t(0,500,\\fs40)\\fs20}x"))
        val fx = Effects.apply("{\\an8}Hi", listOf(Effects.fadeIn(500)))
        assertEquals(fx, ExportFilters.cleanTags(fx)) // MF1 note blocks untouched
    }

    @Test fun filters() {
        val f = SubFile.blank().copy(events = listOf(
            AssEvent(startMs = 2000, endMs = 3000, text = "{\\move(0,0,10,10)\\t(0,100,\\fs40)}b"),
            AssEvent(startMs = 0, endMs = 1000, text = "c", comment = true),
            AssEvent(startMs = 1000, endMs = 2000, text = "{\\k50}ka{\\k50}ra"),
        ))
        val r = ExportFilters.apply(f, setOf(ExportFilters.Filter.STRIP_COMMENTS, ExportFilters.Filter.STRIP_EFFECTS, ExportFilters.Filter.STRIP_KARAOKE, ExportFilters.Filter.SORT_BY_TIME))
        assertEquals(listOf("kara", "{\\pos(0,0)}b"), r.events.map { it.text })
        val fx = Effects.apply("Hi", listOf(Effects.fadeIn(500)))
        val n = ExportFilters.apply(SubFile.blank().copy(events = listOf(AssEvent(text = fx))), setOf(ExportFilters.Filter.STRIP_MF_NOTES))
        assertTrue(n.events[0].text, !n.events[0].text.contains("MF1") && n.events[0].text.contains("\\alpha"))
    }

    @Test fun qc() {
        val f = SubFile.blank().copy(events = listOf(
            AssEvent(startMs = 0, endMs = 1500, text = "a"),
            AssEvent(startMs = 1000, endMs = 1200, text = "a very long line that goes past forty two characters for sure yes", style = "Nope"),
            AssEvent(startMs = 1210, endMs = 9000, text = "x\\Ny\\Nz"),
        ))
        val kinds = Qc.run(f).map { it.line to it.kind }
        assertTrue(kinds.toString(), (0 to Qc.Kind.OVERLAP) in kinds)
        assertTrue(kinds.toString(), (1 to Qc.Kind.SHORT) in kinds && (1 to Qc.Kind.TOO_FAST) in kinds && (1 to Qc.Kind.TOO_LONG_LINE) in kinds && (1 to Qc.Kind.MISSING_STYLE) in kinds && (1 to Qc.Kind.SHORT_GAP) in kinds)
        assertTrue(kinds.toString(), (2 to Qc.Kind.LONG) in kinds && (2 to Qc.Kind.TOO_MANY_LINES) in kinds)
    }
}
