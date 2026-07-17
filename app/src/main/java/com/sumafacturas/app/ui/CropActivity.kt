package com.sumafacturas.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sumafacturas.app.databinding.ActivityCropBinding
import com.sumafacturas.app.ocr.InvoiceParser
import com.sumafacturas.app.ocr.OcrProcessor
import com.sumafacturas.app.util.ScanSession
import kotlinx.coroutines.launch

/**
 * Permite al usuario recortar la zona de la tabla (Referencia / Transaccion /
 * Valor) y, opcionalmente, marcar manualmente la columna "Valor" para que el
 * OCR no confunda esos numeros con fechas, centros u otros datos de pantalla.
 *
 * Todo el procesamiento (OCR incluido) ocurre en el dispositivo.
 */
class CropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropBinding
    private lateinit var sourceBitmap: Bitmap
    private var imageIndex: Int = 0

    // Escala/offset entre las coordenadas de pantalla y el bitmap original,
    // calculadas una vez la imagen se dibuja con scaleType="fitCenter".
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI) ?: run { finish(); return }
        imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, 0)

        sourceBitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: run {
                Toast.makeText(this, "No se pudo abrir la imagen", Toast.LENGTH_SHORT).show()
                finish(); return
            }

        binding.previewImage.setImageBitmap(sourceBitmap)
        binding.previewImage.post { computeImageMapping() }

        binding.btnSelectValorColumn.setOnClickListener {
            binding.cropOverlay.mode = CropOverlayView.Mode.VALOR_COLUMN
            binding.instructionText.text = getString(com.sumafacturas.app.R.string.btn_select_valor_column) +
                ": dibuja el rectangulo sobre los numeros de la columna Valor"
        }

        binding.btnProcessImage.setOnClickListener { processImage() }
    }

    private fun computeImageMapping() {
        val viewW = binding.previewImage.width.toFloat()
        val viewH = binding.previewImage.height.toFloat()
        val bmW = sourceBitmap.width.toFloat()
        val bmH = sourceBitmap.height.toFloat()

        // fitCenter: la imagen se escala para caber completa, centrada.
        scale = min(viewW / bmW, viewH / bmH)
        offsetX = (viewW - bmW * scale) / 2f
        offsetY = (viewH - bmH * scale) / 2f
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b

    private fun processImage() {
        val tableRectScreen = binding.cropOverlay.selectionInBitmapSpace(scale, offsetX, offsetY)
        val valorRectScreen = binding.cropOverlay.valorColumnInBitmapSpace(scale, offsetX, offsetY)

        val cropRect = tableRectScreen?.let { clampToBitmap(it) }
        val bitmapToAnalyze = if (cropRect != null && cropRect.width() > 10 && cropRect.height() > 10) {
            Bitmap.createBitmap(sourceBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        } else {
            sourceBitmap
        }

        // Si el usuario marco la columna Valor, esa zona tambien debe re-expresarse
        // en las coordenadas del bitmap recortado (no del original).
        val valorRectInCrop = valorRectScreen?.let { vr ->
            val clamped = clampToBitmap(vr)
            if (cropRect != null) {
                Rect(
                    clamped.left - cropRect.left, clamped.top - cropRect.top,
                    clamped.right - cropRect.left, clamped.bottom - cropRect.top
                )
            } else clamped
        }

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnProcessImage.isEnabled = false

        lifecycleScope.launch {
            try {
                val ocrText = OcrProcessor.recognize(bitmapToAnalyze)
                val result = InvoiceParser.parse(ocrText, valorRectInCrop, imageIndex)
                ScanSession.addPartialResult(result)
                Toast.makeText(
                    this@CropActivity,
                    "Se detectaron ${result.facturasDetectadas()} facturas en esta imagen",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CropActivity, "Error al analizar la imagen: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnProcessImage.isEnabled = true
            }
        }
    }

    private fun clampToBitmap(r: Rect): Rect = Rect(
        r.left.coerceIn(0, sourceBitmap.width),
        r.top.coerceIn(0, sourceBitmap.height),
        r.right.coerceIn(0, sourceBitmap.width),
        r.bottom.coerceIn(0, sourceBitmap.height)
    )

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_IMAGE_INDEX = "image_index"
    }
}
