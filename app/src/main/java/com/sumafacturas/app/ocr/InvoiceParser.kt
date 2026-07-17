package com.sumafacturas.app.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import com.sumafacturas.app.model.InvoiceRow
import com.sumafacturas.app.model.RowStatus
import com.sumafacturas.app.model.ScanResult

/**
 * Convierte el resultado crudo de OCR en filas de factura estructuradas.
 *
 * Reglas de clasificacion (ver especificacion):
 *  - Fecha:       yyyy-mm-dd                         -> 2026-07-11
 *  - Referencia:  ddd-ddd-ddddddddd (3-3-9 digitos)   -> 006-901-000056813
 *  - Valor:       numero decimal 1-2 decimales        -> 3.81
 *  - Centro:      numero entero corto (3-5 digitos)   -> 4121
 *
 * Solo se suman los numeros que caen, por posicion horizontal, debajo del
 * encabezado "Valor". Esto evita sumar fechas, centros, numeros de
 * establecimiento o cualquier otro dato de la pantalla.
 */
object InvoiceParser {

    private val REGEX_FECHA = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    private val REGEX_REFERENCIA = Regex("""\b\d{3}-\d{3}-\d{9}\b""")
    private val REGEX_VALOR = Regex("""^\$?\s?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}$""")
    private val REGEX_CENTRO = Regex("""^\d{3,5}$""")
    private val PALABRA_FACTURA = Regex("""FACTURA""", RegexOption.IGNORE_CASE)

    private val HEADER_REFERENCIA = Regex("""REFERENCIA""", RegexOption.IGNORE_CASE)
    private val HEADER_TRANSACCION = Regex("""TRANSACCI[OÓ]N""", RegexOption.IGNORE_CASE)
    private val HEADER_VALOR = Regex("""VALOR""", RegexOption.IGNORE_CASE)

    data class ColumnBounds(val referencia: Rect?, val transaccion: Rect?, val valor: Rect?)

    /**
     * Busca la fila de encabezados (Referencia / Transaccion / Valor) dentro
     * de las lineas visuales y devuelve el rango X aproximado de cada columna.
     * Si el usuario ya marco manualmente la columna "Valor" (valorColumnOverride),
     * esa zona tiene prioridad sobre la deteccion automatica del encabezado.
     */
    fun detectColumns(lines: List<OcrProcessor.VisualLine>, valorColumnOverride: Rect?): ColumnBounds {
        var refBox: Rect? = null
        var transBox: Rect? = null
        var valorBox: Rect? = null

        for (line in lines) {
            for (el in line.elements) {
                val t = el.text
                val box = el.boundingBox ?: continue
                if (HEADER_REFERENCIA.containsMatchIn(t)) refBox = box
                if (HEADER_TRANSACCION.containsMatchIn(t)) transBox = box
                if (HEADER_VALOR.containsMatchIn(t)) valorBox = box
            }
        }

        // La columna "Valor" se extiende verticalmente hacia abajo desde el encabezado.
        val valorColumn = valorColumnOverride ?: valorBox?.let {
            Rect(it.left - 15, it.top, it.right + 40, Int.MAX_VALUE / 2)
        }

        return ColumnBounds(refBox, transBox, valorColumn)
    }

    /**
     * Construye las filas de factura a partir de las lineas visuales agrupadas.
     * @param valorColumnOverride zona X definida manualmente por el usuario con
     *        "Seleccionar columna Valor" (tiene prioridad sobre el encabezado detectado).
     * @param sourceImageIndex indice de la imagen de origen (para el merge multi-foto).
     */
    fun parse(
        text: Text,
        valorColumnOverride: Rect? = null,
        sourceImageIndex: Int = 0
    ): ScanResult {
        val lines = OcrProcessor.groupIntoVisualLines(text)
        val columns = detectColumns(lines, valorColumnOverride)

        val rows = mutableListOf<InvoiceRow>()

        // Se ignora la fila de encabezados y cualquier linea previa a ella.
        val headerY = columns.valor?.top ?: columns.transaccion?.top ?: columns.referencia?.top ?: -1
        val dataLines = if (headerY >= 0) lines.filter { it.boundingBox.centerY() > headerY } else lines

        for (line in dataLines) {
            val lineText = line.elements.joinToString(" ") { it.text }

            val esFactura = PALABRA_FACTURA.containsMatchIn(lineText)
            val fecha = REGEX_FECHA.find(lineText)?.value
            val referencia = REGEX_REFERENCIA.find(lineText)?.value

            // Centro: numero corto que NO esta dentro de la columna Valor.
            val centro = line.elements.firstOrNull { el ->
                REGEX_CENTRO.matches(el.text) && !isInsideColumn(el.boundingBox, columns.valor)
            }?.text

            // Valor: solo se aceptan tokens que (a) tienen forma de numero decimal Y
            // (b) caen dentro de la columna Valor detectada/seleccionada.
            val valorElementos = line.elements.filter { el ->
                looksLikeMoney(el.text) && isInsideColumn(el.boundingBox, columns.valor)
            }

            // Si no hay columna Valor detectada todavia (imagen sin encabezado visible,
            // p.ej. una foto recortada solo con filas), se cae de vuelta a "el ultimo
            // numero con forma decimal de la linea", que suele ser el valor en la mayoria
            // de sistemas de facturacion.
            val valorTexto = valorElementos.lastOrNull()?.text
                ?: (if (columns.valor == null) line.elements.lastOrNull { looksLikeMoney(it.text) }?.text else null)

            val valor = valorTexto?.let { parseMoney(it) }

            // Solo nos interesan lineas que realmente parecen una fila de datos:
            // deben tener referencia O valor O la palabra FACTURA.
            if (referencia == null && valor == null && !esFactura) continue

            val status = when {
                esFactura && valor == null -> RowStatus.REVISAR   // fila FACTURA sin valor legible
                esFactura && referencia == null -> RowStatus.REVISAR // fila FACTURA sin referencia legible
                else -> RowStatus.CORRECTO
            }

            rows.add(
                InvoiceRow(
                    tipoTransaccion = if (esFactura) "FACTURA" else (extractTipoTransaccion(lineText) ?: "OTRO"),
                    referencia = referencia,
                    valor = valor,
                    fecha = fecha,
                    centro = centro,
                    lineaOriginal = lineText,
                    status = status,
                    esFactura = esFactura,
                    sourceImageIndex = sourceImageIndex
                )
            )
        }

        val result = ScanResult(rows)

        val filasFactura = result.facturasDetectadas()
        val refsUnicas = result.referenciasUnicas()
        if (filasFactura != refsUnicas && refsUnicas > 0) {
            result.advertenciaConteo =
                "Se detectaron $filasFactura filas de factura.\n" +
                "Se reconocieron $refsUnicas referencias unicas.\n" +
                "Revise las filas marcadas."
        }

        return result
    }

    private fun looksLikeMoney(t: String): Boolean = REGEX_VALOR.matches(t.trim())

    private fun parseMoney(t: String): Double? =
        t.trim().removePrefix("$").trim().replace(",", "").toDoubleOrNull()

    private fun isInsideColumn(box: Rect?, column: Rect?): Boolean {
        if (box == null || column == null) return false
        val cx = box.centerX()
        return cx in column.left..column.right
    }

    private fun extractTipoTransaccion(lineText: String): String? {
        val known = listOf("NOTA DE CREDITO", "NOTA CREDITO", "ANULADO", "DEVOLUCION")
        return known.firstOrNull { lineText.uppercase().contains(it) }
    }
}
