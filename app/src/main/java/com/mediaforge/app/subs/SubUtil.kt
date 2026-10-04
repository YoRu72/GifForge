package com.mediaforge.app.subs

import java.util.Locale

private val CLOCK = Regex("""(?:(\d+):)?(\d{1,3}):(\d{2})(?:[.,](\d{1,9}))?""")

/** `[H:]MM:SS[.,]fff` -> milliseconds (1-3 fraction digits are tenths/hundredths/thousandths). */
fun parseClock(s: String): Long? {
    val g = CLOCK.matchEntire(s.trim())?.groupValues ?: return null
    val h = g[1].ifEmpty { "0" }.toLong()
    val frac = g[4]
    val ms = when (frac.length) {
        0 -> 0L
        1 -> frac.toLong() * 100
        2 -> frac.toLong() * 10
        else -> frac.take(3).toLong()
    }
    return ((h * 60 + g[2].toLong()) * 60 + g[3].toLong()) * 1000 + ms
}

/** `HH:MM:SS.mmm` (hours omitted when [hh] is false and the time is under an hour). */
fun formatClock(ms: Long, hh: Boolean = true, sep: Char = '.'): String {
    val t = ms.coerceAtLeast(0L)
    val h = t / 3600000
    val m = (t / 60000) % 60
    val s = (t / 1000) % 60
    val f = t % 1000
    return if (hh || h > 0) "%02d:%02d:%02d%c%03d".format(Locale.US, h, m, s, sep, f)
    else "%02d:%02d%c%03d".format(Locale.US, m, s, sep, f)
}

/** Decodes the few HTML entities that appear in WebVTT/SRT text. */
fun decodeEntities(s: String): String = s
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", "\\h")
    .replace("&lrm;", "\u200E").replace("&rlm;", "\u200F").replace("&amp;", "&")
