package com.mediaforge.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mediaforge.app.subs.Find
import com.mediaforge.app.subs.History
import com.mediaforge.app.subs.LineOps
import com.mediaforge.app.subs.SearchSpec
import com.mediaforge.app.media.Autosave
import com.mediaforge.app.Prefs
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.Effects
import com.mediaforge.app.subs.formatAssTime
import com.mediaforge.app.subs.parseAssTime

/**
 * Aegisub-style subtitle screen. Top: a plain grid (#, start, end, style, text), one compact row per line.
 * Bottom, docked: the active line's text, then two collapsible sections - its Settings and its Effects
 * (keyframes, fades, blur...). Times are always left to right, even in Arabic screens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubtitleListScreen(doc: SubDoc, onBack: () -> Unit, videoUri: android.net.Uri? = null) {
    val ctx = LocalContext.current
    var video by remember { mutableStateOf(videoUri) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) video = u }
    var cd by remember { mutableStateOf(doc) }
    val scope0 = androidx.compose.runtime.rememberCoroutineScope()
    var file by remember { mutableStateOf(doc.file) }
    var active by rememberSaveable { mutableStateOf(if (doc.file.events.isEmpty()) -1 else 0) }
    var marked by remember { mutableStateOf(setOf<Int>()) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val events = file.events

    // A18.c undo/redo: every change of `file` is recorded; undo/redo set `file` back (the echo is ignored by History)
    val hist = remember { History(doc.file) }
    LaunchedEffect(file) { hist.record(file) }
    fun fixAfterJump() { marked = emptySet(); active = active.coerceIn(if (file.events.isEmpty()) -1 else 0, file.events.size - 1); dirty = true }
    fun undo() { hist.undo()?.let { file = it; fixAfterJump() } }
    fun redo() { hist.redo()?.let { file = it; fixAfterJump() } }

    // A18.f autosave a moment after the last change; offer recovery when a newer unsaved copy exists
    LaunchedEffect(file) { if (dirty && Prefs.subAutosave.value) { kotlinx.coroutines.delay(2500); withContext(Dispatchers.IO) { Autosave.write(ctx, cd.name, file) } } }
    var recovered by remember { mutableStateOf<com.mediaforge.app.subs.SubFile?>(null) }
    LaunchedEffect(Unit) { if (Prefs.subAutosave.value) recovered = withContext(Dispatchers.IO) { Autosave.recover(ctx, cd.name, doc.file) } }

    // A18.d find / replace
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var spec by remember { mutableStateOf(SearchSpec()) }
    var replacement by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val cur = remember { IntArray(1) } // caret position in the active line's text (for Split)

    fun edit(i: Int, f: (AssEvent) -> AssEvent) {
        file = file.copy(events = file.events.mapIndexed { k, e -> if (k == i) f(e) else e }); dirty = true
    }
    fun targets(): Set<Int> = if (marked.isNotEmpty()) marked else if (active in events.indices) setOf(active) else emptySet()
    /** Look changes go to every marked line (or the active one), so one tap can restyle many lines. */
    fun editTargets(f: (AssEvent) -> AssEvent) {
        val t = targets()
        file = file.copy(events = file.events.mapIndexed { k, ev -> if (k in t) f(ev) else ev }); dirty = true
    }

    fun styleName() = file.styles.firstOrNull()?.name ?: "Default"
    /** Adds a line after the active one (or at [startAt]) and selects it: the "+" of the empty state and edit header. */
    fun addLine(startAt: Long? = null) {
        val at = (active + 1).coerceIn(0, events.size)
        val s0 = startAt ?: (events.getOrNull(at - 1)?.endMs ?: 0L)
        file = file.copy(events = events.take(at) + AssEvent(startMs = s0, endMs = s0 + 2000L, style = styleName()) + events.drop(at))
        active = at; marked = emptySet(); dirty = true
    }
    /** Replaces the lines with another subtitle (imported file or extracted track); undo brings the old ones back. */
    fun adopt(d: SubDoc?) {
        if (d == null) { Toast.makeText(ctx, R.string.sub_unreadable, Toast.LENGTH_LONG).show(); return }
        cd = d; file = d.file; active = if (d.file.events.isEmpty()) -1 else 0; marked = emptySet(); dirty = true
        Toast.makeText(ctx, ctx.getString(R.string.sub_imported, d.name), Toast.LENGTH_SHORT).show()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope0.launch { adopt(withContext(Dispatchers.IO) { readSubDoc(ctx, u) }) }
    }
    var tracksOpen by remember { mutableStateOf(false) }
    var shiftOpen by remember { mutableStateOf(false) }
    var fixOpen by remember { mutableStateOf(false) }
    var syncOpen by remember { mutableStateOf(false) }
    if (syncOpen) SyncDialog(events, active, { syncOpen = false }) { a, ta, b, tb ->
        val r = com.mediaforge.app.subs.TimeOps.linearSync(file.events, a, ta, b, tb)
        syncOpen = false
        if (r == null) Toast.makeText(ctx, R.string.sy_failed, Toast.LENGTH_LONG).show()
        else { file = file.copy(events = r); dirty = true; Toast.makeText(ctx, R.string.sh_done, Toast.LENGTH_SHORT).show() }
    }
    if (fixOpen) FixDialog(events, { fixOpen = false }) { rules ->
        val out = com.mediaforge.app.subs.FixOps.applyAll(file.events, rules)
        file = file.copy(events = out); active = active.coerceIn(if (out.isEmpty()) -1 else 0, out.size - 1); marked = emptySet(); dirty = true; fixOpen = false
        Toast.makeText(ctx, R.string.fx_done, Toast.LENGTH_SHORT).show()
    }
    fun scopeSet(s: ShiftScope): Set<Int> = when (s) {
        ShiftScope.ALL -> events.indices.toSet()
        ShiftScope.FROM_ACTIVE -> events.indices.filter { it >= active }.toSet()
        ShiftScope.SELECTED -> marked
    }
    if (shiftOpen) ShiftDialog(
        hasSelection = marked.isNotEmpty(), hasActive = active in events.indices, onDismiss = { shiftOpen = false },
        onShift = { s, d -> file = file.copy(events = com.mediaforge.app.subs.TimeOps.shift(file.events, scopeSet(s), d)); dirty = true; shiftOpen = false; Toast.makeText(ctx, R.string.sh_done, Toast.LENGTH_SHORT).show() },
        onRescale = { s, a, b -> file = file.copy(events = com.mediaforge.app.subs.TimeOps.rescale(file.events, scopeSet(s), a, b)); dirty = true; shiftOpen = false; Toast.makeText(ctx, R.string.sh_done, Toast.LENGTH_SHORT).show() },
    )
    if (tracksOpen && video != null) SubSourceDialog(
        video = video!!, onEmpty = null, onDismiss = { tracksOpen = false },
        onImport = { tracksOpen = false; importer.launch(arrayOf("*/*")) },
        onTrack = { f, _ -> tracksOpen = false; scope0.launch { adopt(withContext(Dispatchers.IO) { readSubDoc(ctx, android.net.Uri.fromFile(f)) }) } },
    )
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { u ->
        if (u != null) {
            val ok = writeSubDoc(ctx, u, cd, file)
            if (ok) { dirty = false; Autosave.clear(ctx, cd.name) }
            Toast.makeText(ctx, if (ok) R.string.sub_saved else R.string.sub_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    var translate by rememberSaveable { mutableStateOf(false) }
    var originals by remember { mutableStateOf<List<String>>(emptyList()) }
    val trOn = translate && originals.size == events.size
    fun nextUntranslated(from: Int): Int {
        if (!trOn) return -1
        val n = events.size
        for (k in 1..n) { val j = (from + k) % n; if (events[j].text == originals[j] || events[j].text.isBlank()) return j }
        return -1
    }
    var chooser by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingVideo by remember { mutableStateOf<Pair<ExportMode, VideoExportSettings>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val videoSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { u ->
        val pv = pendingVideo; val src = video
        pendingVideo = null
        if (u != null && pv != null && src != null) scope.launch {
            exporting = true
            val r = com.mediaforge.app.media.VideoExporter.export(ctx, src, file, pv.first, pv.second)
            val copied = r.ok && r.file != null && withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(u, "wt")?.use { o -> r.file.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
            }
            r.file?.delete()
            exporting = false
            if (copied) Toast.makeText(ctx, ctx.getString(R.string.ex_done) + (if (r.note.isNotEmpty()) " (" + r.note + ")" else ""), Toast.LENGTH_LONG).show()
            else exportError = if (!com.mediaforge.app.media.VideoExporter.available()) ctx.getString(R.string.ex_engine_missing) else r.log
        }
    }
    var pendingExport by remember { mutableStateOf<Pair<com.mediaforge.app.subs.SubFormat, EncChoice>?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { u ->
        val pe = pendingExport
        if (u != null && pe != null) {
            val ok = try {
                val bytes = com.mediaforge.app.subs.Encodings.encode(pe.first.write(file), pe.second.charset, pe.second.bom)
                ctx.contentResolver.openOutputStream(u, "wt")?.use { it.write(bytes) } != null
            } catch (e: Exception) { false }
            Toast.makeText(ctx, if (ok) R.string.sub_saved else R.string.sub_save_failed, Toast.LENGTH_SHORT).show()
        }
        pendingExport = null
    }
    if (chooser) ExportChooser(
        hasVideo = video != null, engineReady = com.mediaforge.app.media.VideoExporter.available(), defaultFormatId = cd.format.id, onDismiss = { chooser = false },
        onExportFile = { id, enc ->
            val f = com.mediaforge.app.subs.SubFormats.byId(id) ?: cd.format
            pendingExport = f to enc; chooser = false
            exporter.launch(cd.name.substringBeforeLast('.') + "." + (f.extensions.firstOrNull() ?: "txt"))
        },
        onExportVideo = { m, st ->
            pendingVideo = m to st; chooser = false
            videoSaver.launch(cd.name.substringBeforeLast('.') + (if (m == ExportMode.HARD) "_hard." else "_soft.") + st.container)
        },
    )
    if (exporting) androidx.compose.material3.AlertDialog(
        onDismissRequest = {}, title = { Text(stringResource(R.string.ex_exporting)) },
        text = { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { com.mediaforge.app.media.VideoExporter.cancel() }) { Text(stringResource(R.string.cancel)) } },
    )
    exportError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportError = null }, title = { Text(stringResource(R.string.ex_failed)) },
            text = { Text(msg, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { exportError = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
    recovered?.let { rf ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recovered = null }, title = { Text(stringResource(R.string.rc_title)) },
            text = { Text(stringResource(R.string.rc_text)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { file = rf; dirty = true; active = if (rf.events.isEmpty()) -1 else 0; recovered = null }) { Text(stringResource(R.string.rc_restore)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { Autosave.clear(ctx, cd.name); recovered = null }) { Text(stringResource(R.string.rc_discard)) } },
        )
    }
    LaunchedEffect(active) { if (active in events.indices) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cd.name + if (dirty) " *" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            ctx.resources.getQuantityString(R.plurals.sub_lines, events.size, events.size) + " - " + (cd.format.extensions.firstOrNull() ?: cd.format.id).uppercase(),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(enabled = hist.canUndo, onClick = { undo() }) { Icon(Icons.Filled.Undo, stringResource(R.string.undo)) }
                    IconButton(enabled = hist.canRedo, onClick = { redo() }) { Icon(Icons.Filled.Redo, stringResource(R.string.redo)) }
                    IconButton(onClick = { findOpen = !findOpen }) {
                        Icon(Icons.Filled.Search, stringResource(R.string.fd_title), tint = if (findOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = { saver.launch(cd.name) }) { Icon(Icons.Filled.Save, stringResource(R.string.save)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            val t = targets()
                            val ed = !translate
                            @Composable fun item(label: Int, enabled: Boolean = true, run: () -> Unit) =
                                DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled, onClick = { menuOpen = false; run() })
                            fun put(list: List<AssEvent>, newActive: Int) { file = file.copy(events = list); active = newActive; marked = emptySet(); dirty = true }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tr_mode)) },
                                leadingIcon = { Icon(Icons.Filled.Translate, null, tint = if (translate) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                onClick = { menuOpen = false; translate = !translate; if (translate) originals = file.events.map { it.text } },
                            )
                            item(R.string.sub_add, ed) { addLine() }
                            item(R.string.sub_insert, ed) {
                                val at = (active + 1).coerceIn(0, events.size)
                                val prevEnd = events.getOrNull(at - 1)?.endMs ?: 0L
                                put(events.take(at) + AssEvent(startMs = prevEnd, endMs = prevEnd + 2000L, style = file.styles.firstOrNull()?.name ?: "Default") + events.drop(at), at)
                            }
                            item(R.string.sub_insert_before, ed) {
                                val at = active.coerceIn(0, events.size)
                                val nextStart = events.getOrNull(at)?.startMs ?: 0L
                                val s0 = (nextStart - 2000L).coerceAtLeast(0L)
                                put(events.take(at) + AssEvent(startMs = s0, endMs = maxOf(nextStart, s0 + 1L), style = file.styles.firstOrNull()?.name ?: "Default") + events.drop(at), at)
                            }
                            item(R.string.sub_duplicate, ed && t.isNotEmpty()) {
                                val dup = events.filterIndexed { i, _ -> i in t }
                                val at = t.max() + 1
                                put(events.take(at) + dup + events.drop(at), at)
                            }
                            item(R.string.delete, ed && t.isNotEmpty()) {
                                val left = events.filterIndexed { i, _ -> i !in t }
                                put(left, if (left.isEmpty()) -1 else t.min().coerceAtMost(left.size - 1))
                            }
                            HorizontalDivider()
                            item(R.string.ln_split, ed && active in events.indices) {
                                val parts = LineOps.split(events[active], cur[0])
                                if (parts.size == 2) put(events.take(active) + parts + events.drop(active + 1), active + 1)
                                else Toast.makeText(ctx, R.string.ln_split_fail, Toast.LENGTH_SHORT).show()
                            }
                            item(R.string.ln_join, ed && marked.size >= 2) {
                                val sel = marked.sorted(); val first = sel.first(); val rest = sel.drop(1).toSet()
                                val joined = LineOps.join(sel.map { events[it] })
                                put(events.mapIndexed { i, e -> if (i == first) joined else e }.filterIndexed { i, _ -> i !in rest }, first)
                            }
                            item(R.string.ln_up, ed && active > 0) { LineOps.move(events, active, -1)?.let { put(it, active - 1) } }
                            item(R.string.ln_down, ed && active in 0 until events.size - 1) { LineOps.move(events, active, 1)?.let { put(it, active + 1) } }
                            HorizontalDivider()
                            item(R.string.sh_menu, events.isNotEmpty()) { shiftOpen = true }
                            item(R.string.sy_menu, events.size > 1) { syncOpen = true }
                            item(R.string.fx_menu, ed && events.isNotEmpty()) { fixOpen = true }
                            item(R.string.ln_sort_start, ed && events.size > 1) { put(LineOps.sort(events, LineOps.SortBy.START), 0) }
                            item(R.string.ln_sort_end, ed && events.size > 1) { put(LineOps.sort(events, LineOps.SortBy.END), 0) }
                            item(R.string.ln_sort_style, ed && events.size > 1) { put(LineOps.sort(events, LineOps.SortBy.STYLE), 0) }
                            HorizontalDivider()
                            item(R.string.sub_import) { importer.launch(arrayOf("*/*")) }
                            if (video != null) item(R.string.sub_tracks) { tracksOpen = true }
                            item(if (video == null) R.string.sv_attach else R.string.sv_detach) { if (video == null) videoPicker.launch(arrayOf("video/*")) else video = null }
                            item(R.string.ex_title) { chooser = true }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // ---------------------------------------------------------------- video + timing (A13)
            video?.let { v ->
                SubVideoPane(
                    uri = v, file = file, events = events, active = active, onActive = { active = it },
                    onSetStart = { t -> if (active in events.indices) edit(active) { it.copy(startMs = t, endMs = maxOf(it.endMs, t + 100L)) } else addLine(t) },
                    onSetEnd = { t -> if (active in events.indices) edit(active) { it.copy(endMs = maxOf(t, it.startMs + 1L)) } },
                    onTapChain = { t ->
                        if (active !in events.indices) addLine(t)
                        else {
                            edit(active) { it.copy(endMs = maxOf(t, it.startMs + 1L)) }
                            val next = active + 1
                            if (next < file.events.size) {
                                edit(next) { it.copy(startMs = t, endMs = maxOf(it.endMs, t + 100L)) }; active = next
                            } else {
                                val n = AssEvent(startMs = t, endMs = t + 2000L, style = file.styles.firstOrNull()?.name ?: "Default")
                                file = file.copy(events = file.events + n); active = file.events.size - 1; dirty = true
                            }
                        }
                    },
                    onNudge = { ds, de ->
                        if (active in events.indices) edit(active) {
                            val s0 = (it.startMs + ds).coerceAtLeast(0L)
                            it.copy(startMs = s0, endMs = maxOf(s0 + 1L, it.endMs + de))
                        }
                    },
                )
                HorizontalDivider()
            }
            if (findOpen) {
                FindBar(
                    file, active, spec, replacement, { spec = it }, { replacement = it },
                    onGo = { d -> val j = Find.next(file, spec, active, d); if (j >= 0) active = j },
                    onReplaceOne = {
                        val e0 = events.getOrNull(active)
                        val r = e0?.let { Find.replaceLine(it, spec, replacement) }
                        if (r != null) { edit(active) { r }; val j = Find.next(file, spec, active, 1); if (j >= 0) active = j }
                    },
                    onReplaceAll = {
                        val (f2, n) = Find.replaceAll(file, spec, replacement)
                        if (n > 0) { file = f2; dirty = true }
                        Toast.makeText(ctx, ctx.getString(R.string.fd_replaced, n), Toast.LENGTH_SHORT).show()
                    },
                    onClose = { findOpen = false },
                )
                HorizontalDivider()
            }
            // ---------------------------------------------------------------- grid
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val showStyle = maxWidth >= 480.dp
                Column {
                    GridHeader(showStyle)
                    if (events.isEmpty()) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sub_empty))
                        androidx.compose.material3.Button(onClick = { addLine() }) { Icon(Icons.Filled.Add, null); Text(stringResource(R.string.sub_add), Modifier.padding(start = 8.dp)) }
                        androidx.compose.material3.OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.sub_import)) }
                        if (video != null) androidx.compose.material3.OutlinedButton(onClick = { tracksOpen = true }) { Text(stringResource(R.string.sub_tracks)) }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        itemsIndexed(events) { i, e ->
                            GridRow(
                                i, e, showStyle, isActive = i == active, translated = if (trOn) (e.text != originals[i] && e.text.isNotBlank()) else null, isMarked = i in marked, marking = marked.isNotEmpty(),
                                onClick = { if (marked.isNotEmpty()) marked = if (i in marked) marked - i else marked + i else active = i },
                                onLong = { marked = if (i in marked) marked - i else marked + i },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            // ---------------------------------------------------------------- docked edit area
            val e = events.getOrNull(active)
            if (e == null) {
                Text(stringResource(R.string.sub_no_active), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                var settingsOpen by rememberSaveable(active) { mutableStateOf(false) }
                var effectsOpen by rememberSaveable(active) { mutableStateOf(false) }
                var lookOpen by rememberSaveable(active) { mutableStateOf(false) }
                Column(Modifier.weight(if (video != null) 0.9f else 1.15f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sub_line_n, active + 1, events.size), style = MaterialTheme.typography.titleSmall)
                            val cps = com.mediaforge.app.subs.ReadOps.cps(e)
                            val tooFast = cps > com.mediaforge.app.subs.ReadOps.max()
                            Text(
                                stringResource(R.string.cps_label, Math.round(cps).toInt()) + if (tooFast) " - " + stringResource(R.string.cps_high) else "",
                                style = MaterialTheme.typography.labelSmall, color = if (tooFast) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            )
                        }
                        if (!translate) IconButton(onClick = { addLine() }) { Icon(Icons.Filled.Add, stringResource(R.string.sub_add)) }
                        IconButton(enabled = active > 0, onClick = { active -= 1 }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.sub_prev)) }
                        IconButton(enabled = active < events.size - 1, onClick = { active += 1 }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.sub_next)) }
                    }
                    if (trOn) {
                        val orig = originals[active]
                        Text(stringResource(R.string.tr_original), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                orig, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            )
                        }
                        val done = events.indices.count { events[it].text != originals[it] && events[it].text.isNotBlank() }
                        Text(stringResource(R.string.tr_progress, done, events.size), style = MaterialTheme.typography.labelSmall)
                    }
                    var tf by remember(active) { mutableStateOf(TextFieldValue(e.text, TextRange(e.text.length))) }
                    if (tf.text != e.text) tf = TextFieldValue(e.text, TextRange(minOf(tf.selection.end, e.text.length)))
                    cur[0] = tf.selection.end
                    OutlinedTextField(
                        value = tf, onValueChange = { v -> tf = v; cur[0] = v.selection.end; if (v.text != e.text) edit(active) { it.copy(text = v.text) } },
                        label = { Text(stringResource(if (trOn) R.string.tr_translation else R.string.sub_text)) }, modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    )
                    if (trOn) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.AssistChip(onClick = { edit(active) { it.copy(text = originals[active]) } }, label = { Text(stringResource(R.string.tr_copy_original)) })
                        androidx.compose.material3.AssistChip(onClick = { edit(active) { it.copy(text = "") } }, label = { Text(stringResource(R.string.tr_clear)) })
                        androidx.compose.material3.AssistChip(onClick = { val j = nextUntranslated(active); if (j >= 0) active = j else Toast.makeText(ctx, R.string.tr_all_done, Toast.LENGTH_SHORT).show() }, label = { Text(stringResource(R.string.tr_next_untranslated)) })
                    }
                    SectionHeader(stringResource(R.string.sub_settings), settingsOpen) { settingsOpen = !settingsOpen }
                    AnimatedVisibility(settingsOpen) { SettingsFields(e) { f -> edit(active, f) } }
                    SectionHeader(stringResource(R.string.sub_look), lookOpen) { lookOpen = !lookOpen }
                    AnimatedVisibility(lookOpen) { LookPanel(file, e) { f -> editTargets(f) } }
                    val n = effectCount(e)
                    SectionHeader(if (n > 0) stringResource(R.string.sub_effects_count, n) else stringResource(R.string.sub_effects), effectsOpen) { effectsOpen = !effectsOpen }
                    AnimatedVisibility(effectsOpen) { EffectsPanel(e, file) { f -> edit(active, f) } }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SectionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
}

@Composable
private fun GridHeader(showStyle: Boolean) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp)) {
        val s = MaterialTheme.typography.labelSmall
        Text("#", Modifier.width(30.dp), style = s)
        ForceLtr {
            Text(stringResource(R.string.sub_start), Modifier.width(66.dp), style = s)
            Text(stringResource(R.string.sub_end), Modifier.width(66.dp), style = s)
        }
        if (showStyle) Text(stringResource(R.string.sub_style), Modifier.width(80.dp), style = s)
        Text(stringResource(R.string.sub_text), Modifier.weight(1f), style = s)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridRow(
    i: Int, e: AssEvent, showStyle: Boolean, isActive: Boolean, translated: Boolean?, isMarked: Boolean, marking: Boolean,
    onClick: () -> Unit, onLong: () -> Unit,
) {
    val bg = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isMarked -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val fast = !e.comment && com.mediaforge.app.subs.ReadOps.cps(e) > com.mediaforge.app.subs.ReadOps.max()
    val dim = if (e.comment) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else if (fast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().background(bg).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mono = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontStyle = if (e.comment) FontStyle.Italic else FontStyle.Normal)
        if (marking) Checkbox(isMarked, { onLong() }, Modifier.width(30.dp)) else Text("${i + 1}", Modifier.width(30.dp), style = mono, color = if (translated == true) MaterialTheme.colorScheme.primary else if (translated == false) MaterialTheme.colorScheme.error else dim)
        ForceLtr {
            Text(formatAssTime(e.startMs), Modifier.width(66.dp), style = mono, color = dim, maxLines = 1)
            Text(formatAssTime(e.endMs), Modifier.width(66.dp), style = mono, color = dim, maxLines = 1)
        }
        if (showStyle) Text(e.style, Modifier.width(80.dp), style = mono, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            plain(e.text), Modifier.weight(1f).clipToBounds(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = dim,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content, fontStyle = if (e.comment) FontStyle.Italic else FontStyle.Normal),
        )
    }
}

/** The collapsible per-line settings: times, style, actor, effect field, layer, margins, comment. */
@Composable
private fun SettingsFields(e: AssEvent, onChange: ((AssEvent) -> AssEvent) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField(stringResource(R.string.sub_start), e.startMs, Modifier.weight(1f)) { t -> onChange { it.copy(startMs = t) } }
            TimeField(stringResource(R.string.sub_end), e.endMs, Modifier.weight(1f)) { t -> onChange { it.copy(endMs = t) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField1(stringResource(R.string.sub_style), e.style, Modifier.weight(1f)) { v -> onChange { it.copy(style = v) } }
            TextField1(stringResource(R.string.sub_actor), e.actor, Modifier.weight(1f)) { v -> onChange { it.copy(actor = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField1(stringResource(R.string.sub_effect), e.effect, Modifier.weight(1f)) { v -> onChange { it.copy(effect = v) } }
            NumField(stringResource(R.string.sub_layer), e.layer, Modifier.weight(1f)) { v -> onChange { it.copy(layer = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(stringResource(R.string.sub_margin_l), e.marginL, Modifier.weight(1f)) { v -> onChange { it.copy(marginL = v) } }
            NumField(stringResource(R.string.sub_margin_r), e.marginR, Modifier.weight(1f)) { v -> onChange { it.copy(marginR = v) } }
            NumField(stringResource(R.string.sub_margin_v), e.marginV, Modifier.weight(1f)) { v -> onChange { it.copy(marginV = v) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = e.comment, onCheckedChange = { c -> onChange { it.copy(comment = c) } })
            Text(stringResource(R.string.sub_comment))
        }
    }
}

/** Text without override blocks; \N becomes a space so a grid row shows one readable line. */
private fun plain(s: String): String = s.replace(Regex("\\{[^}]*\\}"), "").replace("\\N", " ").replace("\\n", " ").replace("\\h", " ")

@Composable
private fun TextField1(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
private fun NumField(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    var t by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (t.toIntOrNull() != value) t = value.toString() }
    OutlinedTextField(
        value = t, onValueChange = { s -> t = s; s.toIntOrNull()?.let(onValue) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
    )
}

/** Edits `H:MM:SS.cc`; the value is committed only when it parses, so typing never jumps. */
@Composable
private fun TimeField(label: String, ms: Long, modifier: Modifier, onValue: (Long) -> Unit) {
    var t by remember { mutableStateOf(formatAssTime(ms)) }
    LaunchedEffect(ms) { if (parseAssTime(t)?.let { formatAssTime(it) } != formatAssTime(ms)) t = formatAssTime(ms) }
    ForceLtr {
        OutlinedTextField(
            value = t, onValueChange = { s -> t = s; parseAssTime(s)?.let(onValue) },
            label = { Text(label) }, singleLine = true, modifier = modifier,
        )
    }
}
