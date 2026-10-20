package com.mediaforge.app.ui

import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mediaforge.app.media.saveGif
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.mediaforge.app.media.openGif
import com.mediaforge.app.media.shareGif
import java.io.File

data class ExportResult(val file: File, val savedToGallery: Boolean, val width: Int, val height: Int, val video: Boolean = false, val withAudio: Boolean = false)

/** Coil loader that can play animated GIFs on every supported Android version. */
@Composable
fun rememberGifLoader(): ImageLoader {
    val ctx = LocalContext.current
    return remember {
        ImageLoader.Builder(ctx).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
    else -> "$bytes B"
}

@Composable
fun GifResultDialog(r: ExportResult, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberGifLoader()
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(r.savedToGallery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rs_ready)) },
        text = {
            Column {
                if (!r.video) AsyncImage(
                    model = r.file,
                    imageLoader = loader,
                    contentDescription = stringResource(R.string.rs_desc),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) else AsyncImage(
                    model = coil.request.ImageRequest.Builder(ctx).data(r.file).decoderFactory(coil.decode.VideoFrameDecoder.Factory()).build(),
                    contentDescription = stringResource(R.string.rs_desc), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("${r.width}x${r.height} - ${formatSize(r.file.length())}" + if (r.video) " - " + stringResource(if (r.withAudio) R.string.rs_with_audio else R.string.rs_no_audio) else "")
                Text(if (saved) stringResource(R.string.rs_saved) else stringResource(R.string.rs_not_saved))
            }
        },
        confirmButton = { Button(onClick = { if (r.video) com.mediaforge.app.media.shareFile(ctx, r.file, "video/mp4") else shareGif(ctx, r.file) }) { Text(stringResource(R.string.share)) } },
        dismissButton = {
            Row {
                if (!saved) TextButton(onClick = { scope.launch { saved = (if (r.video) com.mediaforge.app.media.saveVideo(ctx, r.file) else saveGif(ctx, r.file)) != null } }) { Text(stringResource(R.string.save)) }
                TextButton(onClick = { if (r.video) com.mediaforge.app.media.openFile(ctx, r.file, "video/mp4") else openGif(ctx, r.file) }) { Text(stringResource(R.string.open)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}
