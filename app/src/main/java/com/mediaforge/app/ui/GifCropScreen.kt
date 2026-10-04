package com.mediaforge.app.ui

import androidx.compose.ui.res.stringResource
import com.mediaforge.app.R
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mediaforge.app.media.GifMeta
import com.mediaforge.app.media.GifskiNative
import com.mediaforge.app.media.copyUriToCache
import com.mediaforge.app.media.cropGif
import com.mediaforge.app.media.readGifMeta
import com.mediaforge.app.media.saveGif
import com.mediaforge.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifCropScreen(uri: Uri, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberGifLoader()
    val crop = remember(uri) { CropState() }
    var src by remember { mutableStateOf<File?>(null) }
    var meta by remember { mutableStateOf<GifMeta?>(null) }
    var quality by remember { mutableIntStateOf(Prefs.defQuality.value) }
    var startF by remember { mutableIntStateOf(0) }
    var endF by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }

    LaunchedEffect(uri) {
        val f = withContext(Dispatchers.IO) { copyUriToCache(ctx, uri, "gifsrc_${System.currentTimeMillis()}.gif") }
        if (f == null) { status = ctx.getString(R.string.gc_open_fail); return@LaunchedEffect }
        val m = readGifMeta(f)
        if (m == null) { status = ctx.getString(R.string.gc_read_fail); f.delete(); return@LaunchedEffect }
        src = f
        meta = m
        startF = 0
        endF = (m.frames - 1).coerceAtLeast(0)
    }
    DisposableEffect(uri) { onDispose { src?.delete() } }

    val view = LocalView.current
    DisposableEffect(progress != null) {
        view.keepScreenOn = progress != null && Prefs.keepAwake.value
        onDispose { view.keepScreenOn = false }
    }

    fun runCrop() {
        val s = src ?: return
        val m = meta ?: return
        scope.launch {
            progress = 0f
            status = null
            ctx.cacheDir.listFiles { f -> f.name.startsWith("mediaforge_") }?.forEach { it.delete() }
            val out = File(ctx.cacheDir, "mediaforge_crop_${System.currentTimeMillis()}.gif")
            cropGif(s, out, crop.rect, m, quality, startF, endF + 1) { progress = it }
                .onSuccess { f ->
                    val saved = if (Prefs.autoSave.value) saveGif(ctx, f) else null
                    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.absolutePath, b)
                    result = ExportResult(f, saved != null, b.outWidth, b.outHeight)
                }
                .onFailure { status = if (it.message == "Cancelled") ctx.getString(R.string.gc_cancelled) else ctx.getString(R.string.gc_failed, it.message ?: "") }
            progress = null
        }
    }

    val trimmed = meta?.let { startF > 0 || endF < it.frames - 1 } ?: false
    result?.let { r -> GifResultDialog(r) { result = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.gh_back)) }
                },
                actions = {
                    if (progress == null) {
                        Button(
                            onClick = { runCrop() },
                            enabled = meta != null && (!crop.rect.isFull || trimmed),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.gc_export)) }
                    } else {
                        OutlinedButton(
                            onClick = { GifskiNative.nativeCancelCrop() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.gc_cancel)) }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            val m = meta
            val f = src
            if (m != null && f != null) {
                ForceLtr { Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AsyncImage(
                        model = f,
                        imageLoader = loader,
                        contentDescription = "GIF",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CropOverlay(crop.rect, { crop.rect = it }, crop.aspect, m.width, m.height)
                } }
                Text(
                    "${m.width}x${m.height} - ${m.frames} frames",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                GifTrimPanel(m, f, startF, endF) { s, e -> startF = s; endF = e }
                CropControls(crop, m.width, m.height)
                LabeledSlider(stringResource(R.string.gc_quality), "$quality", quality.toFloat(), 10f..100f, 0) {
                    quality = it.toInt()
                }
            } else if (status == null) {
                Text(stringResource(R.string.gc_loading), Modifier.padding(16.dp))
            }
            progress?.let {
                Text(stringResource(R.string.gc_working, (it * 100).toInt()), Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
            status?.let { Text(it, Modifier.padding(12.dp)) }
        }
    }
}
