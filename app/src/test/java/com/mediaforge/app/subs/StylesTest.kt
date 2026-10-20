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

class VisualEditTest {
    @Test fun dragScaleRotate() {
        val f = SubFile.blank(1280, 720)
        val e = AssEvent(text = "{\\an2}Hi")
        val moved = AssLook.visualEdit(f, e, 10f, -20f, 1f, 0f)
        // anchor of \an2 with default margins 10: x = 640, y = 710
        assertEquals("{\\an2\\pos(650,690)}Hi", moved.text)
        val scaled = AssLook.visualEdit(f, moved, 0f, 0f, 1.5f, 0f)
        assertTrue(scaled.text, scaled.text.contains("\\fscx150\\fscy150"))
        val rot = AssLook.visualEdit(f, scaled, 0f, 0f, 1f, 30f)
        assertTrue(rot.text, rot.text.contains("\\frz-30"))
        val back = AssLook.visualEdit(f, rot, 0f, 0f, 1f, -30f)
        assertTrue(back.text, !back.text.contains("frz"))
    }
}

class TypewriterWipeTest {
    @Test fun typewriterPerWord() {
        val e = AssEvent(startMs = 0, endMs = 2000, text = "{\\an8}كتب الولد الدرس")
        val t = AssLook.typewriter(e)
        assertTrue(t, t.startsWith("{\\an8}كتب "))
        assertEquals(2, Regex("""\\alpha&HFF&""").findAll(t).count())
        assertEquals("كتب الولد الدرس", AssLook.plainText(t))
        assertEquals("x", AssLook.typewriter(AssEvent(text = "x")))
    }

    @Test fun wipeClip() {
        val f = SubFile.blank(1280, 720)
        val t = AssLook.wipe(f, AssEvent(startMs = 0, endMs = 2000, text = "{\\an8}Hi"), AssLook.Wipe.LEFT)
        assertEquals("{\\clip(0,0,0,720)\\t(0,500,\\clip(0,0,1280,720))\\an8}Hi", t)
    }
}
