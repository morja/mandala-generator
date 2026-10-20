package com.mathis.mandala.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Favourite thumbnails: 128 px JPEG in base64, like the macOS app stores them. */
object Thumbnails {
    private const val SIZE = 128
    private const val QUALITY = 85

    fun encode(image: ImageBitmap): String {
        val small = Bitmap.createScaledBitmap(image.asAndroidBitmap(), SIZE, SIZE, true)
        val bytes = ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Null when the data is missing or not an image. */
    fun decode(base64: String): ImageBitmap? = runCatching {
        val bytes = Base64.getMimeDecoder().decode(base64)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
