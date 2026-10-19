package com.mathis.mandala.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.mathis.mandala.core.Image8

/** Writes images to the shared gallery (Pictures/Mandala). Needs no permission from Android 10 on. */
object GalleryExport {
    const val FOLDER = "Mandala"

    fun savePng(context: Context, image: Image8, fileName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("The gallery did not accept a new image.")
        try {
            val bitmap = image.toBitmap()
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "Cannot write to the gallery." }
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not encode the PNG." }
            }
            bitmap.recycle()
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}

fun Image8.toBitmap(): Bitmap = Bitmap.createBitmap(toArgbPixels(), width, height, Bitmap.Config.ARGB_8888)
