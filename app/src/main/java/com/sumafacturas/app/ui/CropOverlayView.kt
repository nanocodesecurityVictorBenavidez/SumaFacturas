package com.sumafacturas.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Vista simple que permite al usuario dibujar y ajustar un rectangulo sobre
 * la imagen mostrada debajo. Se usa tanto para marcar la zona de la tabla
 * como, en un segundo paso, para marcar unicamente la columna "Valor".
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { TABLE, VALOR_COLUMN }

    /** Que rectangulo se edita con el dedo en este momento. */
    var mode: Mode = Mode.TABLE

    /** Zona de la tabla completa (Referencia / Transaccion / Valor). */
    var selection: RectF? = null
        private set

    /** Zona exacta de la columna Valor, marcada manualmente por el usuario (opcional). */
    var valorColumnSelection: RectF? = null
        private set

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragging = false

    private val fillPaint = Paint().apply {
        color = Color.parseColor("#33F2A900")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = Color.parseColor("#F2A900")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val valorFillPaint = Paint().apply {
        color = Color.parseColor("#551E88E5")
        style = Paint.Style.FILL
    }
    private val valorStrokePaint = Paint().apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    fun reset() {
        selection = null
        valorColumnSelection = null
        invalidate()
    }

    /** Restringe el area seleccionable a los limites de la imagen mostrada (letterboxing). */
    var imageBounds: RectF? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartY = event.y
                dragging = true
                val start = RectF(event.x, event.y, event.x, event.y)
                if (mode == Mode.TABLE) selection = start else valorColumnSelection = start
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val rect = normalize(dragStartX, dragStartY, event.x, event.y)
                if (mode == Mode.TABLE) selection = rect else valorColumnSelection = rect
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }

    private fun normalize(x1: Float, y1: Float, x2: Float, y2: Float): RectF {
        val bounds = imageBounds
        var left = min(x1, x2)
        var top = min(y1, y2)
        var right = max(x1, x2)
        var bottom = max(y1, y2)
        if (bounds != null) {
            left = left.coerceIn(bounds.left, bounds.right)
            right = right.coerceIn(bounds.left, bounds.right)
            top = top.coerceIn(bounds.top, bounds.bottom)
            bottom = bottom.coerceIn(bounds.top, bounds.bottom)
        }
        return RectF(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sel = selection
        if (sel != null) {
            // Oscurece todo menos la zona seleccionada, para que sea claro que area se usara.
            canvas.drawRect(0f, 0f, width.toFloat(), sel.top, dimPaint)
            canvas.drawRect(0f, sel.bottom, width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawRect(0f, sel.top, sel.left, sel.bottom, dimPaint)
            canvas.drawRect(sel.right, sel.top, width.toFloat(), sel.bottom, dimPaint)

            canvas.drawRect(sel, fillPaint)
            canvas.drawRect(sel, strokePaint)
        }
        valorColumnSelection?.let { vc ->
            canvas.drawRect(vc, valorFillPaint)
            canvas.drawRect(vc, valorStrokePaint)
        }
    }

    /** Convierte un rectangulo (coordenadas de pantalla) a coordenadas del bitmap original. */
    private fun toBitmapSpace(rect: RectF, viewToImageScale: Float, imageOffsetX: Float, imageOffsetY: Float): android.graphics.Rect =
        android.graphics.Rect(
            ((rect.left - imageOffsetX) / viewToImageScale).toInt(),
            ((rect.top - imageOffsetY) / viewToImageScale).toInt(),
            ((rect.right - imageOffsetX) / viewToImageScale).toInt(),
            ((rect.bottom - imageOffsetY) / viewToImageScale).toInt()
        )

    fun selectionInBitmapSpace(viewToImageScale: Float, imageOffsetX: Float, imageOffsetY: Float): android.graphics.Rect? =
        selection?.let { toBitmapSpace(it, viewToImageScale, imageOffsetX, imageOffsetY) }

    fun valorColumnInBitmapSpace(viewToImageScale: Float, imageOffsetX: Float, imageOffsetY: Float): android.graphics.Rect? =
        valorColumnSelection?.let { toBitmapSpace(it, viewToImageScale, imageOffsetX, imageOffsetY) }
}
