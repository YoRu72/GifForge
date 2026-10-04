package com.mediaforge.app.ui

import com.mediaforge.app.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.mediaforge.app.media.FolderIndex
import com.mediaforge.app.media.LibraryFilter
import com.mediaforge.app.media.LibraryItem
import com.mediaforge.app.media.SortField
import com.mediaforge.app.media.applyFilters
import com.mediaforge.app.media.fmtDate
import com.mediaforge.app.media.folderTitle
import com.mediaforge.app.media.hasMediaAccess
import com.mediaforge.app.media.pickerMillisToLocalSec
import com.mediaforge.app.media.prettyPath
import com.mediaforge.app.media.requiredMediaPermissions
import com.mediaforge.app.media.rootLabel
import com.mediaforge.app.media.rootOf
import com.mediaforge.app.media.startOfDaySec
import com.mediaforge.app.media.startOfYearSec

private const val MB = 1_000_000L

private data class FolderEntry(val path: String, val name: String, val count: Int, val sizeBytes: Long)

@Composable
private fun rememberThumbLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember {
        ImageLoader.Builder(ctx)
            .components { add(VideoFrameDecoder.Factory()) } // video thumbnails; GIFs load as a still first frame
            .memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.25).build() }
            .crossfade(false)
            .build()
    }
}

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun inside(parent: String, folder: String) = parent == folder || parent.startsWith("$folder/")

private fun highlight(text: String, tokens: List<String>, color: Color): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    if (lower.length != text.length) return AnnotatedString(text)
    val marks = BooleanArray(text.length)
    for (t in tokens) {
        var i = lower.indexOf(t)
        while (i >= 0) {
            for (k in i until i + t.length) marks[k] = true
            i = lower.indexOf(t, i + t.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val m = marks[i]
            var j = i
            while (j < text.length && marks[j] == m) j++
            if (m) withStyle(SpanStyle(background = color.copy(alpha = 0.35f), fontWeight = FontWeight.Bold)) {
                append(text.substring(i, j))
            } else append(text.substring(i, j))
            i = j
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScreen(
    onVideo: (Uri) -> Unit,
    onGif: (Uri) -> Unit,
    onBack: () -> Unit,
    vm: MediaViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.load(force = true)
    }
    LaunchedEffect(granted) { if (granted) vm.load() }

    var sizeDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val filter = vm.filter
    val loader = rememberThumbLoader()
    val filtered = remember(vm.items, filter) { applyFilters(vm.items, filter) }
    val browsing = vm.tab == 1 && filter.query.isBlank()
    val index = remember(filtered, browsing) { if (browsing) FolderIndex(filtered) else null }
    val singleRoot = index?.roots?.singleOrNull()
    val cur = vm.path ?: singleRoot
    val atTop = cur == null || (singleRoot != null && cur == singleRoot)

    fun goBack() {
        when {
            vm.searching -> { vm.searching = false; vm.filter = filter.copy(query = "") }
            vm.tab == 1 && cur != null && !atTop ->
                vm.path = if (rootOf(cur) == cur) null else cur.substringBeforeLast('/')
            else -> onBack()
        }
    }
    BackHandler { goBack() }

    val pick: (LibraryItem) -> Unit = { if (it.isVideo) onVideo(it.uri) else onGif(it.uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.tab == 1 && cur != null) folderTitle(cur) else stringResource(R.string.mb_title)) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.mb_back)) }
                },
                actions = {
                    IconButton(onClick = {
                        if (vm.searching) { vm.searching = false; vm.filter = filter.copy(query = "") }
                        else vm.searching = true
                    }) { Icon(Icons.Filled.Search, stringResource(R.string.mb_search)) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.mb_sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortField.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text((if (filter.sort == f) "\u2713 " else "    ") + sortName(f)) },
                                    onClick = {
                                        vm.filter = filter.copy(sort = f, ascending = f == SortField.NAME)
                                        sortMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text((if (filter.ascending) "\u2713 " else "    ") + stringResource(R.string.mb_asc)) },
                                onClick = { vm.filter = filter.copy(ascending = true); sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text((if (!filter.ascending) "\u2713 " else "    ") + stringResource(R.string.mb_desc)) },
                                onClick = { vm.filter = filter.copy(ascending = false); sortMenu = false },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.mb_more)) }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (vm.gridView) stringResource(R.string.mb_list_view) else stringResource(R.string.mb_grid_view)) },
                                onClick = { vm.gridView = !vm.gridView; moreMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mb_rescan)) },
                                onClick = { vm.load(force = true); moreMenu = false },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!granted) {
                PermissionPrompt { permLauncher.launch(requiredMediaPermissions()) }
                return@Column
            }
            if (vm.searching) {
                val scopeName = if (vm.tab == 1 && cur != null && !vm.everywhere) folderTitle(cur) else null
                SearchField(
                    value = filter.query,
                    placeholder = if (scopeName != null) stringResource(R.string.mb_search_in, scopeName) else stringResource(R.string.mb_search_all),
                    onChange = { vm.filter = filter.copy(query = it) },
                    onClose = { vm.searching = false; vm.filter = filter.copy(query = "") },
                )
            }
            FilterBar(
                filter = filter,
                onChange = { vm.filter = it },
                onCustomSize = { sizeDialog = true },
                onCustomDate = { dateDialog = true },
            )
            if (vm.searching && vm.tab == 1 && cur != null) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = !vm.everywhere, onClick = { vm.everywhere = false }, label = { Text(stringResource(R.string.mb_this_folder)) })
                    FilterChip(selected = vm.everywhere, onClick = { vm.everywhere = true }, label = { Text(stringResource(R.string.mb_everywhere)) })
                }
            }
            TabRow(selectedTabIndex = vm.tab) {
                Tab(selected = vm.tab == 0, onClick = { vm.tab = 0 }, text = { Text(stringResource(R.string.mb_tab_all)) })
                Tab(selected = vm.tab == 1, onClick = { vm.tab = 1 }, text = { Text(stringResource(R.string.mb_tab_folders)) })
            }
            if (browsing && cur != null) {
                Breadcrumbs(cur) { vm.path = it }
            }

            val tokens = filter.tokens
            when {
                vm.loading && vm.items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.items.isEmpty() -> Empty(stringResource(R.string.mb_none))
                filtered.isEmpty() -> Empty(stringResource(R.string.mb_no_match)) {
                    vm.filter = LibraryFilter(sort = filter.sort, ascending = filter.ascending)
                }
                vm.tab == 0 -> ExplorerList(
                    folders = emptyList(), list = filtered, grid = vm.gridView, showPath = true,
                    tokens = tokens, loader = loader, header = summary(filtered), onFolder = {}, onItem = pick,
                )
                !browsing -> {
                    val scoped = if (vm.everywhere || cur == null) filtered
                    else filtered.filter { inside(it.parentPath, cur) }
                    if (scoped.isEmpty()) Empty(stringResource(R.string.mb_no_in, folderTitle(cur ?: "")))
                    else ExplorerList(
                        folders = emptyList(), list = scoped, grid = vm.gridView, showPath = true,
                        tokens = tokens, loader = loader, header = summary(scoped), onFolder = {}, onItem = pick,
                    )
                }
                else -> {
                    val idx = index!!
                    if (cur == null) {
                        ExplorerList(
                            folders = idx.roots.map { FolderEntry(it, rootLabel(it), idx.count(it), idx.size(it)) },
                            list = emptyList(), grid = vm.gridView, showPath = false, tokens = tokens,
                            loader = loader, header = null, onFolder = { vm.path = it.path }, onItem = pick,
                        )
                    } else if (idx.count(cur) == 0) {
                        Empty(stringResource(R.string.mb_no_folder_match))
                    } else {
                        ExplorerList(
                            folders = idx.children(cur).map {
                                FolderEntry(it, it.substringAfterLast('/'), idx.count(it), idx.size(it))
                            },
                            list = idx.items(cur), grid = vm.gridView, showPath = false, tokens = tokens,
                            loader = loader, header = null, onFolder = { vm.path = it.path }, onItem = pick,
                        )
                    }
                }
            }
        }
    }

    if (sizeDialog) {
        SizeDialog(
            onDismiss = { sizeDialog = false },
            onApply = { min, max, label ->
                vm.filter = vm.filter.copy(minSize = min, maxSize = max, sizeLabel = label)
                sizeDialog = false
            },
        )
    }
    if (dateDialog) {
        DateRangeDialog(
            onDismiss = { dateDialog = false },
            onApply = { from, to, label ->
                vm.filter = vm.filter.copy(dateFromSec = from, dateToSec = to, dateLabel = label)
                dateDialog = false
            },
        )
    }
}

private fun summary(list: List<LibraryItem>) =
    "${list.size} items - ${formatSize(list.sumOf { it.sizeBytes })}"

// ---------------------------------------------------------------- filters

@Composable
private fun FilterBar(
    filter: LibraryFilter,
    onChange: (LibraryFilter) -> Unit,
    onCustomSize: () -> Unit,
    onCustomDate: () -> Unit,
) {
    val ctx = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuChip(
            if (filter.type == "All") stringResource(R.string.mb_type) else typeName(filter.type), filter.type != "All",
            listOf("All", "Videos", "GIFs").map { t -> typeName(t) to { onChange(filter.copy(type = t)) } },
        )
        MenuChip(
            filter.sizeLabel?.let { stringResource(R.string.mb_size_l, it) } ?: stringResource(R.string.mb_size), filter.sizeLabel != null,
            listOf(
                stringResource(R.string.mb_any_size) to { onChange(filter.copy(minSize = null, maxSize = null, sizeLabel = null)) },
                stringResource(R.string.mb_u1mb) to { onChange(filter.copy(minSize = null, maxSize = MB, sizeLabel = "< 1 MB")) },
                "1 - 10 MB" to { onChange(filter.copy(minSize = MB, maxSize = 10 * MB, sizeLabel = "1 - 10 MB")) },
                "10 - 100 MB" to { onChange(filter.copy(minSize = 10 * MB, maxSize = 100 * MB, sizeLabel = "10 - 100 MB")) },
                stringResource(R.string.mb_o100mb) to { onChange(filter.copy(minSize = 100 * MB, maxSize = null, sizeLabel = "> 100 MB")) },
                stringResource(R.string.mb_custom) to onCustomSize,
            ),
        )
        MenuChip(
            filter.dateLabel?.let { stringResource(R.string.mb_date_l, it) } ?: stringResource(R.string.mb_date), filter.dateLabel != null,
            listOf(
                stringResource(R.string.mb_any_date) to { onChange(filter.copy(dateFromSec = null, dateToSec = null, dateLabel = null)) },
                stringResource(R.string.mb_today) to { onChange(filter.copy(dateFromSec = startOfDaySec(0), dateToSec = null, dateLabel = ctx.getString(R.string.mb_today))) },
                stringResource(R.string.mb_7d) to { onChange(filter.copy(dateFromSec = startOfDaySec(6), dateToSec = null, dateLabel = ctx.getString(R.string.mb_7d_s))) },
                stringResource(R.string.mb_30d) to { onChange(filter.copy(dateFromSec = startOfDaySec(29), dateToSec = null, dateLabel = ctx.getString(R.string.mb_30d_s))) },
                stringResource(R.string.mb_year) to { onChange(filter.copy(dateFromSec = startOfYearSec(), dateToSec = null, dateLabel = ctx.getString(R.string.mb_year))) },
                stringResource(R.string.mb_old) to {
                    onChange(filter.copy(dateFromSec = null, dateToSec = startOfDaySec(365), dateLabel = ctx.getString(R.string.mb_old_s)))
                },
                stringResource(R.string.mb_custom) to onCustomDate,
            ),
        )
        MenuChip(
            filter.resLabel?.let { stringResource(R.string.mb_res_l, it) } ?: stringResource(R.string.mb_res), filter.resLabel != null,
            listOf(
                stringResource(R.string.mb_any_res) to { onChange(filter.copy(minLongSide = 0, resLabel = null)) },
                stringResource(R.string.mb_720) to { onChange(filter.copy(minLongSide = 1280, resLabel = "720p+")) },
                stringResource(R.string.mb_1080) to { onChange(filter.copy(minLongSide = 1920, resLabel = "1080p+")) },
                stringResource(R.string.mb_2k) to { onChange(filter.copy(minLongSide = 2560, resLabel = "2K+")) },
                stringResource(R.string.mb_4k) to { onChange(filter.copy(minLongSide = 3840, resLabel = "4K+")) },
            ),
        )
        MenuChip(
            filter.durLabel?.let { stringResource(R.string.mb_len_l, it) } ?: stringResource(R.string.mb_len), filter.durLabel != null,
            listOf(
                stringResource(R.string.mb_any_len) to { onChange(filter.copy(minDurMs = null, maxDurMs = null, durLabel = null)) },
                stringResource(R.string.mb_u5s) to { onChange(filter.copy(minDurMs = null, maxDurMs = 5_000, durLabel = ctx.getString(R.string.mb_u5s_s))) },
                "5 - 15 s" to { onChange(filter.copy(minDurMs = 5_000, maxDurMs = 15_000, durLabel = "5 - 15 s")) },
                "15 - 60 s" to { onChange(filter.copy(minDurMs = 15_000, maxDurMs = 60_000, durLabel = "15 - 60 s")) },
                stringResource(R.string.mb_o1m) to { onChange(filter.copy(minDurMs = 60_000, maxDurMs = null, durLabel = ctx.getString(R.string.mb_o1m_s))) },
            ),
        )
        if (filter.hasActiveFilters) {
            TextButton(onClick = { onChange(filter.cleared()) }) { Text(stringResource(R.string.mb_clear)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuChip(label: String, active: Boolean, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeDialog(onDismiss: () -> Unit, onApply: (Long?, Long?, String) -> Unit) {
    var minT by remember { mutableStateOf("") }
    var maxT by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("MB") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mb_custom_size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        minT, { minT = it }, label = { Text(stringResource(R.string.mb_min)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        maxT, { maxT = it }, label = { Text(stringResource(R.string.mb_max)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KB", "MB", "GB").forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val mult = when (unit) { "KB" -> 1_000.0; "GB" -> 1_000_000_000.0; else -> 1_000_000.0 }
                var a = minT.replace(',', '.').toDoubleOrNull()?.let { (it * mult).toLong() }
                var b = maxT.replace(',', '.').toDoubleOrNull()?.let { (it * mult).toLong() }
                if (a != null && b != null && a > b) { val t = a; a = b; b = t }
                val shown = { v: Long -> "%.4g".format(v / mult).trimEnd('0').trimEnd('.') }
                when {
                    a == null && b == null -> onDismiss()
                    a != null && b != null -> onApply(a, b, "${shown(a)} - ${shown(b)} $unit")
                    a != null -> onApply(a, null, "> ${shown(a)} $unit")
                    else -> onApply(null, b, "< ${shown(b!!)} $unit")
                }
            }) { Text(stringResource(R.string.mb_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(onDismiss: () -> Unit, onApply: (Long, Long, String) -> Unit) {
    val st = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = st.selectedStartDateMillis != null,
                onClick = {
                    val s = st.selectedStartDateMillis ?: return@TextButton
                    val e = st.selectedEndDateMillis ?: s
                    val from = pickerMillisToLocalSec(s)
                    val to = pickerMillisToLocalSec(e, plusDays = 1)
                    onApply(from, to, if (e == s) fmtDate(from) else "${fmtDate(from)} - ${fmtDate(to - 1)}")
                },
            ) { Text(stringResource(R.string.mb_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(state = st, modifier = Modifier.height(480.dp))
    }
}

// ---------------------------------------------------------------- search + navigation

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            IconButton(onClick = { if (value.isNotEmpty()) onChange("") else onClose() }) {
                Icon(Icons.Filled.Close, stringResource(R.string.mb_clear))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
    )
}

@Composable
private fun Breadcrumbs(path: String, onGo: (String) -> Unit) {
    val root = rootOf(path)
    val crumbs = buildList {
        add(root to rootLabel(root))
        var acc = root
        path.removePrefix(root).split('/').filter { it.isNotEmpty() }.forEach { seg ->
            acc = "$acc/$seg"
            add(acc to seg)
        }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { i, (p, name) ->
            if (i > 0) Text("\u203A", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onGo(p) }) {
                Text(name, fontWeight = if (i == crumbs.lastIndex) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Empty(text: String, onClear: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text)
        if (onClear != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClear) { Text(stringResource(R.string.mb_clear_all)) }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.mb_perm))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.mb_allow)) }
    }
}

// ---------------------------------------------------------------- lists

@Composable
private fun ExplorerList(
    folders: List<FolderEntry>,
    list: List<LibraryItem>,
    grid: Boolean,
    showPath: Boolean,
    tokens: List<String>,
    loader: ImageLoader,
    header: String?,
    onFolder: (FolderEntry) -> Unit,
    onItem: (LibraryItem) -> Unit,
) {
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(header, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(4.dp))
                }
            }
            items(folders, key = { "f:" + it.path }, span = { GridItemSpan(maxLineSpan) }) { f ->
                FolderRow(f) { onFolder(f) }
            }
            items(list, key = { it.uri.toString() }, contentType = { it.isVideo }) { item ->
                MediaCell(item, loader) { onItem(item) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            if (header != null) {
                item {
                    Text(
                        header,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            items(folders, key = { "f:" + it.path }) { f -> FolderRow(f) { onFolder(f) } }
            items(list, key = { it.uri.toString() }, contentType = { it.isVideo }) { item ->
                MediaRow(item, loader, tokens, showPath) { onItem(item) }
            }
        }
    }
}

@Composable
private fun FolderRow(f: FolderEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Folder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.mb_items, f.count) + " - " + formatSize(f.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Thumb(item: LibraryItem, loader: ImageLoader, modifier: Modifier) {
    val ctx = LocalContext.current
    val req = remember(item.uri) { ImageRequest.Builder(ctx).data(item.uri).size(300).build() }
    AsyncImage(
        model = req,
        imageLoader = loader,
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun MediaRow(item: LibraryItem, loader: ImageLoader, tokens: List<String>, showPath: Boolean, onClick: () -> Unit) {
    val hl = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Thumb(item, loader, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(item.name, tokens, hl),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPath) {
                Text(
                    prettyPath(item.parentPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                formatSize(item.sizeBytes),
                fmtDate(item.dateSec),
                if (item.width > 0 && item.height > 0) "${item.width}x${item.height}" else null,
                if (item.isVideo) fmtDuration(item.durationMs) else "GIF",
            ).joinToString(" - ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaCell(item: LibraryItem, loader: ImageLoader, onClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Thumb(item, loader, Modifier.fillMaxSize())
        Badge(if (item.isVideo) fmtDuration(item.durationMs) else "GIF", Modifier.align(Alignment.BottomStart))
        Badge(formatSize(item.sizeBytes), Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(4.dp)
            .background(Color(0x99000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun typeName(t: String): String = stringResource(when (t) { "Videos" -> R.string.mb_videos; "GIFs" -> R.string.mb_gifs; else -> R.string.mb_all })

@Composable
private fun sortName(f: com.mediaforge.app.media.SortField): String = stringResource(
    when (f) {
        com.mediaforge.app.media.SortField.NAME -> R.string.sort_name
        com.mediaforge.app.media.SortField.DATE -> R.string.sort_date
        com.mediaforge.app.media.SortField.SIZE -> R.string.sort_size
        com.mediaforge.app.media.SortField.DURATION -> R.string.sort_dur
        com.mediaforge.app.media.SortField.RESOLUTION -> R.string.sort_res
    },
)
