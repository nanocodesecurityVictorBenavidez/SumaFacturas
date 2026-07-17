package com.sumafacturas.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sumafacturas.app.databinding.ActivityResultsBinding
import com.sumafacturas.app.util.ScanSession

class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = ScanSession.finalResult ?: ScanSession.computeFinal()
        val facturaRows = result.filas.filter { it.esFactura }
        val primera = facturaRows.mapNotNull { it.referencia }.minOrNull()
        val ultima = facturaRows.mapNotNull { it.referencia }.maxOrNull()

        val sb = StringBuilder()
        sb.appendLine("RESULTADO DEL ESCANEO")
        sb.appendLine()
        sb.appendLine("Facturas detectadas: ${result.facturasDetectadas()}")
        sb.appendLine("Facturas seleccionadas: ${result.facturasSeleccionadas()}")
        sb.appendLine("Total facturado: $%.2f".format(result.totalDespuesRevision()))
        sb.appendLine()
        if (primera != null) sb.appendLine("Primera factura: $primera")
        if (ultima != null) sb.appendLine("Ultima factura: $ultima")
        sb.appendLine("Filas con posible error: ${result.filasConError()}")

        binding.resultsSummaryText.text = sb.toString()

        binding.btnNewScan.setOnClickListener {
            ScanSession.reset()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
    }
}
