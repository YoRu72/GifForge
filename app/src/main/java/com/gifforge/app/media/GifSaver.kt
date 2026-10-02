package com.gifforge.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves a GIF to Pictures/GifForge (MediaStore on Android 10+, app folder before that). */
suspend fun saveGif(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
    val name = "GifForge_${System.currentTimeMillis()}.gif"
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GifForge")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return@withContext null
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        }
    } catch (e: Exception) {
        null
    }
}
