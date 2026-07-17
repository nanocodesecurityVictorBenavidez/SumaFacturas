package com.sumafacturas.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sumafacturas.app.databinding.ItemInvoiceRowBinding
import com.sumafacturas.app.model.InvoiceRow
import com.sumafacturas.app.model.RowStatus

/**
 * Muestra cada fila detectada como editable, resaltando en rojo las filas
 * marcadas para revision (posibles errores de OCR), tal como pide la
 * especificacion.
 */
class ReviewAdapter(
    private val rows: MutableList<InvoiceRow>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<ReviewAdapter.RowViewHolder>() {

    inner class RowViewHolder(val binding: ItemInvoiceRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemInvoiceRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val row = rows[position]
        val b = holder.binding

        b.statusDot.setBackgroundColor(
            if (row.status == RowStatus.REVISAR) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        )
        b.checkIncluded.setOnCheckedChangeListener(null)
        b.checkIncluded.isChecked = row.esFactura
        b.checkIncluded.setOnCheckedChangeListener { _, checked ->
            row.esFactura = checked
            onChanged()
        }

        b.editReferencia.setText(row.referencia ?: "")
        b.editValor.setText(row.valor?.let { "%.2f".format(it) } ?: "")

        b.editReferencia.tag = row.id
        b.editValor.tag = row.id

        b.editReferencia.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                row.referencia = b.editReferencia.text.toString().ifBlank { null }
                revalidate(row)
                onChanged()
            }
        }
        b.editValor.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                row.valor = b.editValor.text.toString().replace(",", ".").toDoubleOrNull()
                revalidate(row)
                onChanged()
            }
        }
    }

    private fun revalidate(row: InvoiceRow) {
        row.status = if (row.esFactura && (row.referencia == null || row.valor == null)) {
            RowStatus.REVISAR
        } else {
            RowStatus.CORRECTO
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size
}
