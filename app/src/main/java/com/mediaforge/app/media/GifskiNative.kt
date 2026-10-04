package com.mediaforge.app.media

/** Thin JNI wrapper around the Rust gifski encoder (see /native). */
object GifskiNative {
    init { System.loadLibrary("mediaforge_native") }

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
        quality: Int, fast: Boolean, startFrame: Int, endFrame: Int,
    ): String?

    /** Converts a WOFF2 web font to a plain TTF/OTF file. */
    external fun nativeWoff2ToSfnt(inPath: String, outPath: String): Boolean

    /** Per-frame delays in centiseconds (browser-style: 0/1 become 10), or null. */
    external fun nativeGifDelays(path: String): IntArray?

    /** Full-canvas RGBA bytes (w*h*4) of one frame after compositing, or null. */
    external fun nativeGifFrame(path: String, index: Int): ByteArray?

    /** Sequential GIF reader: open, call next until null, then close. Frames are full-canvas RGBA. */
    external fun nativeGifOpen(path: String): Long
    external fun nativeGifNext(handle: Long): ByteArray?
    external fun nativeGifClose(handle: Long)

    external fun nativeCropProgress(): Int
    external fun nativeCancelCrop()
}
