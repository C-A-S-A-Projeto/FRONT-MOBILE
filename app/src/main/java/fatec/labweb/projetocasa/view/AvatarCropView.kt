package fatec.labweb.projetocasa.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Viewport de Recorte Interativo (estilo WhatsApp) com suporte a gestos multitoque:
 * - Pinch-to-Zoom (scale) e Pan/Arrasto (translateX, translateY) via Matrix afim 2D.
 * - Restrição de bounding box na máscara quadrada 1:1 para impedir bordas vazias.
 * - Exportação exata em 240x240 pixels (downsampling/resizing).
 */
class AvatarCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val OUTPUT_SIZE_PX = 240
    }

    private var sourceBitmap: Bitmap? = null
    private val transformMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var minScale = 1f
    private var maxScale = 5f

    private val cropRect = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val overlayPath = Path()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val bmp = sourceBitmap ?: return false
                if (bmp.width <= 0 || bmp.height <= 0) return false
                val currentScale = obterEscalaAtual()
                var scaleFactor = detector.scaleFactor
                val targetScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
                scaleFactor = targetScale / currentScale

                transformMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                restringirDentroDaMascara()
                invalidate()
                onZoomChanged?.invoke( obterProgressoZoom() )
                return true
            }
        }
    )

    var onZoomChanged: ((Float) -> Unit)? = null

    fun setBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        inicializarTransformacao()
        invalidate()
    }

    fun rotacionar90Graus() {
        val bmp = sourceBitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        val rotacionado = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        sourceBitmap = rotacionado
        inicializarTransformacao()
        invalidate()
    }

    /**
     * Define o nível de zoom normalizado (0.0 = minScale, 1.0 = maxScale) a partir de um Slider/SeekBar.
     */
    fun definirZoomNormalizado(progresso: Float) {
        if (sourceBitmap == null || cropRect.isEmpty) return
        val clamped = progresso.coerceIn(0f, 1f)
        val targetScale = minScale + clamped * (maxScale - minScale)
        val currentScale = obterEscalaAtual()
        if (currentScale <= 0f) return
        val factor = targetScale / currentScale
        transformMatrix.postScale(factor, factor, cropRect.centerX(), cropRect.centerY())
        restringirDentroDaMascara()
        invalidate()
    }

    fun obterProgressoZoom(): Float {
        val denom = (maxScale - minScale)
        if (denom <= 0.0001f) return 0f
        return ((obterEscalaAtual() - minScale) / denom).coerceIn(0f, 1f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        atualizarCropRect(w, h)
        inicializarTransformacao()
    }

    private fun atualizarCropRect(w: Int, h: Int) {
        val padding = min(w, h) * 0.08f
        val side = min(w, h) - padding * 2f
        val left = (w - side) / 2f
        val top = (h - side) / 2f
        cropRect.set(left, top, left + side, top + side)
    }

    private fun inicializarTransformacao() {
        val bmp = sourceBitmap ?: return
        if (width <= 0 || height <= 0) return
        if (cropRect.isEmpty) atualizarCropRect(width, height)

        transformMatrix.reset()
        val scaleX = cropRect.width() / bmp.width.toFloat()
        val scaleY = cropRect.height() / bmp.height.toFloat()
        // Garante preenchimento completo da máscara quadrada 1:1
        minScale = max(scaleX, scaleY)
        maxScale = minScale * 4.0f

        val dx = cropRect.centerX() - (bmp.width * minScale) / 2f
        val dy = cropRect.centerY() - (bmp.height * minScale) / 2f

        transformMatrix.postScale(minScale, minScale)
        transformMatrix.postTranslate(dx, dy)
        restringirDentroDaMascara()
        onZoomChanged?.invoke(0f)
    }

    private fun obterEscalaAtual(): Float {
        transformMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    /**
     * Impede áreas vazias dentro da máscara de recorte 1:1 ajustando translateX e translateY.
     */
    private fun restringirDentroDaMascara() {
        val bmp = sourceBitmap ?: return
        if (cropRect.isEmpty) return

        val scale = obterEscalaAtual()
        if (scale < minScale) {
            val factor = minScale / scale
            transformMatrix.postScale(factor, factor, cropRect.centerX(), cropRect.centerY())
        }

        val mappedRect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        transformMatrix.mapRect(mappedRect)

        var deltaX = 0f
        var deltaY = 0f

        if (mappedRect.width() >= cropRect.width()) {
            if (mappedRect.left > cropRect.left) {
                deltaX = cropRect.left - mappedRect.left
            } else if (mappedRect.right < cropRect.right) {
                deltaX = cropRect.right - mappedRect.right
            }
        } else {
            deltaX = cropRect.centerX() - mappedRect.centerX()
        }

        if (mappedRect.height() >= cropRect.height()) {
            if (mappedRect.top > cropRect.top) {
                deltaY = cropRect.top - mappedRect.top
            } else if (mappedRect.bottom < cropRect.bottom) {
                deltaY = cropRect.bottom - mappedRect.bottom
            }
        } else {
            deltaY = cropRect.centerY() - mappedRect.centerY()
        }

        if (deltaX != 0f || deltaY != 0f) {
            transformMatrix.postTranslate(deltaX, deltaY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sourceBitmap == null) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        transformMatrix.postTranslate(dx, dy)
                        restringirDentroDaMascara()
                        invalidate()
                        lastTouchX = x
                        lastTouchY = y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#111827"))

        val bmp = sourceBitmap
        if (bmp != null) {
            canvas.save()
            canvas.drawBitmap(bmp, transformMatrix, bitmapPaint)
            canvas.restore()
        }

        // Máscara escura externa ao quadrado 1:1
        overlayPath.reset()
        overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        overlayPath.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(overlayPath, overlayPaint)

        // Grade 3x3 estilo WhatsApp
        val thirdW = cropRect.width() / 3f
        val thirdH = cropRect.height() / 3f
        for (i in 1..2) {
            val x = cropRect.left + thirdW * i
            canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint)
            val y = cropRect.top + thirdH * i
            canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint)
        }

        // Guia circular sutil para preview de avatar + borda quadrada 1:1
        canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, gridPaint)
        canvas.drawRect(cropRect, borderPaint)
    }

    /**
     * Calcula as coordenadas relativas (originX, originY, width, height) mapeadas para a imagem
     * original e gera o Bitmap final recortado e redimensionado para exatos 240x240 pixels.
     */
    fun gerarBitmapRecortado240x240(): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (cropRect.isEmpty) return null

        restringirDentroDaMascara()
        transformMatrix.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X].coerceAtLeast(0.0001f)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        // Coordenadas intrínsecas na imagem original
        val rawOriginX = (cropRect.left - transX) / scale
        val rawOriginY = (cropRect.top - transY) / scale
        val rawCropSize = cropRect.width() / scale

        val originX = rawOriginX.toInt().coerceIn(0, max(0, bmp.width - 1))
        val originY = rawOriginY.toInt().coerceIn(0, max(0, bmp.height - 1))
        val cropW = rawCropSize.toInt().coerceIn(1, bmp.width - originX)
        val cropH = rawCropSize.toInt().coerceIn(1, bmp.height - originY)
        val side = min(cropW, cropH).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(bmp, originX, originY, side, side)
        return if (cropped.width == OUTPUT_SIZE_PX && cropped.height == OUTPUT_SIZE_PX) {
            cropped
        } else {
            val scaled = Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE_PX, OUTPUT_SIZE_PX, true)
            if (scaled != cropped) {
                cropped.recycle()
            }
            scaled
        }
    }
}
