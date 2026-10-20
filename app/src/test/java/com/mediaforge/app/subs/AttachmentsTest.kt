package com.mediaforge.app.subs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AttachmentsTest {
    @Test fun uuRoundTrip() {
        for (n in listOf(0, 1, 2, 3, 4, 59, 60, 61, 1000)) {
            val b = Random(n).nextBytes(n)
            val enc = Attachments.encode(b)
            assertTrue(enc.all { it.length <= 80 })
            assertArrayEquals("n=$n", b, Attachments.decode(enc))
        }
        assertEquals(listOf("!!"), Attachments.encode(byteArrayOf(0))) // one byte -> two characters
    }

    @Test fun attachAndSurviveAssWrite() {
        val bytes = Random(1).nextBytes(300)
        var f = SubFile.blank().copy(styles = listOf(AssStyle(fontName = "Amiri")), events = listOf(AssEvent(text = "{\\fnUthmanTN1}x")))
        assertEquals(setOf("Amiri", "UthmanTN1"), Attachments.fontsUsed(f))
        assertEquals(setOf("UthmanTN1"), Attachments.missing(f, setOf("amiri")))
        f = Attachments.attachFont(f, "UthmanTN1_0.ttf", bytes)
        assertEquals(emptySet<String>(), Attachments.missing(f, setOf("amiri")))
        val back = AssFormat.parse(AssFormat.write(f))
        assertEquals(1, back.attachments.size)
        assertArrayEquals(bytes, Attachments.decode(back.attachments[0].lines))
        assertEquals(0, Attachments.remove(f, "UthmanTN1_0.ttf").attachments.size)
    }
}
