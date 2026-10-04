package com.mediaforge.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import com.mediaforge.app.l10n.AppLang
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mediaforge.app.ui.SubDoc
import com.mediaforge.app.ui.SubtitleListScreen
import com.mediaforge.app.ui.VideosScreen
import com.mediaforge.app.ui.newSubDoc
import com.mediaforge.app.ui.readSubDoc
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
        AppLang.init(this) // remember the device language before the app language is applied
        Prefs.init(this)
        FontStore.init(this)
        handleIntent(intent)
        setContent {
            val pref = Prefs.lang.value
            val lctx = remember(pref) { AppLang.wrap(this@MainActivity, pref) }
            CompositionLocalProvider(
                LocalContext provides lctx,
                LocalConfiguration provides lctx.resources.configuration,
                LocalLayoutDirection provides AppLang.direction(pref),
            ) {
                MediaForgeTheme { AppRoot(incomingFont) { incomingFont = null } }
            }
        }
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
    var subUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var newSub by rememberSaveable { mutableStateOf(false) }
    var subVideo by rememberSaveable { mutableStateOf<Uri?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) } // 0 home, 1 videos, 2 fonts, 3 settings
    LaunchedEffect(incomingFont) {
        val u = incomingFont ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { FontStore.importUri(ctx, u) }
        val msg = when {
            r.imported.isNotEmpty() -> ctx.getString(R.string.font_installed, r.imported.size)
            r.unsupported.isNotEmpty() -> ctx.getString(R.string.font_unsupported, r.unsupported.joinToString(", "))
            else -> ctx.getString(R.string.font_invalid)
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        if (r.imported.isNotEmpty()) { video = null; gif = null; browsing = false; subUri = null; newSub = false; subVideo = null; tab = 2 }
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
        subUri != null || newSub -> {
            BackHandler { subUri = null; newSub = false; subVideo = null }
            SubtitleHost(subUri, subVideo) { subUri = null; newSub = false; subVideo = null }
        }
        browsing -> MediaBrowserScreen(
            onVideo = { video = it },
            onGif = { gif = it },
            onBack = { browsing = false },
        )
        else -> {
            BackHandler(enabled = tab != 0) { tab = 0 }
            MainShell(tab, { tab = it }) { pad ->
                Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                    when (tab) {
                        0 -> HomeScreen(
                            onVideo = { video = it }, onGif = { gif = it },
                            onBrowse = { browsing = true }, onFonts = { tab = 2 }, onSettings = { tab = 3 },
                        )
                        1 -> VideosScreen(
                            onVideo = { video = it }, onGif = { gif = it }, onBrowse = { browsing = true },
                            onSubtitle = { subUri = it }, onNewSubtitle = { newSub = true },
                            onSubtitleVideo = { subVideo = it; newSub = true },
                        )
                        2 -> FontScreen(onBack = { tab = 0 })
                        else -> SettingsScreen(onBack = { tab = 0 }, onFonts = { tab = 2 })
                    }
                }
            }
        }
    }
}

/** Loads a subtitle file off the main thread, then shows the list editor. */
@Composable
private fun SubtitleHost(uri: Uri?, videoUri: Uri?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state = produceState<Any?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            if (uri == null) newSubDoc() else readSubDoc(ctx, uri) ?: false
        }
    }
    when (val d = state.value) {
        is SubDoc -> SubtitleListScreen(d, onBack, videoUri)
        false -> {
            LaunchedEffect(Unit) {
                Toast.makeText(ctx, R.string.sub_unreadable, Toast.LENGTH_LONG).show()
                onBack()
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** Rexplayer-style bottom navigation: Home, Videos, Fonts, Settings. */
@Composable
private fun MainShell(tab: Int, onTab: (Int) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val items = listOf(
        Triple(R.string.nav_home, Icons.Filled.Home, 0),
        Triple(R.string.nav_videos, Icons.Filled.VideoLibrary, 1),
        Triple(R.string.nav_fonts, Icons.Filled.TextFields, 2),
        Triple(R.string.nav_settings, Icons.Filled.Settings, 3),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { onTab(i) },
                        icon = { Icon(icon, null) }, label = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }
        },
        content = content,
    )
}
