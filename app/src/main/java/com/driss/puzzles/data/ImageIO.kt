package com.driss.puzzles.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageIO {
    /** Long side of the bitmap a puzzle is cut from. Keeps piece bitmaps well inside the heap. */
    const val PUZZLE_MAX_SIDE = 1600
    /** Long side used while cropping, so the crop still has detail to work with. */
    const val CROP_MAX_SIDE = 2400

    fun decodeStream(open: () -> InputStream, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not decode image")
        val exif = runCatching { open().use { ExifInterface(it) } }.getOrNull()
        return scaleDown(applyExif(decoded, exif), maxSide)
    }

    fun decodeUri(context: Context, uri: Uri, maxSide: Int): Bitmap =
        decodeStream({ context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri") }, maxSide)

    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxSide) return bitmap
        val s = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * s).roundToInt().coerceAtLeast(1),
            (bitmap.height * s).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun applyExif(bitmap: Bitmap, exif: ExifInterface?): Bitmap {
        val degrees = exif?.rotationDegrees ?: 0
        val flipped = exif?.isFlipped ?: false
        if (degrees == 0 && !flipped) return bitmap
        val matrix = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** A content:// uri the camera app can write a new photo to. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
