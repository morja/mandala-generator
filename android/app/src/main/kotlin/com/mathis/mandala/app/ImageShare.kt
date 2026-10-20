package com.mathis.mandala.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.mathis.mandala.core.Image8
import java.io.File

/** Writes an image to the app's cache and returns a URI other apps may read (via the share sheet). */
object ImageShare {
    private const val FOLDER = "shared"

    fun prepare(context: Context, image: Image8, fileName: String): Uri {
        val dir = File(context.cacheDir, FOLDER).apply { mkdirs() }
        // Only the newest shared image is kept.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        val bitmap = image.toBitmap()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not encode the PNG." } }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
