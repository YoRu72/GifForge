package com.gifforge.app.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private fun uriFor(ctx: Context, file: File) =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

fun shareGif(ctx: Context, file: File) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/gif"
        putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share GIF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openGif(ctx: Context, file: File) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriFor(ctx, file), "image/gif")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { ctx.startActivity(view) } catch (_: Exception) { /* no viewer installed */ }
}
