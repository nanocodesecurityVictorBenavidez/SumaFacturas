package com.sumafacturas.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    /** Crea un archivo temporal en la cache local del telefono para guardar la foto tomada. */
    fun createCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val name = "captura_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Limpia las imagenes temporales al terminar un escaneo (no queda nada en el dispositivo). */
    fun clearCache(context: Context) {
        File(context.cacheDir, "images").listFiles()?.forEach { it.delete() }
    }
}
