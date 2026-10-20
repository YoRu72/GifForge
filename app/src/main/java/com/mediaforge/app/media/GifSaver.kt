package com.mediaforge.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves a GIF to Pictures/MediaForge (MediaStore on Android 10+, app folder before that). */
suspend fun saveGif(context: Context, file: File): Uri? = withContext(Dispatchers.IO) {
    val name = "${com.mediaforge.app.Prefs.safePrefix()}${System.currentTimeMillis()}.gif"
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaForge")
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

/** AEGX4: saves a PNG (a video frame, with or without the subtitle drawn) next to the GIFs in Pictures/MediaForge. */
suspend fun savePng(context: Context, bmp: android.graphics.Bitmap): Uri? = withContext(Dispatchers.IO) {
    val name = "${com.mediaforge.app.Prefs.safePrefix()}${System.currentTimeMillis()}.png"
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MediaForge")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return@withContext null
            val dest = File(dir, name)
            dest.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Uri.fromFile(dest)
        }
    } catch (e: Exception) { null }
}
