package com.sumafacturas.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sumafacturas.app.databinding.ActivityReviewBinding
import com.sumafacturas.app.model.InvoiceRow
import com.sumafacturas.app.model.ScanResult
import com.sumafacturas.app.util.ScanSession

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var adapter: ReviewAdapter
    private lateinit var rows: MutableList<InvoiceRow>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isManual = intent.getBooleanExtra(MainActivity.EXTRA_MANUAL, false)
        val result: ScanResult = if (isManual) ScanResult() else (ScanSession.finalResult ?: ScanSession.computeFinal())

        rows = result.filas
        if (isManual && rows.isEmpty()) {
            rows.add(InvoiceRow(tipoTransaccion = "FACTURA", referencia = null, valor = null))
        }

        result.advertenciaConteo?.let {
            binding.warningText.visibility = android.view.View.VISIBLE
            binding.warningText.text = it
        }

        adapter = ReviewAdapter(rows) { updateTotals(result) }
        binding.rowsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.rowsRecyclerView.adapter = adapter

        updateTotals(result)

        binding.btnConfirmResults.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
    }

    private fun updateTotals(result: ScanResult) {
        binding.totalAutoText.text = "Total detectado automaticamente: $%.2f".format(result.totalDetectadoAutomatico())
        binding.totalRevisadoText.text = "Total despues de la revision: $%.2f".format(result.totalDespuesRevision())
    }
}
