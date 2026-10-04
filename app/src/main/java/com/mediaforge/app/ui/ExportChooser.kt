package com.mediaforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.SubFormats

/** What the owner asked for before exporting: the subtitle file alone, the video with a soft track, or the video with burned-in subtitles. */
enum class ExportMode { FILE, SOFT, HARD }

/** A subtitle text encoding choice for the file export. Names are technical and stay as they are. */
data class EncChoice(val label: String, val charset: String, val bom: Boolean)

val encChoices = listOf(
    EncChoice("UTF-8", "UTF-8", false),
    EncChoice("UTF-8 BOM", "UTF-8", true),
    EncChoice("UTF-16 LE", "UTF-16LE", true),
    EncChoice("Windows-1256", "windows-1256", false),
    EncChoice("ISO-8859-6", "ISO-8859-6", false),
)

/** Video export settings (A17). Kept as plain data so the encoder step can read them unchanged. */
data class VideoExportSettings(
    val container: String = "mkv",   // mp4 | mkv
    val codec: String = "h264",      // h264 | hevc | vp9 | av1 | copy (soft only)
    val crf: Int = 23,
    val height: Int = 0,             // 0 = original
    val audioCopy: Boolean = true,
    val subLang: String = "ara",     // language tag of the soft subtitle track (ISO 639-2)
)

/**
 * A17: the chooser shown BEFORE exporting. One sheet, three paths. The file path works now (any format, any
 * encoding). The two video paths collect their settings and wait for the video engine (A17.a/b), so they show
 * a clear notice instead of failing silently.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportChooser(
    hasVideo: Boolean,
    engineReady: Boolean,
    defaultFormatId: String,
    onDismiss: () -> Unit,
    onExportFile: (formatId: String, enc: EncChoice) -> Unit,
    onExportVideo: (ExportMode, VideoExportSettings) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ExportMode.FILE) }
    var fmt by rememberSaveable { mutableStateOf(defaultFormatId) }
    var encIdx by rememberSaveable { mutableStateOf(0) }
    var container by rememberSaveable { mutableStateOf("mkv") }
    var codec by rememberSaveable { mutableStateOf("h264") }
    var crf by remember { mutableFloatStateOf(23f) }
    var height by rememberSaveable { mutableStateOf(0) }
    var audioCopy by rememberSaveable { mutableStateOf(true) }
    var lang by rememberSaveable { mutableStateOf("ara") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ex_title), style = MaterialTheme.typography.titleLarge)
            ModeRow(mode == ExportMode.FILE, stringResource(R.string.ex_mode_file), stringResource(R.string.ex_mode_file_sub)) { mode = ExportMode.FILE }
            ModeRow(mode == ExportMode.SOFT, stringResource(R.string.ex_mode_soft), stringResource(R.string.ex_mode_soft_sub)) { mode = ExportMode.SOFT }
            ModeRow(mode == ExportMode.HARD, stringResource(R.string.ex_mode_hard), stringResource(R.string.ex_mode_hard_sub)) { mode = ExportMode.HARD }
            HorizontalDivider()

            if (mode == ExportMode.FILE) {
                Text(stringResource(R.string.ex_format), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubFormats.all.forEach { f -> FilterChip(selected = fmt == f.id, onClick = { fmt = f.id }, label = { Text(f.label) }) }
                }
                if (fmt != "ass") Text(stringResource(R.string.ex_lossy_note), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ex_encoding), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    encChoices.forEachIndexed { i, c -> FilterChip(selected = encIdx == i, onClick = { encIdx = i }, label = { Text(c.label) }) }
                }
                Button(onClick = { onExportFile(fmt, encChoices[encIdx]) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.ex_do_file)) }
            } else {
                val soft = mode == ExportMode.SOFT
                Text(stringResource(R.string.ex_container), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mkv" to "MKV", "mp4" to "MP4").forEach { (id, l) -> FilterChip(selected = container == id, onClick = { container = id }, label = { Text(l) }) }
                }
                if (soft) Text(stringResource(if (container == "mkv") R.string.ex_soft_mkv_note else R.string.ex_soft_mp4_note), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ex_codec), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val list = buildList {
                        if (soft) add("copy" to stringResource(R.string.ex_codec_copy))
                        add("h264" to "H.264"); add("hevc" to "HEVC"); add("vp9" to "VP9"); add("av1" to "AV1")
                    }
                    list.forEach { (id, l) -> FilterChip(selected = codec == id, onClick = { codec = id }, label = { Text(l) }) }
                }
                if (codec != "copy") {
                    Text(stringResource(R.string.ex_quality, crf.toInt()), style = MaterialTheme.typography.titleSmall)
                    ForceLtr { Slider(crf, { crf = it }, valueRange = 16f..34f, steps = 17) }
                    Text(stringResource(R.string.ex_resolution), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1080, 720, 480).forEach { h ->
                            FilterChip(selected = height == h, onClick = { height = h }, label = { Text(if (h == 0) stringResource(R.string.ex_original) else "${h}p") })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = audioCopy, onClick = { audioCopy = !audioCopy }, label = { Text(stringResource(R.string.ex_audio_copy)) })
                }
                if (!hasVideo) Text(stringResource(R.string.ex_need_video), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (soft) {
                    Text(stringResource(R.string.ex_sub_lang), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ara" to stringResource(R.string.ex_lang_ar), "eng" to stringResource(R.string.ex_lang_en), "und" to stringResource(R.string.ex_lang_und)).forEach { (id, l) ->
                            FilterChip(selected = lang == id, onClick = { lang = id }, label = { Text(l) })
                        }
                    }
                }
                if (!engineReady) Text(stringResource(R.string.ex_engine_note), style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = hasVideo && engineReady,
                    onClick = { onExportVideo(mode, VideoExportSettings(container, codec, crf.toInt(), height, audioCopy, lang)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ex_do_video)) }
            }
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, Modifier.padding(end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
