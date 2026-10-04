package com.mediaforge.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.Prefs
import com.mediaforge.app.R

/** A10: the Videos hub. One place to open a video, open a subtitle file, start a new one, or reopen a recent file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onVideo: (Uri) -> Unit, onGif: (Uri) -> Unit, onBrowse: () -> Unit,
    onSubtitle: (Uri) -> Unit, onNewSubtitle: () -> Unit,
) {
    val ctx = LocalContext.current
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { if (isGifUri(ctx, it)) onGif(it) else onVideo(it) }
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let {
            runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Prefs.set(Prefs.recentSubs, Recents.push(Prefs.recentSubs.value, it))
            onSubtitle(it)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.vid_title), style = MaterialTheme.typography.headlineSmall)
        HubCard(Icons.Filled.VideoLibrary, stringResource(R.string.vid_open_video), stringResource(R.string.vid_open_video_sub)) {
            videoPicker.launch(arrayOf("video/*", "image/gif"))
        }
        HubCard(Icons.Filled.Folder, stringResource(R.string.home_finder_title), stringResource(R.string.home_finder_sub), onBrowse)
        HubCard(Icons.Filled.Subtitles, stringResource(R.string.vid_open_sub), stringResource(R.string.vid_open_sub_sub)) {
            subPicker.launch(arrayOf("*/*"))
        }
        HubCard(Icons.Filled.NoteAdd, stringResource(R.string.vid_new_sub), stringResource(R.string.vid_new_sub_sub), onNewSubtitle)
        val recents = Recents.get(Prefs.recentSubs.value)
        if (recents.isNotEmpty()) {
            Text(stringResource(R.string.vid_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            recents.forEach { u ->
                HubCard(Icons.Filled.Subtitles, displayName(ctx, u), null) { onSubtitle(u) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubCard(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun isGifUri(ctx: android.content.Context, uri: Uri): Boolean {
    val t = ctx.contentResolver.getType(uri)
    if (t == "image/gif") return true
    if (t != null && t.startsWith("video/")) return false
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val b = ByteArray(4)
            s.read(b) == 4 && String(b, Charsets.ISO_8859_1) == "GIF8"
        } ?: false
    } catch (e: Exception) { false }
}
