package com.sumafacturas.app.model

import java.util.UUID

enum class RowStatus { CORRECTO, REVISAR }

/**
 * Representa una fila detectada en la tabla del sistema de facturacion,
 * reconstruida unicamente a partir de texto leido por OCR sobre una imagen.
 */
data class InvoiceRow(
    val id: String = UUID.randomUUID().toString(),
    var tipoTransaccion: String,      // ej. "FACTURA", "NOTA CREDITO", etc.
    var referencia: String?,          // ej. 006-901-000056813
    var valor: Double?,               // ej. 3.81
    var fecha: String? = null,        // ej. 2026-07-11
    var centro: String? = null,       // ej. 4121
    var lineaOriginal: String = "",   // texto crudo de la linea (auditoria)
    var status: RowStatus = RowStatus.CORRECTO,
    var esFactura: Boolean = true,
    var sourceImageIndex: Int = 0     // de que foto/imagen provino esta fila
) {
    /** true si la fila cuenta para el conteo/suma final */
    fun cuentaParaTotal(): Boolean = esFactura && valor != null && status != RowStatus.REVISAR

    fun formatoValor(): String = valor?.let { "%.2f".format(it) } ?: "--"
}

/** Resultado agregado de uno o varios escaneos ya combinados y deduplicados. */
data class ScanResult(
    val filas: MutableList<InvoiceRow> = mutableListOf(),
    var advertenciaConteo: String? = null
) {
    fun facturasDetectadas(): Int = filas.count { it.esFactura }
    fun facturasSeleccionadas(): Int = filas.count { it.cuentaParaTotal() }
    fun totalDetectadoAutomatico(): Double =
        filas.filter { it.esFactura && it.status != RowStatus.REVISAR }.sumOf { it.valor ?: 0.0 }
    fun totalDespuesRevision(): Double =
        filas.filter { it.esFactura }.sumOf { it.valor ?: 0.0 }
    fun filasConError(): Int = filas.count { it.status == RowStatus.REVISAR }
    fun referenciasUnicas(): Int = filas.mapNotNull { it.referencia }.distinct().size
}
