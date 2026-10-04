package com.mediaforge.app.subs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.regex.PatternSyntaxException

/**
 * A18.c: snapshot undo/redo. The screen calls [record] after every change of the file; changes that arrive
 * within [mergeMs] of each other and keep the same number of lines (typing, slider drags) share one step.
 */
class History(initial: SubFile, private val cap: Int = 100, private val mergeMs: Long = 800L) {
    private val stack = ArrayList<SubFile>().apply { add(initial) }
    private var idx = 0
    private var lastT = 0L
    private var version by mutableIntStateOf(0) // read by the UI so the buttons refresh

    val canUndo: Boolean get() { version; return idx > 0 }
    val canRedo: Boolean get() { version; return idx < stack.size - 1 }

    fun record(f: SubFile, now: Long = System.currentTimeMillis()) {
        if (f == stack[idx]) return // also swallows the echo of an undo/redo
        while (stack.size > idx + 1) stack.removeAt(stack.size - 1)
        val merge = idx > 0 && now - lastT < mergeMs && stack[idx].events.size == f.events.size
        if (merge) stack[idx] = f else { stack.add(f); idx++ }
        if (stack.size > cap) { stack.removeAt(0); idx-- }
        lastT = now; version++
    }

    fun undo(): SubFile? { if (idx <= 0) return null; idx--; lastT = 0L; version++; return stack[idx] }
    fun redo(): SubFile? { if (idx >= stack.size - 1) return null; idx++; lastT = 0L; version++; return stack[idx] }
}

/** A18.e: line operations on plain lists of events (the screen keeps the selection). */
object LineOps {
    private val TAG = Regex("\\{[^}]*\\}")
    private fun visibleLen(s: String) = TAG.replace(s, "").length

    /** Splits [e] at text index [at]; the time is shared by visible text length. Returns the line unchanged when a half would be empty. */
    fun split(e: AssEvent, at: Int): List<AssEvent> {
        var cut = at.coerceIn(0, e.text.length)
        // a cursor inside {...} moves to just after the block, so tags are never torn in half
        val open = e.text.lastIndexOf('{', (cut - 1).coerceAtLeast(0)); val close = e.text.indexOf('}', open.coerceAtLeast(0))
        if (open in 0 until cut && close >= cut) cut = close + 1
        val left = e.text.substring(0, cut).trimEnd().removeSuffix("\\N").trimEnd()
        val right = e.text.substring(cut).trimStart().removePrefix("\\N").trimStart()
        if (visibleLen(left) == 0 || visibleLen(right) == 0) return listOf(e)
        val a = visibleLen(left).toDouble(); val b = visibleLen(right).toDouble()
        val mid = (e.startMs + e.durationMs * (a / (a + b))).toLong().coerceIn(e.startMs + 1, (e.endMs - 1).coerceAtLeast(e.startMs + 1))
        return listOf(e.copy(endMs = mid, text = left), e.copy(startMs = mid, text = right))
    }

    /** One line spanning all of [lines] (earliest start, latest end), texts joined by a line break. */
    fun join(lines: List<AssEvent>): AssEvent {
        val first = lines.first()
        return first.copy(
            startMs = lines.minOf { it.startMs }, endMs = lines.maxOf { it.endMs },
            text = lines.joinToString("\\N") { it.text.trim() }.trim(),
        )
    }

    /** Moves line [i] by [d] (-1 up, +1 down); returns the new list or null at the edge. */
    fun move(list: List<AssEvent>, i: Int, d: Int): List<AssEvent>? {
        val j = i + d
        if (i !in list.indices || j !in list.indices) return null
        return list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    enum class SortBy { START, END, STYLE, ACTOR }

    fun sort(list: List<AssEvent>, by: SortBy): List<AssEvent> = when (by) {
        SortBy.START -> list.sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.layer }))
        SortBy.END -> list.sortedWith(compareBy({ it.endMs }, { it.startMs }))
        SortBy.STYLE -> list.sortedWith(compareBy({ it.style.lowercase() }, { it.startMs }))
        SortBy.ACTOR -> list.sortedWith(compareBy({ it.actor.lowercase() }, { it.startMs }))
    }
}

enum class SearchField { TEXT, STYLE, ACTOR, EFFECT }

data class SearchSpec(
    val query: String = "", val regex: Boolean = false, val matchCase: Boolean = false, val field: SearchField = SearchField.TEXT,
)

/** A18.d: find / replace over one field of the lines. */
object Find {
    /** The pattern, or null for an empty query. Throws [PatternSyntaxException] for a bad regex. */
    fun compile(s: SearchSpec): Regex? {
        if (s.query.isEmpty()) return null
        val opts = if (s.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(if (s.regex) s.query else Regex.escape(s.query), opts)
    }

    private fun get(e: AssEvent, f: SearchField) = when (f) {
        SearchField.TEXT -> e.text; SearchField.STYLE -> e.style; SearchField.ACTOR -> e.actor; SearchField.EFFECT -> e.effect
    }

    private fun put(e: AssEvent, f: SearchField, v: String) = when (f) {
        SearchField.TEXT -> e.copy(text = v); SearchField.STYLE -> e.copy(style = v)
        SearchField.ACTOR -> e.copy(actor = v); SearchField.EFFECT -> e.copy(effect = v)
    }

    /** Indices of the lines whose field matches. Empty for a bad regex or empty query. */
    fun matches(file: SubFile, s: SearchSpec): List<Int> {
        val r = try { compile(s) } catch (_: PatternSyntaxException) { null } ?: return emptyList()
        return file.events.indices.filter { r.containsMatchIn(get(file.events[it], s.field)) }
    }

    /** Next matching line after [from] in direction [dir] (+1/-1), wrapping around; -1 when nothing matches. */
    fun next(file: SubFile, s: SearchSpec, from: Int, dir: Int): Int {
        val m = matches(file, s)
        if (m.isEmpty()) return -1
        return if (dir >= 0) (m.firstOrNull { it > from } ?: m.first()) else (m.lastOrNull { it < from } ?: m.last())
    }

    fun error(s: SearchSpec): Boolean = try { compile(s); false } catch (_: PatternSyntaxException) { true }

    /** Replaces in one line; null when it did not change (or the replacement is invalid). */
    fun replaceLine(e: AssEvent, s: SearchSpec, repl: String): AssEvent? {
        val r = try { compile(s) } catch (_: PatternSyntaxException) { null } ?: return null
        val old = get(e, s.field)
        val new = try { r.replace(old, if (s.regex) repl else Regex.escapeReplacement(repl)) } catch (_: Exception) { return null }
        return if (new == old) null else put(e, s.field, new)
    }

    /** Replaces in every line; returns the new file and the number of changed lines. */
    fun replaceAll(file: SubFile, s: SearchSpec, repl: String): Pair<SubFile, Int> {
        var n = 0
        val list = file.events.map { e -> replaceLine(e, s, repl)?.also { n++ } ?: e }
        return file.copy(events = list) to n
    }
}
