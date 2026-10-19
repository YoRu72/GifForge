package com.mediaforge.app.subs

/**
 * 19.a / AEGX8: style operations for the styles manager and the translator's base style.
 * The base style is the look every new or imported (non-ASS) script starts with, so the translator chooses the
 * font, size, outline and shadow once instead of tagging every line.
 */
object StyleOps {
    /** Base style when the owner has not chosen one: clean outline, no drop shadow (Aegisub's default shadow 2 looked heavy). */
    val FACTORY = AssStyle(
        name = "Default", fontName = "Arial", fontSize = 52f, outlineWidth = 2f, shadow = 0f,
        back = 0x80000000.toInt(), marginL = 30, marginR = 30, marginV = 30,
    )

    /** The base style stored as one `Style:` line ("" = [FACTORY]); always named Default. */
    fun base(saved: String): AssStyle =
        (saved.takeIf { it.isNotBlank() }?.let { AssFormat.styleFromLine(it) } ?: FACTORY).copy(name = "Default")

    fun save(st: AssStyle): String = AssFormat.styleToLine(st.copy(name = "Default"))

    /** Sizes, outline, shadow and margins times [k] (a style moved between scripts of different PlayResY). */
    fun rescale(st: AssStyle, k: Float): AssStyle = if (k == 1f) st else st.copy(
        fontSize = Math.round(st.fontSize * k).toFloat(), outlineWidth = Math.round(st.outlineWidth * k * 2f) / 2f,
        shadow = Math.round(st.shadow * k * 2f) / 2f, marginL = Math.round(st.marginL * k), marginR = Math.round(st.marginR * k),
        marginV = Math.round(st.marginV * k),
    )

    /** A script from a format without styles (SRT, VTT, ...) or a new one: its Default style becomes [base]. */
    fun withBase(f: SubFile, base: AssStyle): SubFile {
        val others = f.styles.filter { it.name != "Default" }
        return f.copy(styles = listOf(base.copy(name = "Default")) + others)
    }

    /** Replaces style [old] (by name) with [st]; lines using the old name follow a rename. */
    fun update(f: SubFile, old: String, st: AssStyle): SubFile {
        val styles = f.styles.map { if (it.name == old) st else it }
        val events = if (old != st.name) f.events.map { if (it.style == old) it.copy(style = st.name) else it } else f.events
        return f.copy(styles = styles, events = events)
    }

    /** A free name based on [base] ("Sign", "Sign 2", ...). */
    fun freeName(f: SubFile, base: String): String {
        if (f.styles.none { it.name == base }) return base
        var i = 2
        while (f.styles.any { it.name == "$base $i" }) i++
        return "$base $i"
    }

    fun add(f: SubFile, st: AssStyle): SubFile = f.copy(styles = f.styles + st.copy(name = freeName(f, st.name)))

    /** Deletes style [name]; its lines move to the first remaining style. The last style is never deleted. */
    fun delete(f: SubFile, name: String): SubFile {
        if (f.styles.size <= 1) return f
        val rest = f.styles.filter { it.name != name }
        val to = rest.first().name
        return f.copy(styles = rest, events = f.events.map { if (it.style == name) it.copy(style = to) else it })
    }

    /** Every line (or the lines in [only]) uses style [name]. */
    fun assign(f: SubFile, name: String, only: Set<Int>? = null): SubFile =
        f.copy(events = f.events.mapIndexed { i, e -> if (only == null || i in only) e.copy(style = name) else e })

    private val PER_LINE_LOOK = listOf("fn", "fs", "b", "i", "c", "3c", "bord", "shad")

    /**
     * Removes per-line font, size, bold, italic, colour, outline and shadow tags, so the style decides again.
     * Effect keyframes (MF1 block), positions and animations stay. Returns the file and the number of lines changed.
     */
    fun clearLineLooks(f: SubFile): Pair<SubFile, Int> {
        var n = 0
        val ev = f.events.map { e ->
            val t = PER_LINE_LOOK.fold(e.text) { acc, tag -> AssLook.setTag(acc, tag, null) }
            if (t != e.text) { n++; e.copy(text = t) } else e
        }
        return f.copy(events = ev) to n
    }

    /** Style names used by lines but missing from the style list (Aegisub shows them in red). */
    fun missing(f: SubFile): Set<String> = f.events.map { it.style }.toSet() - f.styles.map { it.name }.toSet()

    /** How many lines use each style. */
    fun usage(f: SubFile): Map<String, Int> = f.events.groupingBy { it.style }.eachCount()
}
