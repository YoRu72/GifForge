package com.mediaforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.mediaforge.app.Prefs
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.AssStyle
import com.mediaforge.app.subs.StyleOps
import com.mediaforge.app.subs.SubFile
import kotlin.math.roundToInt

/** Live sample of a style, drawn by the same renderer as the video preview (so outline and shadow look as exported). */
@Composable
internal fun StyleSample(st: AssStyle, resX: Int = 1280, resY: Int = 720) {
    val sample = stringResource(R.string.stl_sample)
    val f = remember(st, resX, resY, sample) {
        val s = st.copy(alignment = 5, marginL = 0, marginR = 0, marginV = 0)
        SubFile.blank(resX, resY).copy(styles = listOf(s), events = listOf(AssEvent(startMs = 0, endMs = 1000, style = s.name, text = sample)))
    }
    Box(Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF5A6B7A))) {
        SubtitleOverlay(f, f.events, 0L, resX.toFloat() / resY.coerceAtLeast(1), Modifier.fillMaxSize())
    }
}

/**
 * Every field of an ASS style that matters on a phone: font, size, bold, italic, colours, outline, shadow, box,
 * alignment and margins. Letter spacing is left out on purpose: it breaks Arabic joining (B4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StyleEditor(st: AssStyle, showName: Boolean, onChange: (AssStyle) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showName) OutlinedTextField(
            st.name, { v -> v.replace(",", "").take(40).let { if (it.isNotBlank()) onChange(st.copy(name = it)) } },
            Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.stl_name)) },
        )
        Text(stringResource(R.string.tt_font), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(st.fontName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { picking = true }) { Text(stringResource(R.string.sub_pick_font)) }
        }
        LabeledSlider(stringResource(R.string.tt_size), st.fontSize.roundToInt().toString(), st.fontSize, 8f..200f, 0) { v -> onChange(st.copy(fontSize = v.roundToInt().toFloat())) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(st.bold, { onChange(st.copy(bold = !st.bold)) }, { Text(stringResource(R.string.tt_bold)) })
            FilterChip(st.italic, { onChange(st.copy(italic = !st.italic)) }, { Text(stringResource(R.string.sub_italic)) })
            FilterChip(st.borderStyle == 3, { onChange(st.copy(borderStyle = if (st.borderStyle == 3) 1 else 3)) }, { Text(stringResource(R.string.stl_box)) })
        }
        Text(stringResource(R.string.tt_color), style = MaterialTheme.typography.labelLarge)
        Swatches(st.primary) { c -> onChange(st.copy(primary = c)) }
        Text(stringResource(if (st.borderStyle == 3) R.string.stl_box_color else R.string.tt_stroke_color), style = MaterialTheme.typography.labelLarge)
        Swatches(st.outline) { c -> onChange(st.copy(outline = c)) }
        LabeledSlider(stringResource(R.string.stl_outline), fmt1(st.outlineWidth), st.outlineWidth, 0f..10f, 19) { v -> onChange(st.copy(outlineWidth = half(v))) }
        LabeledSlider(stringResource(R.string.stl_shadow), fmt1(st.shadow), st.shadow, 0f..10f, 19) { v -> onChange(st.copy(shadow = half(v))) }
        if (st.shadow > 0f) {
            Text(stringResource(R.string.stl_shadow_color), style = MaterialTheme.typography.labelLarge)
            Swatches(st.back or (0xFF shl 24)) { c -> onChange(st.copy(back = (c and 0x00FFFFFF) or (st.back and 0xFF000000.toInt()))) }
            val a = ((st.back ushr 24) and 0xFF) / 255f
            LabeledSlider(stringResource(R.string.stl_shadow_opacity), "${(a * 100).roundToInt()}%", a, 0f..1f, 0) { v ->
                onChange(st.copy(back = (st.back and 0x00FFFFFF) or ((v * 255f).roundToInt().coerceIn(0, 255) shl 24)))
            }
        }
        Text(stringResource(R.string.tt_snap), style = MaterialTheme.typography.labelLarge)
        AnPad(st.alignment) { a -> onChange(st.copy(alignment = a)) }
        LabeledSlider(stringResource(R.string.sub_margin_v), st.marginV.toString(), st.marginV.toFloat(), 0f..300f, 0) { v -> onChange(st.copy(marginV = v.roundToInt())) }
        LabeledSlider(stringResource(R.string.stl_margin_sides), st.marginL.toString(), st.marginL.toFloat(), 0f..300f, 0) { v ->
            onChange(st.copy(marginL = v.roundToInt(), marginR = v.roundToInt()))
        }
    }
    if (picking) FontPickerDialog(onPick = { name -> onChange(st.copy(fontName = name)); picking = false }, onClose = { picking = false })
}

private fun half(v: Float) = (v * 2f).roundToInt() / 2f
private fun fmt1(v: Float) = if (v % 1f == 0f) v.roundToInt().toString() else "%.1f".format(java.util.Locale.US, v)

/**
 * 19.a / AEGX8: styles manager. Pick a style, edit it live (the video preview follows), add, duplicate, delete,
 * give it to every line or to the marked ones, clear per-line looks so the style decides, keep it as the base style.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylesDialog(file: SubFile, marked: Set<Int>, onDismiss: () -> Unit, onFile: (SubFile) -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(file.styles.firstOrNull()?.name ?: "Default") }
    val st = file.styles.firstOrNull { it.name == sel } ?: file.styles.firstOrNull() ?: AssStyle()
    val usage = StyleOps.usage(file)
    val missing = StyleOps.missing(file)
    // the base style is kept at 720 lines; a style from a 1080 script is scaled on the way in and out
    val k = 720f / file.playResY.coerceAtLeast(1)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.stl_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    file.styles.forEach { s ->
                        FilterChip(s.name == st.name, { sel = s.name }, { Text(s.name + " (" + (usage[s.name] ?: 0) + ")") })
                    }
                    FilterChip(false, {
                        val n = StyleOps.freeName(file, "Style"); onFile(StyleOps.add(file, st.copy(name = n))); sel = n
                    }, { Text("+") })
                }
                if (missing.isNotEmpty()) Text(
                    stringResource(R.string.stl_missing, missing.joinToString(", ")),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
                StyleSample(st, file.playResX, file.playResY)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    StyleEditor(st, showName = true) { ns ->
                        if (ns.name != st.name && file.styles.any { it.name == ns.name }) return@StyleEditor // names stay unique
                        onFile(StyleOps.update(file, st.name, ns)); sel = ns.name
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onFile(StyleOps.assign(file, st.name)) }) { Text(stringResource(R.string.stl_use_all)) }
                        if (marked.isNotEmpty()) OutlinedButton(onClick = { onFile(StyleOps.assign(file, st.name, marked)) }) { Text(stringResource(R.string.stl_use_marked)) }
                        OutlinedButton(onClick = {
                            val (f2, n) = StyleOps.clearLineLooks(file); onFile(f2)
                            Toast.makeText(ctx, ctx.getString(R.string.stl_cleared, n), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.stl_clear_looks)) }
                        OutlinedButton(onClick = { val n = StyleOps.freeName(file, st.name); onFile(StyleOps.add(file, st.copy(name = n))); sel = n }) {
                            Text(stringResource(R.string.stl_duplicate))
                        }
                        OutlinedButton(enabled = file.styles.size > 1, onClick = { onFile(StyleOps.delete(file, st.name)); sel = file.styles.first { it.name != st.name }.name }) {
                            Text(stringResource(R.string.delete))
                        }
                        OutlinedButton(onClick = {
                            Prefs.set(Prefs.subBaseStyle, StyleOps.save(StyleOps.rescale(st, k)))
                            Toast.makeText(ctx, R.string.stl_base_saved, Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.stl_save_base)) }
                        OutlinedButton(onClick = { onFile(StyleOps.update(file, st.name, StyleOps.rescale(baseStyle(), 1f / k).copy(name = st.name))) }) {
                            Text(stringResource(R.string.stl_load_base))
                        }
                    }
                }
            }
        }
    }
}

/** Settings: the base style every new or imported (SRT, VTT...) script starts with. */
@Composable
fun BaseStyleDialog(onDismiss: () -> Unit) {
    var st by remember { mutableStateOf(baseStyle()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(stringResource(R.string.stl_base_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.stl_base_note), style = MaterialTheme.typography.bodySmall)
                StyleSample(st)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    StyleEditor(st, showName = false) { st = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { st = StyleOps.FACTORY }) { Text(stringResource(R.string.stl_reset)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { Prefs.set(Prefs.subBaseStyle, StyleOps.save(st)); onDismiss() }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
