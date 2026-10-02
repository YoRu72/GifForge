package com.gifforge.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onVideo: (Uri) -> Unit, onGif: (Uri) -> Unit, onBrowse: () -> Unit) {
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let(onVideo) }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let(onGif) }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("GifForge", style = MaterialTheme.typography.headlineLarge)
            Text("Video to GIF, done right", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            SectionCard(Icons.Filled.VideoLibrary, "Video to GIF", "Trim, crop, add text, export") {
                videoPicker.launch("video/*")
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(Icons.Filled.Crop, "Crop a GIF", "Crop a GIF you already made") {
                gifPicker.launch("image/gif")
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(Icons.Filled.Folder, "Media finder", "Browse your videos and GIFs by folder", onBrowse)
        }
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
