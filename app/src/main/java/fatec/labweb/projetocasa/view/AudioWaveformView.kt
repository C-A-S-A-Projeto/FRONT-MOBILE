package fatec.labweb.projetocasa.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * AudioWaveformView: Componente que desenha ondas sonoras verticais de amplitude em tempo real
 * simulando a interface de gravação de áudio do WhatsApp.
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val amplitudes = mutableListOf<Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        style = Paint.Style.FILL
    }
    private val rectF = RectF()
    private val barWidth = 8f
    private val barSpace = 4f
    private val maxBars = 36

    fun addAmplitude(amp: Float) {
        val normalized = (amp / 32767f).coerceIn(0.08f, 1.0f)
        if (amplitudes.size >= maxBars) {
            amplitudes.removeAt(0)
        }
        amplitudes.add(normalized)
        postInvalidate()
    }

    fun clear() {
        amplitudes.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val viewHeight = height.toFloat()
        val viewWidth = width.toFloat()

        if (amplitudes.isEmpty()) {
            paint.color = Color.parseColor("#94A3B8")
            val centerY = viewHeight / 2f
            canvas.drawLine(0f, centerY, viewWidth, centerY, paint)
            return
        }

        paint.color = Color.parseColor("#2563EB")
        val totalBarWidth = barWidth + barSpace
        val startX = (viewWidth - (amplitudes.size * totalBarWidth)).coerceAtLeast(0f)

        for (i in amplitudes.indices) {
            val amp = amplitudes[i]
            val barHeight = (viewHeight * amp).coerceAtLeast(6f)
            val left = startX + (i * totalBarWidth)
            val top = (viewHeight - barHeight) / 2f
            val right = left + barWidth
            val bottom = top + barHeight

            rectF.set(left, top, right, bottom)
            canvas.drawRoundRect(rectF, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
