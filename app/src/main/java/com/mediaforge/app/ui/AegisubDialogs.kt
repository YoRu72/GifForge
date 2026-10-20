package com.mediaforge.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.media.Keyframes
import com.mediaforge.app.subs.Resample
import com.mediaforge.app.subs.SelectLines
import com.mediaforge.app.subs.SubFile
import com.mediaforge.app.subs.TimingPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun CheckRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onToggle)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Small number field in ms (digits only; times stay left to right on Arabic screens). */
@Composable
private fun MsBox(label: String, value: Long, modifier: Modifier = Modifier, onValue: (Long) -> Unit) {
    var t by remember(value) { mutableStateOf(value.toString()) }
    ForceLtr {
        OutlinedTextField(
            t, { s -> t = westernDigits(s).filter { it in '0'..'9' }.take(6); t.toLongOrNull()?.let(onValue) }, modifier, singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ---------------------------------------------------------------- Timing Post-Processor (23.b)

/**
 * Aegisub's Timing Post-Processor: lead-in/out, make adjacent lines continuous, snap to video keyframes. The style list
 * limits it to dialogue styles (signs keep their exact times). Keyframes come from the attached video.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimingPostDialog(file: SubFile, marked: Set<Int>, video: Uri?, onDismiss: () -> Unit, onApply: (List<com.mediaforge.app.subs.AssEvent>) -> Unit) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(TimingPost.Settings()) }
    var styles by remember { mutableStateOf(file.styles.map { it.name }.toSet()) }
    var onlyMarked by rememberSaveable { mutableStateOf(marked.isNotEmpty()) }
    val keyframes by produceState<List<Long>?>(null, video) {
        value = if (video == null) emptyList() else withContext(Dispatchers.IO) { Keyframes.read(ctx, video) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tpp_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tpp_styles), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    file.styles.forEach { st -> FilterChip(st.name in styles, { styles = if (st.name in styles) styles - st.name else styles + st.name }, { Text(st.name) }) }
                }
                if (marked.isNotEmpty()) CheckRow(stringResource(R.string.tpp_only_marked), onlyMarked) { onlyMarked = it }
                CheckRow(stringResource(R.string.tpp_lead_in), s.leadInOn) { s = s.copy(leadInOn = it) }
                CheckRow(stringResource(R.string.tpp_lead_out), s.leadOutOn) { s = s.copy(leadOutOn = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MsBox(stringResource(R.string.tpp_in_ms), s.leadIn, Modifier.weight(1f)) { s = s.copy(leadIn = it) }
                    MsBox(stringResource(R.string.tpp_out_ms), s.leadOut, Modifier.weight(1f)) { s = s.copy(leadOut = it) }
                }
                CheckRow(stringResource(R.string.tpp_adjacent), s.adjacentOn) { s = s.copy(adjacentOn = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MsBox(stringResource(R.string.tpp_gap), s.adjacentGap, Modifier.weight(1f)) { s = s.copy(adjacentGap = it) }
                    MsBox(stringResource(R.string.tpp_overlap), s.adjacentOverlap, Modifier.weight(1f)) { s = s.copy(adjacentOverlap = it) }
                }
                LabeledSlider(stringResource(R.string.tpp_bias), "${(s.bias * 100).toInt()}%", s.bias.toFloat(), 0f..1f, 0) { v -> s = s.copy(bias = (v * 100).toInt() / 100.0) }
                val kf = keyframes
                CheckRow(stringResource(R.string.tpp_keyframes), s.keyframesOn && !kf.isNullOrEmpty()) { s = s.copy(keyframesOn = it) }
                Text(
                    when {
                        video == null -> stringResource(R.string.tpp_kf_no_video)
                        kf == null -> stringResource(R.string.tpp_kf_loading)
                        else -> stringResource(R.string.tpp_kf_count, kf.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!kf.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MsBox(stringResource(R.string.tpp_start_before), s.keyStartBefore, Modifier.weight(1f)) { s = s.copy(keyStartBefore = it) }
                        MsBox(stringResource(R.string.tpp_start_after), s.keyStartAfter, Modifier.weight(1f)) { s = s.copy(keyStartAfter = it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MsBox(stringResource(R.string.tpp_end_before), s.keyEndBefore, Modifier.weight(1f)) { s = s.copy(keyEndBefore = it) }
                        MsBox(stringResource(R.string.tpp_end_after), s.keyEndAfter, Modifier.weight(1f)) { s = s.copy(keyEndAfter = it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val which = file.events.indices.filter { file.events[it].style in styles && (!onlyMarked || it in marked) }.toSet()
                onApply(TimingPost.process(file.events, which, s, keyframes.orEmpty()))
            }) { Text(stringResource(R.string.sh_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Select Lines (AEGX6)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectLinesDialog(file: SubFile, marked: Set<Int>, onDismiss: () -> Unit, onSelect: (Set<Int>) -> Unit) {
    var spec by remember { mutableStateOf(SelectLines.Spec()) }
    var action by remember { mutableStateOf(SelectLines.Action.SET) }
    val hits = SelectLines.matches(file.events, spec)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sel_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(spec.query, { spec = spec.copy(query = it) }, Modifier.fillMaxWidth(), singleLine = true, isError = hits == null,
                    label = { Text(stringResource(R.string.sel_match)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SelectLines.Field.TEXT to R.string.sub_text, SelectLines.Field.STYLE to R.string.sub_style, SelectLines.Field.ACTOR to R.string.sub_actor, SelectLines.Field.EFFECT to R.string.sub_effect)
                        .forEach { (f, l) -> FilterChip(spec.field == f, { spec = spec.copy(field = f) }, { Text(stringResource(l)) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SelectLines.Mode.CONTAINS to R.string.sel_contains, SelectLines.Mode.EXACT to R.string.sel_exact, SelectLines.Mode.REGEX to R.string.fd_regex)
                        .forEach { (m, l) -> FilterChip(spec.mode == m, { spec = spec.copy(mode = m) }, { Text(stringResource(l)) }) }
                }
                CheckRow(stringResource(R.string.fd_case), spec.matchCase) { spec = spec.copy(matchCase = it) }
                CheckRow(stringResource(R.string.sel_invert), spec.invert) { spec = spec.copy(invert = it) }
                CheckRow(stringResource(R.string.sel_dialogue), spec.dialogue) { spec = spec.copy(dialogue = it) }
                CheckRow(stringResource(R.string.sel_comments), spec.comments) { spec = spec.copy(comments = it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(SelectLines.Action.SET to R.string.sel_set, SelectLines.Action.ADD to R.string.sel_add, SelectLines.Action.SUBTRACT to R.string.sel_sub, SelectLines.Action.INTERSECT to R.string.sel_intersect)
                        .forEach { (a, l) -> FilterChip(action == a, { action = a }, { Text(stringResource(l)) }) }
                }
                Text(
                    if (hits == null) stringResource(R.string.fd_bad_regex) else stringResource(R.string.sel_count, hits.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hits == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = { TextButton(enabled = hits != null, onClick = { hits?.let { onSelect(SelectLines.combine(marked, it, action)) } }) { Text(stringResource(R.string.sh_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Script properties (19.b) + resampler (23.d)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertiesDialog(file: SubFile, videoW: Int, videoH: Int, onDismiss: () -> Unit, onApply: (SubFile) -> Unit) {
    var title by remember { mutableStateOf(file.info("Title").orEmpty()) }
    var original by remember { mutableStateOf(file.info("Original Script").orEmpty()) }
    var translation by remember { mutableStateOf(file.info("Original Translation").orEmpty()) }
    var resX by remember { mutableStateOf(file.playResX.toString()) }
    var resY by remember { mutableStateOf(file.playResY.toString()) }
    var wrap by remember { mutableStateOf(file.wrapStyle) }
    var scaled by remember { mutableStateOf(file.scaledBorderAndShadow) }
    var matrix by remember { mutableStateOf(file.info("YCbCr Matrix") ?: "None") }
    var rescale by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pr_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pr_name)) })
                OutlinedTextField(original, { original = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pr_original)) })
                OutlinedTextField(translation, { translation = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.pr_translator)) })
                Text(stringResource(R.string.pr_res), style = MaterialTheme.typography.labelLarge)
                ForceLtr {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(resX, { resX = westernDigits(it).filter { c -> c in '0'..'9' }.take(5) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("×")
                        OutlinedTextField(resY, { resY = westernDigits(it).filter { c -> c in '0'..'9' }.take(5) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                if (videoW > 0 && videoH > 0) TextButton(onClick = { resX = videoW.toString(); resY = videoH.toString() }) { Text(stringResource(R.string.pr_from_video, videoW, videoH)) }
                CheckRow(stringResource(R.string.pr_rescale), rescale) { rescale = it }
                Text(stringResource(R.string.pr_wrap), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to R.string.pr_wrap0, 1 to R.string.pr_wrap1, 2 to R.string.pr_wrap2, 3 to R.string.pr_wrap3)
                        .forEach { (v, l) -> FilterChip(wrap == v, { wrap = v }, { Text(stringResource(l)) }) }
                }
                CheckRow(stringResource(R.string.pr_scaled), scaled) { scaled = it }
                Text("YCbCr Matrix", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "TV.601", "TV.709", "PC.601", "PC.709").forEach { m -> FilterChip(matrix == m, { matrix = m }, { Text(m) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val x = resX.toIntOrNull()?.coerceIn(16, 16384) ?: file.playResX
                val y = resY.toIntOrNull()?.coerceIn(16, 16384) ?: file.playResY
                var f = if (rescale) Resample.resample(file, x, y) else file.withInfo("PlayResX", x.toString()).withInfo("PlayResY", y.toString())
                f = f.withInfo("WrapStyle", wrap.toString()).withInfo("ScaledBorderAndShadow", if (scaled) "yes" else "no").withInfo("YCbCr Matrix", matrix)
                if (title.isNotBlank() || file.info("Title") != null) f = f.withInfo("Title", title)
                if (original.isNotBlank() || file.info("Original Script") != null) f = f.withInfo("Original Script", original)
                if (translation.isNotBlank() || file.info("Original Translation") != null) f = f.withInfo("Original Translation", translation)
                onApply(f)
            }) { Text(stringResource(R.string.sh_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Paste over (AEGX6)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasteOverDialog(onDismiss: () -> Unit, onApply: (Set<com.mediaforge.app.subs.PasteOver.Field>) -> Unit) {
    var fields by remember { mutableStateOf(setOf(com.mediaforge.app.subs.PasteOver.Field.TEXT)) }
    val labels = listOf(
        com.mediaforge.app.subs.PasteOver.Field.TEXT to R.string.sub_text, com.mediaforge.app.subs.PasteOver.Field.START to R.string.sub_start,
        com.mediaforge.app.subs.PasteOver.Field.END to R.string.sub_end, com.mediaforge.app.subs.PasteOver.Field.STYLE to R.string.sub_style,
        com.mediaforge.app.subs.PasteOver.Field.ACTOR to R.string.sub_actor, com.mediaforge.app.subs.PasteOver.Field.EFFECT to R.string.sub_effect,
        com.mediaforge.app.subs.PasteOver.Field.LAYER to R.string.sub_layer, com.mediaforge.app.subs.PasteOver.Field.MARGINS to R.string.po_margins,
        com.mediaforge.app.subs.PasteOver.Field.COMMENT to R.string.sub_comment,
    )
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ln_paste_over)) },
        text = {
            Column {
                Text(stringResource(R.string.po_note), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { (f, l) -> FilterChip(f in fields, { fields = if (f in fields) fields - f else fields + f }, { Text(stringResource(l)) }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = fields.isNotEmpty(), onClick = { onApply(fields) }) { Text(stringResource(R.string.sh_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- QC list (23.c)

private fun qcLabel(k: com.mediaforge.app.subs.Qc.Kind): Int = when (k) {
    com.mediaforge.app.subs.Qc.Kind.OVERLAP -> R.string.qc_overlap
    com.mediaforge.app.subs.Qc.Kind.SHORT_GAP -> R.string.qc_gap
    com.mediaforge.app.subs.Qc.Kind.TOO_FAST -> R.string.qc_fast
    com.mediaforge.app.subs.Qc.Kind.TOO_LONG_LINE -> R.string.qc_long_line
    com.mediaforge.app.subs.Qc.Kind.TOO_MANY_LINES -> R.string.qc_many_lines
    com.mediaforge.app.subs.Qc.Kind.SHORT -> R.string.qc_short
    com.mediaforge.app.subs.Qc.Kind.LONG -> R.string.qc_long
    com.mediaforge.app.subs.Qc.Kind.EMPTY -> R.string.qc_empty
    com.mediaforge.app.subs.Qc.Kind.MISSING_STYLE -> R.string.qc_style
}

/** Reports only; tapping an issue jumps to its line. Limits follow the reading-speed profile. */
@Composable
fun QcDialog(file: SubFile, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    val issues = remember(file) { com.mediaforge.app.subs.Qc.run(file, com.mediaforge.app.subs.Qc.Limits(maxCps = com.mediaforge.app.subs.ReadOps.max())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qc_title)) },
        text = {
            if (issues.isEmpty()) Text(stringResource(R.string.qc_none))
            else LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(issues) { i ->
                    Column(Modifier.fillMaxWidth().clickable { onJump(i.line) }.padding(vertical = 6.dp)) {
                        Text(stringResource(R.string.fx_line, i.line + 1) + " - " + stringResource(qcLabel(i.kind)) + (if (i.value.isNotEmpty()) " (" + i.value + ")" else ""), style = MaterialTheme.typography.bodyMedium)
                        Text(com.mediaforge.app.subs.AssLook.plainText(file.events[i.line].text).replace('\n', ' '), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

// ---------------------------------------------------------------- Styling assistant (23.e / AEGX8)

/** Aegisub's styling assistant: the lines one by one, a style per tap; the line advances by itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylingAssistantDialog(file: SubFile, start: Int, onDismiss: () -> Unit, onStyle: (line: Int, style: String) -> Unit) {
    var i by remember { mutableStateOf(start.coerceIn(0, (file.events.size - 1).coerceAtLeast(0))) }
    val e = file.events.getOrNull(i)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sa_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e == null) Text(stringResource(R.string.sub_empty)) else {
                    Text(stringResource(R.string.sub_line_n, i + 1, file.events.size), style = MaterialTheme.typography.labelMedium)
                    Text(com.mediaforge.app.subs.AssLook.plainText(e.text), style = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                    Text(stringResource(R.string.sa_current, e.style), style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        file.styles.forEach { st ->
                            FilterChip(st.name == e.style, { onStyle(i, st.name); if (i + 1 < file.events.size) i++ }, { Text(st.name) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = i > 0, onClick = { i-- }) { Text(stringResource(R.string.sub_prev)) }
                        TextButton(enabled = i + 1 < file.events.size, onClick = { i++ }) { Text(stringResource(R.string.sub_next)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

// ---------------------------------------------------------------- Version history (AEGX10)

@Composable
fun VersionsDialog(name: String, onDismiss: () -> Unit, onRestore: (SubFile) -> Unit) {
    val ctx = LocalContext.current
    val versions by produceState<List<com.mediaforge.app.media.Autosave.Version>?>(null, name) {
        value = withContext(Dispatchers.IO) { com.mediaforge.app.media.Autosave.versions(ctx, name) }
    }
    val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ver_title)) },
        text = {
            val v = versions
            when {
                v == null -> Text(stringResource(R.string.gc_loading))
                v.isEmpty() -> Text(stringResource(R.string.ver_none))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(v) { ver ->
                        Row(Modifier.fillMaxWidth().clickable { com.mediaforge.app.media.Autosave.load(ver)?.let(onRestore) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ForceLtr { Text(fmt.format(java.util.Date(ver.timeMs)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium) }
                            Text(ctx.resources.getQuantityString(R.plurals.sub_lines, ver.lines, ver.lines), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
