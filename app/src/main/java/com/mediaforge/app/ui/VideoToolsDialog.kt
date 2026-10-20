package com.mediaforge.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.app.Prefs
import com.mediaforge.app.R
import com.mediaforge.app.media.VideoTools
import com.mediaforge.app.media.saveAudio
import com.mediaforge.app.media.saveVideo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** FEAT1 in the Videos hub: join clips into one MP4, extract the audio of a video. */
@Composable
fun VideoToolsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us -> clips = clips + us }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch {
            progress = 0.5f
            val f = VideoTools.extractAudio(ctx, u, File(ctx.cacheDir, "mediaforge_${System.currentTimeMillis()}.m4a"))
            val saved = f?.let { saveAudio(ctx, it) }
            progress = -1f
            Toast.makeText(ctx, if (saved != null) R.string.vt_audio_saved else R.string.vt_audio_failed, Toast.LENGTH_LONG).show()
        }
    }
    result?.let { r -> GifResultDialog(r) { result = null } }
    AlertDialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        title = { Text(stringResource(R.string.vt_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.vt_join), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.vt_join_note), style = MaterialTheme.typography.bodySmall)
                clips.forEachIndexed { i, u ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${i + 1}. " + displayName(ctx, u), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = i > 0, onClick = { clips = clips.toMutableList().also { val x = it[i]; it[i] = it[i - 1]; it[i - 1] = x } }) { Text("↑") }
                        TextButton(onClick = { clips = clips.filterIndexed { k, _ -> k != i } }) { Text("✕") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = progress < 0f, onClick = { picker.launch(arrayOf("video/*")) }) { Text(stringResource(R.string.vt_add_clips)) }
                    Button(enabled = clips.size >= 2 && progress < 0f, onClick = {
                        job = scope.launch {
                            progress = 0f
                            val out = File(ctx.cacheDir, "mediaforge_${System.currentTimeMillis()}.mp4")
                            VideoTools.join(ctx, clips, out, Prefs.defQuality.value) { progress = it }
                                .onSuccess { o ->
                                    val saved = if (Prefs.autoSave.value) saveVideo(ctx, o.file) else null
                                    result = ExportResult(o.file, saved != null, o.width, o.height, video = true, withAudio = o.withAudio)
                                }
                                .onFailure { Toast.makeText(ctx, ctx.getString(R.string.ed_failed, it.message ?: ""), Toast.LENGTH_LONG).show() }
                            progress = -1f
                        }
                    }) { Text(stringResource(R.string.vt_do_join)) }
                }
                if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.vt_audio), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.vt_audio_note), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = progress < 0f, onClick = { audioPicker.launch(arrayOf("video/*")) }) { Text(stringResource(R.string.vt_do_audio)) }
            }
        },
        confirmButton = { TextButton(onClick = { job?.cancel(); onDismiss() }) { Text(stringResource(if (progress >= 0f) R.string.cancel else R.string.close)) } },
    )
}
