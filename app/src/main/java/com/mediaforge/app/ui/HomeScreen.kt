package com.mediaforge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R

@Composable
fun HomeScreen(onVideo: (Uri) -> Unit, onGif: (Uri) -> Unit, onBrowse: () -> Unit, onFonts: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    // one entry for both: videos and GIFs open in the same editor
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { if (isGif(ctx, it)) onGif(it) else onVideo(it) }
    }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("MediaForge", style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            SectionCard(Icons.Filled.VideoLibrary, stringResource(R.string.home_edit_title), stringResource(R.string.home_edit_sub)) {
                picker.launch(arrayOf("video/*", "image/gif"))
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(Icons.Filled.Folder, stringResource(R.string.home_finder_title), stringResource(R.string.home_finder_sub), onBrowse)
            Spacer(Modifier.height(12.dp))
            SectionCard(Icons.Filled.TextFields, stringResource(R.string.home_fonts_title), stringResource(R.string.home_fonts_sub), onFonts)
            Spacer(Modifier.height(12.dp))
            SectionCard(Icons.Filled.Settings, stringResource(R.string.home_settings_title), stringResource(R.string.home_settings_sub), onSettings)
        }
    }
}

private fun isGif(ctx: android.content.Context, uri: Uri): Boolean {
    val t = ctx.contentResolver.getType(uri)
    if (t == "image/gif") return true
    if (t != null && t.startsWith("video/")) return false
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val b = ByteArray(4)
            s.read(b) == 4 && String(b, Charsets.ISO_8859_1) == "GIF8"
        } ?: false
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
