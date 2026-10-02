package com.gifforge.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gifforge.app.ui.EditorScreen
import com.gifforge.app.ui.GifCropScreen
import com.gifforge.app.ui.HomeScreen
import com.gifforge.app.ui.MediaBrowserScreen
import com.gifforge.app.ui.theme.GifForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GifForgeTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var video by rememberSaveable { mutableStateOf<Uri?>(null) }
    var gif by rememberSaveable { mutableStateOf<Uri?>(null) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    val v = video
    val g = gif
    when {
        v != null -> {
            BackHandler { video = null } // returns to the finder if that's where you came from
            EditorScreen(uri = v, onBack = { video = null })
        }
        g != null -> {
            BackHandler { gif = null }
            GifCropScreen(uri = g, onBack = { gif = null })
        }
        browsing -> MediaBrowserScreen(
            onVideo = { video = it },
            onGif = { gif = it },
            onBack = { browsing = false },
        )
        else -> HomeScreen(
            onVideo = { video = it },
            onGif = { gif = it },
            onBrowse = { browsing = true },
        )
    }
}
