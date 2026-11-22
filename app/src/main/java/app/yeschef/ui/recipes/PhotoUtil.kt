package app.yeschef.ui.recipes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A fresh file the camera app can write the photo into, shared via our FileProvider. */
fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() } // only ever keep the latest
    val file = File(dir, "recipe-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
}

/**
 * Decodes a photo, scales it so the long side is at most [maxSide] px (phone photos
 * are huge and Mealie only shows them at card size), applies EXIF rotation, and
 * re-encodes it as JPEG.
 */
suspend fun preparePhoto(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray = withContext(Dispatchers.IO) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = maxSide.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
