package com.gifforge.app.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gifforge.app.media.VideoInfo
import com.gifforge.app.media.encodeGif
import com.gifforge.app.media.readVideoInfo
import com.gifforge.app.media.renderOverlayBitmap
import com.gifforge.app.media.saveGif
import android.graphics.BitmapFactory
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val tabs = listOf("Trim", "Crop", "Text", "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(uri) { EditorState() }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }

    // Keep the screen awake while encoding
    val view = LocalView.current
    DisposableEffect(progress != null) {
        view.keepScreenOn = progress != null
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(uri) {
        val i = readVideoInfo(ctx, uri) ?: return@LaunchedEffect
        info = i
        state.durationMs = i.durationMs
        state.startMs = 0L
        state.endMs = minOf(i.durationMs, 10_000L) // sensible default; adjust in Trim tab
    }

    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    // Loop playback inside the trimmed range
    LaunchedEffect(player) {
        while (true) {
            delay(40)
            if (state.endMs > 0 && player.isPlaying) {
                val p = player.currentPosition
                if (p >= state.endMs || p < state.startMs - 300) player.seekTo(state.startMs)
            }
        }
    }
    LaunchedEffect(player, state.speed) { player.setPlaybackSpeed(state.speed) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val overlayBmp = remember(state.overlays, state.crop.rect, info) {
        info?.let { i ->
            val c = state.crop.rect.toPx(i.width, i.height)
            val pw = minOf(720, c[2]).coerceAtLeast(2)
            val ph = (pw.toLong() * c[3] / c[2]).toInt().coerceAtLeast(2)
            renderOverlayBitmap(pw, ph, state.overlays)
        }
    }

    fun export() {
        if (info == null) return
        job = scope.launch {
            player.pause()
            progress = 0f
            status = null
            ctx.cacheDir.listFiles { f -> f.name.startsWith("gifforge_") }?.forEach { it.delete() }
            val tmp = File(ctx.cacheDir, "gifforge_${System.currentTimeMillis()}.gif")
            try {
                encodeGif(ctx, uri, tmp, state.toOptions()) { progress = it }
                    .onSuccess { f ->
                        val saved = saveGif(ctx, f)
                        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.absolutePath, b)
                        result = ExportResult(f, saved != null, b.outWidth, b.outHeight)
                    }
                    .onFailure { status = "Failed: ${it.message}" }
            } catch (e: CancellationException) {
                status = "Export cancelled"
                throw e
            } finally {
                progress = null
            }
        }
    }

    result?.let { r -> GifResultDialog(r) { result = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (progress == null) {
                        Button(
                            onClick = { export() },
                            enabled = info != null,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Export") }
                    } else {
                        OutlinedButton(
                            onClick = { job?.cancel() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Cancel") }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxWidth().height(240.dp).onSizeChanged { boxSize = it }) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = player } },
                    modifier = Modifier.fillMaxSize(),
                )
                val inf = info
                if (inf != null) {
                    // dim everything outside the crop, and draw text inside the crop area
                    Canvas(Modifier.fillMaxSize()) {
                        val fr = fitBox(size.width, size.height, inf.width, inf.height)
                        val r = state.crop.rect
                        val l = fr.x + r.l * fr.w
                        val t = fr.y + r.t * fr.h
                        val rr = fr.x + r.r * fr.w
                        val bb = fr.y + r.b * fr.h
                        if (!r.isFull && tab != 1) {
                            val dim = Color(0xB3000000)
                            drawRect(dim, Offset(fr.x, fr.y), Size(fr.w, t - fr.y))
                            drawRect(dim, Offset(fr.x, bb), Size(fr.w, fr.y + fr.h - bb))
                            drawRect(dim, Offset(fr.x, t), Size(l - fr.x, bb - t))
                            drawRect(dim, Offset(rr, t), Size(fr.x + fr.w - rr, bb - t))
                        }
                        overlayBmp?.let {
                            drawImage(
                                it.asImageBitmap(),
                                dstOffset = IntOffset(l.roundToInt(), t.roundToInt()),
                                dstSize = IntSize((rr - l).roundToInt(), (bb - t).roundToInt()),
                            )
                        }
                    }
                    if (tab == 1) {
                        CropOverlay(state.crop.rect, { state.crop.rect = it }, state.crop.aspect, inf.width, inf.height)
                    }
                    val sel = state.selected()
                    if (tab == 2 && sel != null) {
                        // Drag on the preview to move the selected text (relative to the crop area)
                        Box(
                            Modifier.fillMaxSize().pointerInput(sel.id, inf, boxSize) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val fr = fitBox(boxSize.width.toFloat(), boxSize.height.toFloat(), inf.width, inf.height)
                                    val cr = state.crop.rect
                                    val dw = fr.w * (cr.r - cr.l)
                                    val dh = fr.h * (cr.b - cr.t)
                                    state.update(sel.id) {
                                        it.copy(
                                            posX = (it.posX + drag.x / dw).coerceIn(0f, 1f),
                                            posY = (it.posY + drag.y / dh).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            info?.let {
                Text(
                    "${it.width}x${it.height} - ${"%.1f".format(it.durationMs / 1000f)}s" +
                        (it.fps?.let { f -> " - ${"%.0f".format(f)} fps" } ?: ""),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            progress?.let {
                Text(
                    "Encoding... ${(it * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
            status?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> TrimTab(state, info, player)
                    1 -> CropTab(state, info)
                    2 -> TextTab(state)
                    else -> SettingsTab(state, info)
                }
            }
        }
    }
}
