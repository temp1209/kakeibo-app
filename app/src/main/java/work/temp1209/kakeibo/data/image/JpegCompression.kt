package work.temp1209.kakeibo.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

fun resizeJpegLongSide(inputFile: File, maxLongSidePx: Int): Bitmap {
    val rotationDegrees = runCatching { readExifRotationDegrees(inputFile) }.getOrElse { 0 }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
    val srcW = bounds.outWidth.coerceAtLeast(1)
    val srcH = bounds.outHeight.coerceAtLeast(1)
    val (effectiveW, effectiveH) =
        if (rotationDegrees == 90 || rotationDegrees == 270) srcH to srcW else srcW to srcH
    val longSide = maxOf(effectiveW, effectiveH)
    val scale = if (longSide > maxLongSidePx) maxLongSidePx.toFloat() / longSide.toFloat() else 1f
    val dstW = (effectiveW * scale).toInt().coerceAtLeast(1)
    val dstH = (effectiveH * scale).toInt().coerceAtLeast(1)

    val decodeOpts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOpts)
        ?: throw IllegalStateException("Failed to decode JPEG")

    val rotated = if (rotationDegrees == 0) {
        decoded
    } else {
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        ).also { decoded.recycle() }
    }

    return if (scale == 1f) rotated else Bitmap.createScaledBitmap(rotated, dstW, dstH, true).also { rotated.recycle() }
}

fun saveJpeg(bitmap: Bitmap, outFile: File, quality: Int) {
    FileOutputStream(outFile).use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IllegalStateException("Failed to compress JPEG")
        }
    }
    bitmap.recycle()
}

private fun readExifRotationDegrees(file: File): Int {
    val exif = ExifInterface(file)
    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}

