package com.gifforge.app.media

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SortField(val label: String) {
    NAME("Name"), DATE("Date modified"), SIZE("Size"), DURATION("Duration"), RESOLUTION("Resolution")
}

/** Everything the search bar and filter chips can express. Labels are what the chips display. */
data class LibraryFilter(
    val query: String = "",
    val type: String = "All", // All / Videos / GIFs
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val sizeLabel: String? = null,
    val dateFromSec: Long? = null, // inclusive
    val dateToSec: Long? = null,   // exclusive
    val dateLabel: String? = null,
    val minLongSide: Int = 0,
    val resLabel: String? = null,
    val minDurMs: Long? = null,
    val maxDurMs: Long? = null,
    val durLabel: String? = null,
    val sort: SortField = SortField.DATE,
    val ascending: Boolean = false,
) {
    val tokens: List<String>
        get() = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    val hasActiveFilters: Boolean
        get() = type != "All" || sizeLabel != null || dateLabel != null || resLabel != null || durLabel != null

    fun cleared() = copy(
        type = "All",
        minSize = null, maxSize = null, sizeLabel = null,
        dateFromSec = null, dateToSec = null, dateLabel = null,
        minLongSide = 0, resLabel = null,
        minDurMs = null, maxDurMs = null, durLabel = null,
    )
}

fun applyFilters(items: List<LibraryItem>, f: LibraryFilter): List<LibraryItem> {
    val tokens = f.tokens
    val durationActive = f.minDurMs != null || f.maxDurMs != null
    val out = items.filter { i ->
        (f.type == "All" || (f.type == "Videos" && i.isVideo) || (f.type == "GIFs" && !i.isVideo)) &&
            (f.minSize == null || i.sizeBytes >= f.minSize) &&
            (f.maxSize == null || i.sizeBytes <= f.maxSize) &&
            (f.dateFromSec == null || i.dateSec >= f.dateFromSec) &&
            (f.dateToSec == null || i.dateSec < f.dateToSec) &&
            (f.minLongSide == 0 || maxOf(i.width, i.height) >= f.minLongSide) &&
            (!durationActive || (i.isVideo &&
                (f.minDurMs == null || i.durationMs >= f.minDurMs) &&
                (f.maxDurMs == null || i.durationMs <= f.maxDurMs))) &&
            tokens.all { t -> i.nameLower.contains(t) || i.bucketLower.contains(t) }
    }
    val cmp: Comparator<LibraryItem> = when (f.sort) {
        SortField.NAME -> compareBy<LibraryItem> { it.nameLower }
        SortField.DATE -> compareBy<LibraryItem> { it.dateSec }
        SortField.SIZE -> compareBy<LibraryItem> { it.sizeBytes }
        SortField.DURATION -> compareBy<LibraryItem> { it.durationMs }
        SortField.RESOLUTION -> compareBy<LibraryItem> { it.width.toLong() * it.height }
    }
    return out.sortedWith(if (f.ascending) cmp else cmp.reversed())
}

// ---- date helpers (java.util only, works on API 24) ----

fun startOfDaySec(daysAgo: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return c.timeInMillis / 1000
}

fun startOfYearSec(): Long {
    val c = Calendar.getInstance()
    c.set(c.get(Calendar.YEAR), Calendar.JANUARY, 1, 0, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis / 1000
}

/** Date pickers return UTC midnight; convert to local midnight (plus optional day offset). */
fun pickerMillisToLocalSec(utcMillis: Long, plusDays: Int = 0): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val l = Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        add(Calendar.DAY_OF_YEAR, plusDays)
    }
    return l.timeInMillis / 1000
}

fun fmtDate(sec: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(sec * 1000))
