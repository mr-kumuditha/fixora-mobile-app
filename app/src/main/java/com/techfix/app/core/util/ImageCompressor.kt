package com.techfix.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Client-side compression for repair-request photos before they go to
 * Supabase Storage — downscales to a max edge and re-encodes as JPEG, since
 * both camera captures and gallery picks can otherwise be several MB each.
 */
object ImageCompressor {
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    fun compress(context: Context, sourceUri: Uri): File {
        UploadDiagnostics.debug(
            "Compression started. URI=$sourceUri scheme=${sourceUri.scheme} " +
                "mimeType=${context.contentResolver.getType(sourceUri)}",
        )
        val outFile = File(context.cacheDir, "repair_photo_${UUID.randomUUID()}.jpg")
        var bitmap: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val decoded = decode(context, sourceUri)
            bitmap = decoded.bitmap
            val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }

            val encoded = FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            check(encoded) { "JPEG encoder rejected the image" }
            check(outFile.isFile && outFile.canRead() && outFile.length() > 0L) {
                "Compressed image file is empty or unreadable"
            }
            UploadDiagnostics.debug(
                "Compression succeeded. decoder=${decoded.decoder} " +
                    "sourceDimensions=${decoded.sourceWidth}x${decoded.sourceHeight} " +
                    "decodedDimensions=${bitmap.width}x${bitmap.height} " +
                    "compressedPath=${outFile.absolutePath} compressedSize=${outFile.length()}",
            )
            return outFile
        } catch (exception: Exception) {
            outFile.delete()
            UploadDiagnostics.error("Compression failed. URI=$sourceUri", exception)
            throw exception
        } finally {
            if (scaled != null && scaled !== bitmap) bitmap?.recycle()
            scaled?.recycle()
        }
    }

    private fun decode(context: Context, sourceUri: Uri): DecodedImage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, sourceUri)
        } else {
            decodeWithFileDescriptor(context, sourceUri)
        }

    /**
     * Photo Picker providers are not required to return a normal InputStream.
     * ImageDecoder understands modern content providers (including Android's
     * backported Photo Picker) and also applies EXIF orientation correctly.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, sourceUri: Uri): DecodedImage {
        var sourceWidth = 0
        var sourceHeight = 0
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            sourceWidth = info.size.width
            sourceHeight = info.size.height
            check(sourceWidth > 0 && sourceHeight > 0) { "Unsupported image dimensions" }

            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(
                1f,
                MAX_DIMENSION.toFloat() / maxOf(sourceWidth, sourceHeight),
            )
            if (scale < 1f) {
                decoder.setTargetSize(
                    (sourceWidth * scale).toInt().coerceAtLeast(1),
                    (sourceHeight * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        return DecodedImage(bitmap, sourceWidth, sourceHeight, "ImageDecoder")
    }

    private fun decodeWithFileDescriptor(context: Context, sourceUri: Uri): DecodedImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsDescriptor = resolver.openFileDescriptor(sourceUri, "r")
            ?: error("Image is unavailable")
        boundsDescriptor.use { descriptor ->
            // A bounds-only decode returns null by contract; dimensions in
            // [bounds] determine whether the source was decoded successfully.
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        } ?: error("Could not decode image")
        return DecodedImage(bitmap, bounds.outWidth, bounds.outHeight, "BitmapFactory")
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        return sample
    }

    private data class DecodedImage(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val decoder: String,
    )
}
