package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 19.a styles manager and the translator's base style. */
class StylesTest {
    @Test fun baseStyleRoundTrip() {
        val st = AssStyle(name = "X", fontName = "KFGQPC Uthman Taha Naskh", fontSize = 60f, outlineWidth = 2.5f, shadow = 0f, bold = true, marginV = 40, back = 0x80102030.toInt())
        val saved = StyleOps.save(st)
        assertEquals(st.copy(name = "Default"), StyleOps.base(saved))
        assertEquals(StyleOps.FACTORY, StyleOps.base(""))
        assertEquals(StyleOps.FACTORY, StyleOps.base("garbage"))
        assertNull(AssFormat.styleFromLine("Style: A,B"))
    }

    @Test fun importedSrtUsesBase() {
        val base = StyleOps.FACTORY.copy(fontName = "Amiri")
        val f = StyleOps.withBase(SrtFormat.parse("1\n00:00:01,000 --> 00:00:02,000\nمرحبا\n"), base)
        assertEquals("Amiri", f.style(f.events[0].style)!!.fontName)
        assertEquals(1, f.styles.size)
    }

    @Test fun updateDeleteAssign() {
        var f = SubFile.blank().copy(
            styles = listOf(AssStyle("Default"), AssStyle("Sign")),
            events = listOf(AssEvent(style = "Default"), AssEvent(style = "Sign"), AssEvent(style = "Gone")),
        )
        assertEquals(setOf("Gone"), StyleOps.missing(f))
        f = StyleOps.update(f, "Sign", AssStyle("Signs", fontSize = 30f))
        assertEquals("Signs", f.events[1].style)
        assertEquals("Signs 2", StyleOps.freeName(f, "Signs"))
        f = StyleOps.delete(f, "Signs")
        assertEquals("Default", f.events[1].style)
        assertEquals(f, StyleOps.delete(f, "Default")) // the last style stays
        assertEquals(listOf("Default", "X", "Gone"), StyleOps.assign(f.copy(styles = f.styles + AssStyle("X")), "X", setOf(1)).events.map { it.style })
    }

    @Test fun clearLineLooksKeepsEffectsAndPosition() {
        val fx = Effects.compile(listOf(Effects.fadeIn(1000)))
        val e = AssEvent(text = fx + "{\\fnArial\\fs40\\b1\\c&H0000FF&\\pos(10,20)\\bord4}مرحبا")
        val (f, n) = StyleOps.clearLineLooks(SubFile.blank().copy(events = listOf(e, AssEvent(text = "plain"))))
        assertEquals(1, n)
        assertEquals(fx + "{\\pos(10,20)}مرحبا", f.events[0].text)
    }

    @Test fun rescale() {
        val st = AssStyle(fontSize = 48f, outlineWidth = 2f, shadow = 1f, marginV = 30)
        val r = StyleOps.rescale(st, 1.5f)
        assertEquals(72f, r.fontSize, 0f)
        assertEquals(3f, r.outlineWidth, 0f)
        assertEquals(1.5f, r.shadow, 0f)
        assertEquals(45, r.marginV)
        assertTrue(StyleOps.rescale(st, 1f) === st)
    }
}
