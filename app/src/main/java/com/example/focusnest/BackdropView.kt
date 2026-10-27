package com.example.focusnest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.random.Random

/** Decorative background drawn in code: a star field or mountain silhouettes. */
class BackdropView(context: Context, private val theme: AppTheme) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Fixed seed, so the star field looks the same every time the screen opens
    private val stars: List<Triple<Float, Float, Float>> = Random(7).let { rnd ->
        List(90) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.75f, 0.8f + rnd.nextFloat() * 1.8f) }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (theme.backdrop) {
            Backdrop.STARS -> {
                paint.color = theme.decor
                for ((x, y, r) in stars) canvas.drawCircle(x * w, y * h, r * density, paint)
            }
            Backdrop.MOUNTAINS -> {
                drawRange(canvas, w, h, FAR_RANGE, theme.decorFar)
                drawRange(canvas, w, h, NEAR_RANGE, theme.decor)
            }
            Backdrop.NONE -> Unit
        }
    }

    private fun drawRange(canvas: Canvas, w: Float, h: Float, points: List<Pair<Float, Float>>, color: Int) {
        path.reset()
        path.moveTo(0f, h)
        for ((x, y) in points) path.lineTo(x * w, y * h)
        path.lineTo(w, h)
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    companion object {
        // Points are fractions of the screen: x across, y down. The ranges sit in the lower part of the screen.
        private val FAR_RANGE = listOf(
            0f to 0.7f, 0.18f to 0.6f, 0.32f to 0.7f, 0.5f to 0.58f, 0.7f to 0.68f, 0.86f to 0.6f, 1f to 0.7f
        )
        private val NEAR_RANGE = listOf(
            0f to 0.82f, 0.22f to 0.74f, 0.4f to 0.8f, 0.62f to 0.72f, 0.82f to 0.8f, 1f to 0.76f
        )
    }
}
