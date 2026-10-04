package com.mediaforge.app.ui

import androidx.compose.ui.res.stringResource
import com.mediaforge.app.R
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.GifProxy

/**
 * One editor for videos and GIFs: a GIF is converted once into a hidden video, then the normal
 * EditorScreen edits it. If conversion fails, the old lossless "quick" GIF editor is offered.
 */
@Composable
fun GifEditorHost(uri: Uri, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember(uri) { mutableFloatStateOf(0f) }
    var proxy by remember(uri) { mutableStateOf<GifProxy.Proxy?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var quick by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        GifProxy.build(ctx, uri) { progress = it }
            .onSuccess { proxy = it }
            .onFailure { error = it.message ?: ctx.getString(R.string.gh_unknown_error) }
    }

    val p = proxy
    val err = error
    when {
        quick -> GifCropScreen(uri, onBack)
        p != null -> EditorScreen(uri = Uri.fromFile(p.file), onBack = onBack, fpsHint = p.fpsHint, fullClip = true)
        err != null -> Scaffold { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.gh_prepare_fail), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(err, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { quick = true }) { Text(stringResource(R.string.gh_open_quick)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.gh_back)) }
            }
        }
        else -> Scaffold { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.gh_preparing), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.gh_once, (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
