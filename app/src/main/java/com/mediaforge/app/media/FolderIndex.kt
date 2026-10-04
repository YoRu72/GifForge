package com.mediaforge.app.media

/** Storage root of a path: /storage/emulated/0, /storage/<sd-id>, or the first segment. */
fun rootOf(path: String): String {
    val seg = path.split('/').filter { it.isNotEmpty() }
    return when {
        seg.size >= 3 && seg[0] == "storage" && seg[1] == "emulated" -> "/" + seg.take(3).joinToString("/")
        seg.size >= 2 && seg[0] == "storage" -> "/" + seg.take(2).joinToString("/")
        seg.isNotEmpty() -> "/" + seg[0]
        else -> "/"
    }
}

fun rootLabel(root: String): String = when {
    root.startsWith("/storage/emulated") -> "Internal storage"
    root.startsWith("/storage/") -> "SD card (${root.substringAfterLast('/')})"
    else -> root.trimStart('/').ifEmpty { "Other" }
}

fun folderTitle(path: String): String =
    if (rootOf(path) == path) rootLabel(path) else path.substringAfterLast('/')

/** Path shown to the user: storage root replaced by its friendly name. */
fun prettyPath(path: String): String {
    val r = rootOf(path)
    return rootLabel(r) + path.removePrefix(r)
}

/** Real folder tree built once from a (filtered) list of files, with recursive counts and sizes. */
class FolderIndex(items: List<LibraryItem>) {
    private val direct = HashMap<String, MutableList<LibraryItem>>()
    private val kids = HashMap<String, MutableSet<String>>()
    private val counts = HashMap<String, Int>()
    private val sizes = HashMap<String, Long>()
    private val rootSet = LinkedHashSet<String>()

    init {
        for (item in items) {
            val p = item.parentPath
            direct.getOrPut(p) { ArrayList() }.add(item)
            var cur = p
            while (true) {
                counts[cur] = (counts[cur] ?: 0) + 1
                sizes[cur] = (sizes[cur] ?: 0L) + item.sizeBytes
                if (rootOf(cur) == cur || !cur.contains('/')) {
                    rootSet.add(cur)
                    break
                }
                val parent = cur.substringBeforeLast('/')
                kids.getOrPut(parent) { LinkedHashSet() }.add(cur)
                cur = parent
            }
        }
    }

    val roots: List<String> get() = rootSet.sortedBy { rootLabel(it).lowercase() }
    fun children(path: String): List<String> = kids[path]?.sortedBy { it.substringAfterLast('/').lowercase() } ?: emptyList()
    fun items(path: String): List<LibraryItem> = direct[path] ?: emptyList()
    fun count(path: String): Int = counts[path] ?: 0
    fun size(path: String): Long = sizes[path] ?: 0L
}
