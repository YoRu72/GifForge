package com.gifforge.app.media

/** Thin JNI wrapper around the Rust gifski encoder (see /native). */
object GifskiNative {
    init { System.loadLibrary("gifforge_native") }

    external fun nativeStart(
        path: String, width: Int, height: Int, quality: Int,
        fast: Boolean, repeatInfinite: Boolean,
    ): Long

    external fun nativeAddFrame(
        handle: Long, index: Int, rgba: ByteArray, w: Int, h: Int, pts: Double,
    ): Boolean

    external fun nativeFinish(handle: Long): Boolean
    external fun nativeCancel(handle: Long)

    /** [width, height, frameCount] or null if the file isn't a readable GIF. */
    external fun nativeGifInfo(path: String): IntArray?

    /** Returns null on success, otherwise an error message. */
    external fun nativeCropGif(
        inPath: String, outPath: String, x: Int, y: Int, w: Int, h: Int,
        quality: Int, fast: Boolean,
    ): String?

    external fun nativeCropProgress(): Int
    external fun nativeCancelCrop()
}
