package com.sumafacturas.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Realiza el reconocimiento optico de caracteres (OCR) de forma local en el
 * dispositivo usando ML Kit. La imagen NUNCA sale del telefono ni se envia a
 * ningun servidor: todo el procesamiento ocurre offline.
 */
object OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Ejecuta OCR sobre un bitmap completo (la zona ya recortada por el usuario). */
    suspend fun recognize(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /**
     * Representa una "linea visual" de texto: todos los elementos de OCR cuyo
     * centro vertical cae dentro del mismo rango de Y (misma fila de la tabla),
     * ordenados de izquierda a derecha por su posicion X.
     */
    data class VisualLine(val elements: List<Text.Element>, val boundingBox: Rect)

    /**
     * Agrupa los elementos reconocidos en filas visuales usando su posicion Y,
     * ya que el layout de una tabla de facturacion no siempre produce una
     * Text.Line por fila (el OCR a veces separa columnas en lineas distintas).
     */
    fun groupIntoVisualLines(text: Text, rowToleranceRatio: Double = 0.6): List<VisualLine> {
        val allElements = text.textBlocks.flatMap { block -> block.lines.flatMap { it.elements } }
        if (allElements.isEmpty()) return emptyList()

        val sortedByY = allElements.sortedBy { it.boundingBox?.centerY() ?: 0 }
        val avgHeight = sortedByY.mapNotNull { it.boundingBox?.height() }.average().takeIf { !it.isNaN() } ?: 30.0
        val tolerance = (avgHeight * rowToleranceRatio).toInt().coerceAtLeast(8)

        val rows = mutableListOf<MutableList<Text.Element>>()
        for (el in sortedByY) {
            val y = el.boundingBox?.centerY() ?: continue
            val row = rows.lastOrNull()?.let { r ->
                val refY = r.first().boundingBox?.centerY() ?: return@let null
                if (kotlin.math.abs(y - refY) <= tolerance) r else null
            }
            if (row != null) {
                row.add(el)
            } else {
                rows.add(mutableListOf(el))
            }
        }

        return rows.map { row ->
            val sorted = row.sortedBy { it.boundingBox?.centerX() ?: 0 }
            val box = sorted.mapNotNull { it.boundingBox }.reduce { a, b -> Rect(a).apply { union(b) } }
            VisualLine(sorted, box)
        }.sortedBy { it.boundingBox.centerY() }
    }
}
