package com.albaconecta.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object CameraUriFactory {
    private const val DIRECTORY = "contact_photos"

    /**
     * Guarda en filesDir, no en cacheDir: Android puede vaciar el cache y el adjunto
     * de una solicitud pendiente de reintento desaparecería.
     */
    fun create(context: Context): File {
        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create contact photos directory")
        }
        return File.createTempFile("contact_", ".jpg", directory)
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Best-effort: borrar un adjunto no debe romper la pantalla. */
    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
