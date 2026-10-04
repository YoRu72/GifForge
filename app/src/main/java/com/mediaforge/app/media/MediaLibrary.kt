package com.mediaforge.app.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class LibraryItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val sizeBytes: Long,
    val addedSec: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucket: String,
    val path: String,
    val modifiedSec: Long,
) {
    val nameLower: String = name.lowercase()
    val bucketLower: String = bucket.lowercase()
    /** Date used for filtering/sorting: modified time, falling back to added time. */
    val dateSec: Long get() = if (modifiedSec > 0) modifiedSec else addedSec
    /** Folder this file lives in (full path). */
    val parentPath: String = if (path.contains('/')) path.substringBeforeLast('/') else "/Other/$bucket"
}

data class LibraryFolder(val id: Long, val name: String, val items: List<LibraryItem>) {
    val videos: Int get() = items.count { it.isVideo }
    val gifs: Int get() = items.size - videos
}

fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasMediaAccess(ctx: Context): Boolean = requiredMediaPermissions().any {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

object MediaLibrary {
    /** Two narrow MediaStore queries (videos, GIFs only), run in parallel off the main thread. */
    suspend fun load(ctx: Context): List<LibraryItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val videos = async { query(ctx, isVideo = true) }
            val gifs = async { query(ctx, isVideo = false) }
            videos.await() + gifs.await()
        }
    }

    private fun query(ctx: Context, isVideo: Boolean): List<LibraryItem> {
        val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        if (isVideo) cols += MediaStore.Video.VideoColumns.DURATION

        val selection = if (isVideo) null else "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = if (isVideo) null else arrayOf("image/gif")

        val out = ArrayList<LibraryItem>()
        ctx.contentResolver.query(
            base, cols.toTypedArray(), selection, args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            out.ensureCapacity(c.count)
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iBid = c.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
            val iBname = c.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iDur = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
            while (c.moveToNext()) {
                out += LibraryItem(
                    uri = ContentUris.withAppendedId(base, c.getLong(iId)),
                    name = c.getString(iName) ?: "Unnamed",
                    isVideo = isVideo,
                    durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                    sizeBytes = c.getLong(iSize),
                    addedSec = c.getLong(iDate),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    bucketId = c.getLong(iBid),
                    bucket = c.getString(iBname) ?: "Other",
                    path = c.getString(iData) ?: "",
                    modifiedSec = c.getLong(iMod),
                )
            }
        }
        return out
    }
}
