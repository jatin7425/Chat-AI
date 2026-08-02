package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Downscales + JPEG-compresses a picked image before it's uploaded to the backend, so persona photos don't ship at full camera resolution over a mobile connection. */
fun compressImageToJpegBytes(context: Context, uri: Uri, maxDimension: Int, maxBytes: Int): ByteArray? {
    val input = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val decoded = BitmapFactory.decodeByteArray(input, 0, input.size) ?: return null
    val scale = minOf(1f, maxDimension.toFloat() / maxOf(decoded.width, decoded.height))
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else decoded

    var quality = 90
    var output = ByteArrayOutputStream()
    do {
        output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        quality -= 15
    } while (output.size() > maxBytes && quality > 10)

    return output.toByteArray()
}
