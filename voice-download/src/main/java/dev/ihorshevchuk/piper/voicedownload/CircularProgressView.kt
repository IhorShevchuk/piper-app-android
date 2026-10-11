package dev.ihorshevchuk.piper.voicedownload

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * Circular download-progress indicator: a faint full-circle track
 * with a round-capped progress arc starting at the top, in the app
 * accent color.
 *
 * TalkBack: the hosting row sets the content description
 * (label + percent).
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Progress, 0..1. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var lineWidth: Float = 6f * resources.displayMetrics.density
        set(value) {
            field = value
            updatePaint()
            invalidate()
        }

    var tint: Int = resolveAccent(context)
        set(value) {
            field = value
            updatePaint()
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        updatePaint()
        // 25dp default size.
        val size = (25f * resources.displayMetrics.density).toInt()
        minimumWidth = size
        minimumHeight = size
    }

    private fun updatePaint() {
        trackPaint.strokeWidth = lineWidth
        trackPaint.color = tint
        trackPaint.alpha = 51 // 20% opacity
        progressPaint.strokeWidth = lineWidth
        progressPaint.color = tint
    }

    companion object {
        /** The theme accent (system blue). */
        private fun resolveAccent(context: Context): Int {
            val tv = TypedValue()
            return if (context.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)) {
                tv.data
            } else {
                Color.BLUE
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - lineWidth / 2f
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(
                cx - radius, cy - radius, cx + radius, cy + radius,
                -90f, 360f * progress, false, progressPaint
            )
        }
    }
}
