package com.mathis.mandala.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.mathis.mandala.core.Encoding
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.ImageLoader
import java.io.File

/**
 * Background pictures: a picked photo is copied into the app's files (at most [MAX_SIDE] pixels a
 * side) and referenced by a `file:` URL, like the macOS app stores its image path.
 */
object BackgroundImages {
    private const val MAX_SIDE = 2048

    /** Copies the picked picture and returns its `file:` URL. */
    fun import(context: Context, uri: Uri): String {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) decoder.setTargetSampleSize((side + MAX_SIDE - 1) / MAX_SIDE)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
        // One picture at a time; a new name per pick so the settings change and the preview renders.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "background-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "Could not store the picture." } }
        return file.toURI().toString()
    }
}

/** Loads `file:` pictures stretched to the square, keeping the last one, since every preview asks again. */
class AndroidImageLoader : ImageLoader {
    private var cached: Triple<String, Int, Image8>? = null

    @Synchronized
    override fun load(url: String, size: Int): Image8? {
        cached?.let { (u, s, img) -> if (u == url && s == size) return img }
        val path = runCatching { File(java.net.URI(url)).path }.getOrNull() ?: return null
        // Decode at the smallest power-of-two reduction that still covers the requested size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val out = Image8(size, size, Encoding.SRGB)
        for (p in pixels.indices) {
            val c = pixels[p]
            out.data[p * 3] = (c shr 16).toByte()
            out.data[p * 3 + 1] = (c shr 8).toByte()
            out.data[p * 3 + 2] = c.toByte()
        }
        cached = Triple(url, size, out)
        return out
    }
}
