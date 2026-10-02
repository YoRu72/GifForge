package com.gifforge.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.gifforge.app.media.openGif
import com.gifforge.app.media.shareGif
import java.io.File

data class ExportResult(val file: File, val savedToGallery: Boolean, val width: Int, val height: Int)

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GIF ready") },
        text = {
            Column {
                AsyncImage(
                    model = r.file,
                    imageLoader = loader,
                    contentDescription = "Exported GIF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("${r.width}x${r.height} - ${formatSize(r.file.length())}")
                Text(if (r.savedToGallery) "Saved to Pictures/GifForge" else "Could not save to gallery - use Share")
            }
        },
        confirmButton = { Button(onClick = { shareGif(ctx, r.file) }) { Text("Share") } },
        dismissButton = {
            Row {
                TextButton(onClick = { openGif(ctx, r.file) }) { Text("Open") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
