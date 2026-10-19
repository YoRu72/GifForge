package com.mediaforge.app.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 17.a2: undo/redo, line operations, find/replace, encodings, keyframe effects, RTL punctuation. */
class EditingTest {
    private fun ev(s: Long, e: Long, t: String, style: String = "Default") = AssEvent(startMs = s, endMs = e, text = t, style = style)
    private fun file(vararg e: AssEvent) = SubFile.blank().copy(events = e.toList())

    @Test fun historyUndoRedoAndMerge() {
        val a = file(ev(0, 1000, "a"))
        val h = History(a, mergeMs = 800)
        assertFalse(h.canUndo)
        val b = a.copy(events = listOf(ev(0, 1000, "ab")))
        val c = a.copy(events = listOf(ev(0, 1000, "abc")))
        h.record(b, now = 10_000)
        h.record(c, now = 10_300) // typing within 800 ms merges
        assertTrue(h.canUndo)
        assertEquals(a, h.undo())
        assertFalse(h.canUndo)
        assertEquals(c, h.redo())
        assertFalse(h.canRedo)
        h.record(c, now = 20_000) // echo of redo is ignored
        val d = c.copy(events = c.events + ev(1000, 2000, "new")) // line count changes = own step
        h.record(d, now = 20_100)
        assertEquals(c, h.undo())
        h.record(b, now = 30_000) // a new change drops the redo branch
        assertFalse(h.canRedo)
    }

    @Test fun historyCap() {
        var f = file(ev(0, 1, "0"))
        val h = History(f, cap = 5, mergeMs = 0)
        for (i in 1..20) { f = f.copy(events = f.events + ev(i.toLong(), i + 1L, "$i")); h.record(f, now = i * 10_000L) }
        var n = 0
        while (h.undo() != null) n++
        assertEquals(4, n)
    }

    @Test fun splitKeepsTagsAndSharesTime() {
        val e = ev(0, 1000, "{\\b1}abcd efgh")
        val parts = LineOps.split(e, 3) // inside the tag block -> moved after it -> left would be empty
        assertEquals(1, parts.size)
        val p2 = LineOps.split(e, "{\\b1}abcd".length)
        assertEquals(2, p2.size)
        assertEquals("{\\b1}abcd", p2[0].text)
        assertEquals("efgh", p2[1].text)
        assertEquals(p2[0].endMs, p2[1].startMs)
        assertEquals(500L, p2[0].endMs)
        assertEquals(1, LineOps.split(e, 0).size)
        val tiny = LineOps.split(ev(0, 1, "ab"), 1) // 1 ms line: halves must stay ordered
        assertTrue(tiny.all { it.startMs <= it.endMs })
    }

    @Test fun joinMoveSort() {
        val j = LineOps.join(listOf(ev(1000, 2000, "a "), ev(500, 3000, " b")))
        assertEquals(500L, j.startMs)
        assertEquals(3000L, j.endMs)
        assertEquals("a\\Nb", j.text)
        val list = listOf(ev(3, 4, "c", "Z"), ev(1, 2, "a", "b"), ev(2, 9, "b", "A"))
        assertEquals(listOf("a", "c", "b"), LineOps.move(list, 0, 1)!!.map { it.text })
        assertNull(LineOps.move(list, 0, -1))
        assertEquals(listOf("a", "b", "c"), LineOps.sort(list, LineOps.SortBy.START).map { it.text })
        assertEquals(listOf("a", "c", "b"), LineOps.sort(list, LineOps.SortBy.END).map { it.text })
        assertEquals(listOf("b", "a", "c"), LineOps.sort(list, LineOps.SortBy.STYLE).map { it.text })
    }

    @Test fun findAndReplace() {
        val f = file(ev(0, 1, "Hello world"), ev(1, 2, "hello again"), ev(2, 3, "nothing"))
        assertEquals(listOf(0, 1), Find.matches(f, SearchSpec("hello")))
        assertEquals(listOf(1), Find.matches(f, SearchSpec("hello", matchCase = true)))
        assertEquals(1, Find.next(f, SearchSpec("hello"), 0, 1))
        assertEquals(0, Find.next(f, SearchSpec("hello"), 1, 1)) // wraps
        assertEquals(1, Find.next(f, SearchSpec("hello"), 0, -1)) // wraps backwards
        assertTrue(Find.error(SearchSpec("(", regex = true)))
        assertEquals(emptyList<Int>(), Find.matches(f, SearchSpec("(", regex = true)))
        val (g, n) = Find.replaceAll(f, SearchSpec("(\\w+) (\\w+)", regex = true), "$2 $1")
        assertEquals(2, n)
        assertEquals("world Hello", g.events[0].text)
        val (h, m) = Find.replaceAll(f, SearchSpec("o w"), "$1")
        assertEquals(1, m)
        assertEquals("Hell\$1orld", h.events[0].text) // plain mode: replacement taken literally
        assertNull(Find.replaceLine(f.events[2], SearchSpec("zzz"), "x"))
        assertEquals("Signs", Find.replaceLine(ev(0, 1, "", "Sign"), SearchSpec("Sign", field = SearchField.STYLE), "Signs")!!.style)
    }

    @Test fun encodings() {
        val arabic = "مرحبا بالعالم، كيف الحال؟\nسطر ثان"
        for (cs in listOf("UTF-8", "UTF-16LE", "UTF-16BE")) for (bom in listOf(true, false)) {
            val d = Encodings.decode(Encodings.encode(arabic, cs, bom))
            assertEquals("$cs bom=$bom", arabic, d.text)
        }
        val w1256 = Encodings.decode(Encodings.encode(arabic, "windows-1256", false))
        assertEquals(arabic, w1256.text)
        assertEquals("windows-1256", w1256.charset)
        val bomFlag = Encodings.decode(Encodings.encode("x", "UTF-8", true))
        assertTrue(bomFlag.bom)
        assertEquals("Привет мир", Encodings.decodeAs(Encodings.encode("Привет мир", "windows-1251", false), "windows-1251"))
    }

    @Test fun legacyCharsetGuess() {
        val samples = listOf(
            "windows-1256" to "مرحبا بالعالم، كيف الحال؟\nسطر ثان",
            "windows-1256" to "قال الرجل: إنّ الصبر مفتاح الفرج.",
            "windows-1251" to "Привет, как дела? Сегодня хорошая погода.",
            "KOI8-R" to "Привет, как дела? Сегодня хорошая погода.",
            "windows-1253" to "Καλημέρα, τι κάνεις σήμερα;",
            "windows-1255" to "שלום, מה שלומך היום?",
            "windows-1252" to "Café crème, déjà vu à l'été.",
            "windows-1256" to "قَالَ الرَّجُلُ: إِنَّ الصَّبْرَ مِفْتَاحُ الْفَرَجِ.", // full tashkeel
            "ISO-8859-6" to "الكتاب على الطاولة، والقلم في الحقيبة.",
            "windows-1251" to "Добрий день! Як справи? Їжак і єнот.",
            "windows-1251" to "Здравей, как си? Добър ден, приятелю.",
            "windows-1255" to "הספר נמצא על השולחן ליד החלון.",
            "windows-1253" to "Η ΕΛΛΑΔΑ είναι μια όμορφη χώρα.",
            "windows-1254" to "Türkçe altyazı dosyası, güzel değil mi?",
            "windows-1250" to "Dobrý den, jak se máte? Příliš žluťoučký kůň.",
        )
        for ((cs, text) in samples) {
            val d = Encodings.decode(Encodings.encode(text, cs, false))
            assertEquals(cs, text, d.text)
        }
    }

    @Test fun effectsRoundTrip() {
        val tracks = listOf(
            Track(EffectProp.OPACITY, listOf(Keyframe(0, 0f, Curve.EASE_OUT), Keyframe(500, 100f))),
            Track(EffectProp.BLUR, listOf(Keyframe(0, 3f, Curve.HOLD), Keyframe(800, 0.5f))),
        )
        val text = Effects.apply("{\\an8}Hi", tracks)
        assertTrue(text, text.startsWith("{\\alpha&HFF&\\t(0,500,0.5,\\alpha&H00&)\\blur3\\t(800,801,\\blur0.5) MF1;"))
        assertEquals(tracks, Effects.parse(text))
        assertEquals("{\\an8}Hi", Effects.strip(text))
        assertEquals("{\\an8}Hi", Effects.apply(text, emptyList()))
        val mid = tracks[0].valueAt(250)
        assertTrue("$mid", mid > 50f && mid < 100f) // ease-out is past half way at half time
        assertEquals(3f, tracks[1].valueAt(799), 0.0001f)
        assertEquals(0.5f, tracks[1].valueAt(5000), 0.0001f)
    }

    @Test fun rtlPunctuation() {
        assertEquals("مرحبا.", RtlFix.fixLine(".مرحبا"))
        assertEquals("{\\an8}كيف؟", RtlFix.fixLine("{\\an8}؟كيف"))
        assertEquals(".hello", RtlFix.fixLine(".hello")) // LTR lines are left alone
        assertEquals("أهلا!\\Nسلام.", RtlFix.fixText("!أهلا\\N.سلام"))
    }
}
