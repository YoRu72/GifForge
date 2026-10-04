package com.mediaforge.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import androidx.compose.ui.unit.sp
import com.mediaforge.app.media.FontCatalog
import com.mediaforge.app.media.FontFace
import com.mediaforge.app.media.FontLoad
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.media.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_SAMPLE = "12345abcd"

private sealed interface FontItem {
    data class Header(val family: String, val count: Int) : FontItem
    data class Row(val face: FontFace) : FontItem
}

private fun buildItems(faces: List<FontFace>, q: String): List<FontItem> {
    val filtered = if (q.isEmpty()) faces
    else faces.filter { (it.family + " " + it.style + " " + it.file.name).lowercase().contains(q) }
    return filtered.groupBy { it.family.lowercase() }.toSortedMap().flatMap { (_, group) ->
        val sorted = group.sortedWith(compareBy<FontFace>({ it.weight }, { it.italic }))
        listOf<FontItem>(FontItem.Header(sorted.first().family, sorted.size)) + sorted.map { FontItem.Row(it) }
    }
}

/**
 * Font directory, grouped by family. With [onPick] set it works as a font chooser.
 * The "Aa" button turns on a bar where you can type your own preview text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontScreen(onBack: () -> Unit, onPick: ((String?) -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mine by remember { mutableStateOf(FontStore.list(ctx)) }
    val system = remember { FontStore.systemFonts() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var customOn by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FontFace?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }

    // metadata is read off the main thread and cached
    val mineFaces by produceState<List<FontFace>?>(null, mine) { value = withContext(Dispatchers.IO) { FontCatalog.scan(mine) } }
    val systemFaces by produceState<List<FontFace>?>(null) { value = withContext(Dispatchers.IO) { FontCatalog.scan(system) } }

    LaunchedEffect(message) {
        if (message != null) { delay(7000); message = null }
    }

    fun report(r: ImportResult) {
        mine = FontStore.list(ctx)
        busy = false
        val n = r.imported.size
        var m = ctx.resources.getQuantityString(R.plurals.fonts_imported, n, n)
        if (r.failed > 0) m += "\n" + ctx.getString(R.string.fonts_import_failed, r.failed)
        if (r.unsupported.isNotEmpty()) m += "\n" + ctx.getString(R.string.fonts_import_unsupported, r.unsupported.joinToString(", "))
        message = m
    }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) {
                uris.map { FontStore.importUri(ctx, it) }.fold(ImportResult.EMPTY) { a, b -> a + b }
            }
            report(r)
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            report(FontStore.importTree(ctx, uri))
        }
    }

    val faces = if (tab == 0) mineFaces else systemFaces
    val q = query.trim().lowercase()
    val listItems = remember(faces, q) { faces?.let { buildItems(it, q) } ?: emptyList() }
    val sample = if (customOn && customText.isNotBlank()) customText else com.mediaforge.app.Prefs.fontSample.value.ifBlank { DEFAULT_SAMPLE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (onPick != null) R.string.fonts_choose else R.string.fonts_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { customOn = !customOn }) {
                        Icon(
                            Icons.Filled.TextFields, stringResource(R.string.fonts_custom_preview),
                            tint = if (customOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    Box {
                        IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.fonts_add)) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fonts_import_files)) },
                                onClick = { addMenu = false; filesLauncher.launch(arrayOf("*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fonts_import_folder)) },
                                onClick = { addMenu = false; treeLauncher.launch(null) },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary)
            }
            if (customOn) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { if (it.length <= 40) customText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.fonts_type_preview)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.fonts_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.fonts_tab_mine, mineFaces?.size?.toString() ?: "...")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.fonts_tab_system, systemFaces?.size?.toString() ?: "...")) })
            }
            when {
                faces == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                listItems.isEmpty() && (onPick == null || tab == 1 || q.isNotEmpty()) ->
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                if (q.isNotEmpty()) R.string.fonts_none_match
                                else if (tab == 0) R.string.fonts_none_mine
                                else R.string.fonts_none_system,
                            ),
                        )
                    }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (onPick != null && q.isEmpty()) {
                        item(key = "default") {
                            Column(
                                Modifier.fillMaxWidth().clickable { onPick(null) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(stringResource(R.string.fonts_default), style = MaterialTheme.typography.labelMedium)
                                Text(sample, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    items(
                        listItems,
                        key = { if (it is FontItem.Header) "h:" + it.family.lowercase() else "f:" + (it as FontItem.Row).face.ref },
                    ) { item ->
                        when (item) {
                            is FontItem.Header -> Text(
                                "${item.family}  (${item.count})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 2.dp),
                            )
                            is FontItem.Row -> FontFaceRow(
                                face = item.face,
                                sample = sample,
                                canDelete = tab == 0,
                                picking = onPick != null,
                                retryTick = retryTick,
                                onClick = { onPick?.invoke(item.face.ref) },
                                onDelete = { toDelete = item.face },
                                onRetry = { FontStore.unblock(item.face.ref); retryTick++ },
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.fonts_delete_title)) },
            text = {
                Text(f.file.name + if (f.faceCount > 1) " " + stringResource(R.string.fonts_collection_of, f.faceCount) else "")
            },
            confirmButton = {
                TextButton(onClick = { FontStore.delete(f.file); mine = FontStore.list(ctx); toDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FontFaceRow(
    face: FontFace,
    sample: String,
    canDelete: Boolean,
    picking: Boolean,
    retryTick: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    // Loaded lazily, one at a time, off the main thread; rows scrolled off-screen cost nothing.
    val load by produceState<FontLoad>(FontLoad.Loading, face.ref, retryTick) { value = FontStore.load(face.ref) }
    val size = remember(face.file) { face.file.length() }
    // Arabic, symbol and icon fonts lack Latin letters: preview them with glyphs they really have
    val shown by produceState(sample, face.ref, sample) { value = withContext(Dispatchers.IO) { FontCatalog.previewText(face, sample) } }
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = picking && load is FontLoad.Ready, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                face.style,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (val l = load) {
                is FontLoad.Ready -> Text(
                    shown, fontFamily = FontFamily(l.typeface), fontSize = 26.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                FontLoad.Loading -> Text("...", fontSize = 26.sp, color = MaterialTheme.colorScheme.outline)
                FontLoad.Failed -> Text(stringResource(R.string.fonts_cant_load), color = MaterialTheme.colorScheme.error)
                FontLoad.Blocked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fonts_skipped), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.fonts_retry)) }
                }
            }
            val meta = buildList {
                add(if (face.cff) "OTF" else "TTF")
                add(formatSize(size))
                if (face.isVariable) add(stringResource(R.string.fonts_variable) + " (" + face.axes.joinToString(", ") { it.tag } + ")")
                if (face.color) add(stringResource(R.string.fonts_color))
                if (face.faceCount > 1) add(stringResource(R.string.fonts_collection, face.ttcIndex + 1, face.faceCount))
            }.joinToString(" - ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canDelete) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}
