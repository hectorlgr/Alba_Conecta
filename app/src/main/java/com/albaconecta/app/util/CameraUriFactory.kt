package com.albaconecta.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object CameraUriFactory {
    fun create(context: Context): Uri {
        val directory = File(context.cacheDir, "contact_photos")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create camera cache directory")
        }
        val file = File.createTempFile("contact_", ".jpg", directory)
        try {
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
    }

    fun delete(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // Best-effort cleanup of a temporary cache file.
        }
    }
}
