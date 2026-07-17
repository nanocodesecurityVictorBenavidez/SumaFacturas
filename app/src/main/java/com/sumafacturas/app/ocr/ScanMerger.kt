package com.sumafacturas.app.ocr

import com.sumafacturas.app.model.InvoiceRow
import com.sumafacturas.app.model.RowStatus
import com.sumafacturas.app.model.ScanResult

/**
 * Combina los resultados de varias fotos/capturas de la misma pantalla de
 * facturacion (util cuando la lista tiene mas filas de las que caben en una
 * sola foto) y elimina las facturas repetidas entre imagenes.
 *
 * Dos filas se consideran la MISMA factura si tienen la misma Referencia.
 * Si la referencia coincide pero Valor o Fecha difieren, no se descarta
 * automaticamente: se marca para revision manual, tal como pide la
 * especificacion ("Si una misma referencia aparece con valores diferentes,
 * debe solicitar revision manual").
 */
object ScanMerger {

    fun merge(results: List<ScanResult>): ScanResult {
        val merged = mutableListOf<InvoiceRow>()
        // referencia -> fila ya agregada (para detectar duplicados/conflictos)
        val seenByReference = LinkedHashMap<String, InvoiceRow>()

        for (result in results) {
            for (row in result.filas) {
                val ref = row.referencia
                if (ref == null) {
                    // Sin referencia no se puede deduplicar; se conserva tal cual.
                    merged.add(row)
                    continue
                }

                val existing = seenByReference[ref]
                if (existing == null) {
                    seenByReference[ref] = row
                    merged.add(row)
                } else {
                    val mismatch = !valuesMatch(existing.valor, row.valor) || !datesMatch(existing.fecha, row.fecha)
                    if (mismatch) {
                        // Conflicto real: misma referencia, datos distintos -> revisar ambas.
                        existing.status = RowStatus.REVISAR
                        row.status = RowStatus.REVISAR
                        merged.add(row)
                    }
                    // Si coinciden exactamente, es un duplicado legitimo entre fotos
                    // solapadas: se descarta silenciosamente (no se suma dos veces).
                }
            }
        }

        val combined = ScanResult(merged)
        val filasFactura = combined.facturasDetectadas()
        val refsUnicas = combined.referenciasUnicas()
        if (filasFactura != refsUnicas && refsUnicas > 0) {
            combined.advertenciaConteo =
                "Se detectaron $filasFactura filas de factura.\n" +
                "Se reconocieron $refsUnicas referencias unicas.\n" +
                "Revise las filas marcadas."
        }
        return combined
    }

    private fun valuesMatch(a: Double?, b: Double?): Boolean {
        if (a == null || b == null) return true // dato faltante no cuenta como conflicto
        return kotlin.math.abs(a - b) < 0.005
    }

    private fun datesMatch(a: String?, b: String?): Boolean {
        if (a == null || b == null) return true
        return a == b
    }
}
