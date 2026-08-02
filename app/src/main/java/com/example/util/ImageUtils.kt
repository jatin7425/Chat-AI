package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * Reads an image from the given Uri, resizes it if needed, and compresses it into a JPEG ByteArray (BLOB).
     * Capping dimensions ensures fast database retrieval and prevents SQLite Cursor limit issues.
     */
    fun uriToCompressedBlob(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 85
    ): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
                val width = originalBitmap.width
                val height = originalBitmap.height
                if (width <= 0 || height <= 0) return null

                val maxDim = maxOf(width, height)
                val scale = if (maxDim > maxDimension) maxDimension.toFloat() / maxDim else 1.0f
                val targetWidth = (width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (height * scale).toInt().coerceAtLeast(1)

                val resizedBitmap = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                } else {
                    originalBitmap
                }

                ByteArrayOutputStream().use { outputStream ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    outputStream.toByteArray()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
