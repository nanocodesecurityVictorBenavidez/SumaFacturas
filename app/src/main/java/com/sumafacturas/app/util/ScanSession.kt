package com.sumafacturas.app.util

import com.sumafacturas.app.model.ScanResult
import com.sumafacturas.app.ocr.ScanMerger

/**
 * Guarda en memoria los resultados parciales de cada imagen procesada durante
 * la sesion actual. No hay persistencia en disco ni envio a ningun servidor:
 * todo vive unicamente mientras la app esta abierta.
 */
object ScanSession {
    private val partialResults = mutableListOf<ScanResult>()

    var finalResult: ScanResult? = null
        private set

    fun addPartialResult(result: ScanResult) {
        partialResults.add(result)
    }

    fun imageCount(): Int = partialResults.size

    fun computeFinal(): ScanResult {
        val merged = ScanMerger.merge(partialResults)
        finalResult = merged
        return merged
    }

    fun reset() {
        partialResults.clear()
        finalResult = null
    }
}
