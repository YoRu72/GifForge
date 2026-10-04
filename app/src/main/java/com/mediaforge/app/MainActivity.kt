package com.mediaforge.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.ui.EditorScreen
import com.mediaforge.app.ui.FontScreen
import com.mediaforge.app.ui.GifEditorHost
import com.mediaforge.app.ui.HomeScreen
import com.mediaforge.app.ui.MediaBrowserScreen
import com.mediaforge.app.ui.SettingsScreen
import com.mediaforge.app.ui.theme.MediaForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        FontStore.init(this)
        handleIntent(intent)
        setContent { MediaForgeTheme { AppRoot(incomingFont) { incomingFont = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A .ttf/.otf opened from a file manager or browser lands here. */
    private var incomingFont by mutableStateOf<Uri?>(null)

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { incomingFont = it }
    }
}

@Composable
private fun AppRoot(incomingFont: Uri?, onFontHandled: () -> Unit) {
    val ctx = LocalContext.current
    var video by rememberSaveable { mutableStateOf<Uri?>(null) }
    var gif by rememberSaveable { mutableStateOf<Uri?>(null) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var fonts by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(incomingFont) {
        val u = incomingFont ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { FontStore.importUri(ctx, u) }
        val msg = when {
            r.imported.isNotEmpty() -> "Font installed (${r.imported.size})"
            r.unsupported.isNotEmpty() -> "Android can't render ${r.unsupported.joinToString(", ")} fonts"
            else -> "That file isn't a usable font"
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        if (r.imported.isNotEmpty()) { video = null; gif = null; browsing = false; fonts = true }
        onFontHandled()
    }
    val v = video
    val g = gif
    when {
        v != null -> {
            BackHandler { video = null } // returns to the finder if that's where you came from
            EditorScreen(uri = v, onBack = { video = null })
        }
        g != null -> {
            BackHandler { gif = null }
            GifEditorHost(uri = g, onBack = { gif = null })
        }
        fonts -> {
            BackHandler { fonts = false }
            FontScreen(onBack = { fonts = false })
        }
        settings -> {
            BackHandler { settings = false }
            SettingsScreen(onBack = { settings = false }, onFonts = { fonts = true })
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
            onFonts = { fonts = true },
            onSettings = { settings = true },
        )
    }
}
