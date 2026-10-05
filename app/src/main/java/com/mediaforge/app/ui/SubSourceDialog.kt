package com.mediaforge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.media.SubTrack
import com.mediaforge.app.media.SubTracks
import kotlinx.coroutines.launch
import java.io.File

/**
 * Soft-sub menu: lists the subtitle tracks found inside [video] and lets the person pick one, import a separate
 * subtitle file, or start empty. Used before opening a video (Videos hub) and from the subtitle screen menu.
 * [onTrack] gets the extracted .ass; [onImport] asks the host to open the file picker; [onEmpty] may be null.
 */
@Composable
fun SubSourceDialog(
    video: android.net.Uri, onTrack: (File, SubTrack) -> Unit, onImport: () -> Unit, onEmpty: (() -> Unit)?, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<SubTrack>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(video) { tracks = SubTracks.list(ctx, video) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.st_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                val t = tracks
                when {
                    t == null || busy -> { CircularProgressIndicator(Modifier.padding(8.dp)); if (busy) Text(stringResource(R.string.st_extracting)) }
                    t.isEmpty() -> Text(stringResource(R.string.st_none))
                    else -> t.forEach { tr ->
                        val name = listOf(tr.title, tr.lang).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { stringResource(R.string.st_track_n, tr.index) }
                        OutlinedButton(
                            enabled = tr.textBased && SubTracks.canExtract(), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            onClick = { busy = true; failed = false; scope.launch { val f = SubTracks.extract(ctx, video, tr); busy = false; if (f != null) onTrack(f, tr) else failed = true } },
                        ) { Column { Text(name); Text(tr.codec.uppercase() + if (!tr.textBased) " - " + stringResource(R.string.st_image) else "", style = MaterialTheme.typography.labelSmall) } }
                    }
                }
                if (failed) Text(stringResource(R.string.st_failed), color = MaterialTheme.colorScheme.error)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sub_import)) }
                if (onEmpty != null) OutlinedButton(onClick = onEmpty, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(stringResource(R.string.st_empty)) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
