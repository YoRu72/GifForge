package com.mediaforge.app.ui

import androidx.compose.ui.res.stringResource

import android.net.Uri
import android.view.LayoutInflater
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.mediaforge.app.media.LayerBlend
import com.mediaforge.app.media.renderStillFrame
import androidx.compose.foundation.gestures.detectTransformGestures
import com.mediaforge.app.media.normDeg
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import com.mediaforge.app.R
import com.mediaforge.app.media.renderBarsBitmap
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.mediaforge.app.media.VideoInfo
import com.mediaforge.app.media.encodeGif
import com.mediaforge.app.media.readVideoInfo
import com.mediaforge.app.media.renderLayers
import androidx.compose.runtime.mutableLongStateOf
import com.mediaforge.app.media.saveGif
import com.mediaforge.app.Prefs
import android.graphics.BitmapFactory
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val tabs = listOf(R.string.tab_trim, R.string.tab_crop, R.string.tab_text, R.string.tab_bars, R.string.tab_shapes, R.string.tab_settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit, fpsHint: Int? = null, fullClip: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(uri) { EditorState() }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    var fontPicker by remember { mutableStateOf(false) }
    var blendFrame by remember { mutableStateOf<Bitmap?>(null) }
    var playMs by remember { mutableLongStateOf(0L) } // playhead, read only while drawing so playback doesn't recompose
    var fontTarget by remember { mutableIntStateOf(0) } // 0 = selected text, 1 = top bar, 2 = bottom bar

    // Keep the screen awake while encoding
    val view = LocalView.current
    DisposableEffect(progress != null) {
        view.keepScreenOn = progress != null && Prefs.keepAwake.value
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(uri) {
        val i = readVideoInfo(ctx, uri) ?: return@LaunchedEffect
        info = i
        state.durationMs = i.durationMs
        state.startMs = 0L
        state.endMs = if (fullClip) i.durationMs else minOf(i.durationMs, Prefs.defClipSec.value * 1000L)
        fpsHint?.let { state.fps = it.coerceIn(5, 30) } // GIF sources start at their own frame rate // sensible default; adjust in Trim tab
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
            if (state.hasTimed) playMs = player.currentPosition
            if (state.endMs > 0 && player.isPlaying) {
                val p = player.currentPosition
                if (p >= state.endMs || p < state.startMs - 300) player.seekTo(state.startMs)
            }
        }
    }
    LaunchedEffect(player, state.speed) { player.setPlaybackSpeed(state.speed) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val overlayLayers = remember(state.overlays, state.elements, state.crop.rect, info) {
        info?.let { i ->
            val c = state.crop.rect.toPx(i.width, i.height)
            val pw = minOf(720, c[2]).coerceAtLeast(2)
            val ph = (pw.toLong() * c[3] / c[2]).toInt().coerceAtLeast(2)
            renderLayers(pw, ph, state.overlays, state.elements)
        } ?: emptyList()
    }
    val overlayImages = remember(overlayLayers) { overlayLayers.map { it.bmp.asImageBitmap() } }

    val topPct = state.topBar?.heightPct ?: 0f
    val botPct = state.bottomBar?.heightPct ?: 0f
    val compMode = (topPct > 0f || botPct > 0f) && tab != 1 // preview shows video + bars together
    val barsBmp = remember(state.topBar, state.bottomBar, state.crop.rect, info) {
        info?.let { i ->
            val c = state.crop.rect.toPx(i.width, i.height)
            val pw = minOf(720, c[2]).coerceAtLeast(2)
            val ph = (pw.toLong() * c[3] / c[2]).toInt().coerceAtLeast(2)
            renderBarsBitmap(pw, ph, state.topBar, state.bottomBar)
        }
    }

    fun export() {
        if (info == null) return
        job = scope.launch {
            player.pause()
            progress = 0f
            status = null
            ctx.cacheDir.listFiles { f -> f.name.startsWith("mediaforge_") }?.forEach { it.delete() }
            val tmp = File(ctx.cacheDir, "mediaforge_${System.currentTimeMillis()}.gif")
            try {
                encodeGif(ctx, uri, tmp, state.toOptions()) { progress = it }
                    .onSuccess { f ->
                        val saved = if (Prefs.autoSave.value) saveGif(ctx, f) else null
                        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.absolutePath, b)
                        result = ExportResult(f, saved != null, b.outWidth, b.outHeight)
                    }
                    .onFailure { status = ctx.getString(R.string.ed_failed, it.message ?: "") }
            } catch (e: CancellationException) {
                status = ctx.getString(R.string.ed_cancelled)
                throw e
            } finally {
                progress = null
            }
        }
    }

    if (fontPicker) {
        Dialog(
            onDismissRequest = { fontPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                FontScreen(
                    onBack = { fontPicker = false },
                    onPick = { path ->
                        when (fontTarget) {
                            1 -> state.topBar = state.topBar?.copy(fontPath = path, fontVars = null)
                            2 -> state.bottomBar = state.bottomBar?.copy(fontPath = path, fontVars = null)
                            else -> state.selected()?.let { s -> state.update(s.id) { it.copy(fontPath = path) } }
                        }
                        fontPicker = false
                    },
                )
            }
        }
    }

    result?.let { r -> GifResultDialog(r) { result = null } }
    blendFrame?.let { bf ->
        AlertDialog(
            onDismissRequest = { blendFrame = null },
            title = { Text(stringResource(R.string.ed_blended)) },
            text = { Image(bf.asImageBitmap(), stringResource(R.string.ed_blended), Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { blendFrame = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ed_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (progress == null) {
                        Button(
                            onClick = { export() },
                            enabled = info != null,
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.export)) }
                    } else {
                        OutlinedButton(
                            onClick = { job?.cancel() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ForceLtr { Box(Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF101010)).onSizeChanged { boxSize = it }) {
                val inf = info
                if (compMode && inf != null && boxSize.width > 0) {
                    val fr = previewFrame(boxSize.width.toFloat(), boxSize.height.toFloat(), inf.width, inf.height, state.crop.rect, topPct, botPct)
                    val cr = state.crop.rect
                    val vx = fr.x + cr.l * fr.w
                    val vy = fr.y + cr.t * fr.h
                    val vw = (cr.r - cr.l) * fr.w
                    val vh = (cr.b - cr.t) * fr.h
                    val dens = LocalDensity.current
                    Box(
                        Modifier
                            .offset { IntOffset(vx.roundToInt(), vy.roundToInt()) }
                            .size(with(dens) { vw.toDp() }, with(dens) { vh.toDp() })
                            .clipToBounds(),
                    ) {
                        AndroidView(
                            factory = { c ->
                                (LayoutInflater.from(c).inflate(R.layout.player_texture, null) as PlayerView)
                                    .apply { this.player = player; useController = false }
                            },
                            onRelease = { it.player = null },
                            modifier = Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .size(with(dens) { fr.w.toDp() }, with(dens) { fr.h.toDp() })
                                .offset { IntOffset((fr.x - vx).roundToInt(), (fr.y - vy).roundToInt()) },
                        )
                    }
                } else {
                    AndroidView(
                        factory = { c -> PlayerView(c).apply { this.player = player; useController = false } },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (inf != null) {
                    // dim everything outside the crop, and draw text inside the crop area
                    Canvas(Modifier.fillMaxSize()) {
                        val fr = previewFrame(size.width, size.height, inf.width, inf.height, state.crop.rect, if (compMode) topPct else 0f, if (compMode) botPct else 0f)
                        val r = state.crop.rect
                        val l = fr.x + r.l * fr.w
                        val t = fr.y + r.t * fr.h
                        val rr = fr.x + r.r * fr.w
                        val bb = fr.y + r.b * fr.h
                        if (!r.isFull && tab != 1 && !compMode) {
                            val dim = Color(0xB3000000)
                            drawRect(dim, Offset(fr.x, fr.y), Size(fr.w, t - fr.y))
                            drawRect(dim, Offset(fr.x, bb), Size(fr.w, fr.y + fr.h - bb))
                            drawRect(dim, Offset(fr.x, t), Size(l - fr.x, bb - t))
                            drawRect(dim, Offset(rr, t), Size(fr.x + fr.w - rr, bb - t))
                        }
                        if (compMode) barsBmp?.let {
                            val tH = (bb - t) * topPct / 100f
                            val bH = (bb - t) * botPct / 100f
                            drawImage(
                                it.asImageBitmap(),
                                dstOffset = IntOffset(l.roundToInt(), (t - tH).roundToInt()),
                                dstSize = IntSize((rr - l).roundToInt(), (bb - t + tH + bH).roundToInt()),
                            )
                        }
                        val nowMs = playMs
                        overlayLayers.forEachIndexed { li, ly ->
                            if (ly.visibleAt(nowMs)) {
                                drawImage(
                                    overlayImages[li],
                                    dstOffset = IntOffset(l.roundToInt(), t.roundToInt()),
                                    dstSize = IntSize((rr - l).roundToInt(), (bb - t).roundToInt()),
                                    alpha = ly.opacity,
                                )
                            }
                        }
                    }
                    if (tab == 0 || tab == 3 || tab == 5 || (tab == 2 && state.selected() == null) || (tab == 4 && state.selectedElement() == null)) {
                        // tap the video to play / pause
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures { if (player.isPlaying) player.pause() else player.play() }
                            },
                        )
                    }
                    if (tab == 1) {
                        CropOverlay(state.crop.rect, { state.crop.rect = it }, state.crop.aspect, inf.width, inf.height)
                    }
                    val selEl = state.selectedElement()
                    if (tab == 4 && selEl != null) {
                        // drag = move, pinch = resize, twist = rotate the selected shape
                        Box(
                            Modifier.fillMaxSize().pointerInput(selEl.id, inf, boxSize, topPct, botPct) {
                                detectTransformGestures { _, pan, zoom, rot ->
                                    val fr = previewFrame(boxSize.width.toFloat(), boxSize.height.toFloat(), inf.width, inf.height, state.crop.rect, topPct, botPct)
                                    val cr = state.crop.rect
                                    val dw = fr.w * (cr.r - cr.l)
                                    val dh = fr.h * (cr.b - cr.t)
                                    state.updateElement(selEl.id) {
                                        it.copy(
                                            cx = (it.cx + pan.x / dw).coerceIn(0f, 1f),
                                            cy = (it.cy + pan.y / dh).coerceIn(0f, 1f),
                                            w = (it.w * zoom).coerceIn(0.02f, 1.5f),
                                            h = (it.h * zoom).coerceIn(0.02f, 1.5f),
                                            rotation = normDeg(it.rotation + rot),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    val sel = state.selected()
                    if (tab == 2 && sel != null) {
                        // Drag on the preview to move the selected text (relative to the crop area)
                        Box(
                            Modifier.fillMaxSize().pointerInput(sel.id, inf, boxSize, topPct, botPct) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val fr = previewFrame(boxSize.width.toFloat(), boxSize.height.toFloat(), inf.width, inf.height, state.crop.rect, topPct, botPct)
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
            } }
            ForceLtr { TransportBar(player, state.durationMs, state.startMs, state.endMs) }
            if (state.overlays.any { it.blend != LayerBlend.NORMAL } || state.elements.any { it.blend != LayerBlend.NORMAL }) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val at = player.currentPosition.coerceIn(state.startMs, maxOf(state.startMs, state.endMs))
                            blendFrame = renderStillFrame(ctx, uri, state.toOptions(), at)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.ed_see_blend)) }
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
                    stringResource(R.string.ed_encoding, (it * 100).toInt()),
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
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> TrimTab(state, info, player)
                    1 -> CropTab(state, info)
                    2 -> TextTab(state, player) { fontTarget = 0; fontPicker = true }
                    3 -> BarsTab(state) { t -> fontTarget = t; fontPicker = true }
                    4 -> ElementsTab(state, player)
                    else -> SettingsTab(state, info)
                }
            }
        }
    }
}
