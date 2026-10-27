package com.example.focusnest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A circular progress ring. progress 1f is a full ring, 0f is empty. */
class RingView(context: Context) : View(context) {

    private val stroke = context.resources.displayMetrics.density * 14f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = 0xFFE5E7EB.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = 0xFF3B6FD8.toInt()
    }

    private val bounds = RectF()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var ringColor: Int
        get() = progressPaint.color
        set(value) {
            progressPaint.color = value
            invalidate()
        }

    var trackColor: Int
        get() = trackPaint.color
        set(value) {
            trackPaint.color = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2
        bounds.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)
        canvas.drawArc(bounds, -90f, 360f * progress, false, progressPaint)
    }
}
