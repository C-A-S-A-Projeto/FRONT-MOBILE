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
import kotlin.math.roundToInt

/**
 * AvatarCropView: Viewport interativo de recorte espacial estilo WhatsApp (proporção 1:1)
 * com transformações afins 2D (scale / pinch-to-zoom e translateX / translateY / pan),
 * restrições estritas de bounding box (impedindo áreas vazias dentro da máscara quadrada 1:1)
 * e geração de saída exata em 240x240 pixels.
 */
class AvatarCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class TransformacaoAfim2D(
        val scale: Float,
        val translateX: Float,
        val translateY: Float
    )

    data class CoordenadasRecorteIntrinsecas(
        val originX: Int,
        val originY: Int,
        val width: Int,
        val height: Int
    )

    companion object {
        const val OUTPUT_SIZE_PX = 240
        const val JPEG_QUALITY = 85
        const val MAX_ZOOM_MULTIPLIER = 5.0f

        /**
         * Aplica restrições de bounding box às transformações afins 2D (scale, translateX, translateY)
         * garantindo que a imagem nunca deixe áreas vazias dentro da máscara de recorte quadrada 1:1.
         */
        fun restringirTransformacaoBoundingBox(
            imageWidth: Float,
            imageHeight: Float,
            cropLeft: Float,
            cropTop: Float,
            cropSize: Float,
            requestedScale: Float,
            requestedTranslateX: Float,
            requestedTranslateY: Float
        ): TransformacaoAfim2D {
            if (imageWidth <= 0f || imageHeight <= 0f || cropSize <= 0f) {
                return TransformacaoAfim2D(1f, 0f, 0f)
            }

            val minScale = max(cropSize / imageWidth, cropSize / imageHeight)
            val maxScale = minScale * MAX_ZOOM_MULTIPLIER
            val clampedScale = requestedScale.coerceIn(minScale, maxScale)

            val scaledWidth = imageWidth * clampedScale
            val scaledHeight = imageHeight * clampedScale

            val cropRight = cropLeft + cropSize
            val cropBottom = cropTop + cropSize

            // Restrição horizontal: a borda esquerda da imagem <= cropLeft e a borda direita >= cropRight
            val minTransX = cropRight - scaledWidth
            val maxTransX = cropLeft
            val clampedTransX = if (minTransX <= maxTransX) {
                requestedTranslateX.coerceIn(minTransX, maxTransX)
            } else {
                cropLeft + (cropSize - scaledWidth) / 2f
            }

            // Restrição vertical: a borda superior da imagem <= cropTop e a borda inferior >= cropBottom
            val minTransY = cropBottom - scaledHeight
            val maxTransY = cropTop
            val clampedTransY = if (minTransY <= maxTransY) {
                requestedTranslateY.coerceIn(minTransY, maxTransY)
            } else {
                cropTop + (cropSize - scaledHeight) / 2f
            }

            return TransformacaoAfim2D(
                scale = clampedScale,
                translateX = clampedTransX,
                translateY = clampedTransY
            )
        }

        /**
         * Calcula as coordenadas de recorte relativas (originX, originY, width, height)
         * mapeadas para as dimensões intrínsecas da imagem original.
         */
        fun calcularCoordenadasRecorte(
            imageWidth: Int,
            imageHeight: Int,
            cropLeft: Float,
            cropTop: Float,
            cropSize: Float,
            transformacao: TransformacaoAfim2D
        ): CoordenadasRecorteIntrinsecas {
            if (imageWidth <= 0 || imageHeight <= 0 || transformacao.scale <= 0f) {
                return CoordenadasRecorteIntrinsecas(0, 0, max(1, imageWidth), max(1, imageHeight))
            }

            val rawOriginX = ((cropLeft - transformacao.translateX) / transformacao.scale).roundToInt()
            val rawOriginY = ((cropTop - transformacao.translateY) / transformacao.scale).roundToInt()
            val rawCropSide = (cropSize / transformacao.scale).roundToInt()

            val maxSide = min(imageWidth, imageHeight)
            val side = rawCropSide.coerceIn(1, maxSide)
            val originX = rawOriginX.coerceIn(0, max(0, imageWidth - side))
            val originY = rawOriginY.coerceIn(0, max(0, imageHeight - side))

            return CoordenadasRecorteIntrinsecas(
                originX = originX,
                originY = originY,
                width = side,
                height = side
            )
        }

        /**
         * Executa o crop nas coordenadas intrínsecas seguido de downsampling/resizing filtrado
         * para exatos 240x240 pixels.
         */
        fun recortarERedimensionar240x240(
            source: Bitmap,
            coordenadas: CoordenadasRecorteIntrinsecas,
            targetSize: Int = OUTPUT_SIZE_PX
        ): Bitmap {
            val safeX = coordenadas.originX.coerceIn(0, max(0, source.width - 1))
            val safeY = coordenadas.originY.coerceIn(0, max(0, source.height - 1))
            val safeW = coordenadas.width.coerceIn(1, source.width - safeX)
            val safeH = coordenadas.height.coerceIn(1, source.height - safeY)

            val cropped = Bitmap.createBitmap(source, safeX, safeY, safeW, safeH)
            if (cropped.width == targetSize && cropped.height == targetSize) {
                return cropped
            }

            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            val srcRect = android.graphics.Rect(0, 0, cropped.width, cropped.height)
            val dstRect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
            canvas.drawBitmap(cropped, srcRect, dstRect, paint)
            if (cropped != source && cropped != output) {
                cropped.recycle()
            }
            return output
        }
    }

    private var bitmap: Bitmap? = null
    private val renderMatrix = Matrix()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
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
    private val circleGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9938BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val cropRect = RectF()
    private val overlayPath = Path()

    private var currentScale = 1f
    private var currentTransX = 0f
    private var currentTransY = 0f
    private var minScale = 1f
    private var maxScale = 5f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    var onZoomChanged: ((zoomNormalized: Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val bmp = bitmap ?: return false
                val factor = detector.scaleFactor
                val targetScale = (currentScale * factor).coerceIn(minScale, maxScale)
                val scaleChange = targetScale / currentScale

                val focusX = detector.focusX
                val focusY = detector.focusY

                val nextTransX = focusX - (focusX - currentTransX) * scaleChange
                val nextTransY = focusY - (focusY - currentTransY) * scaleChange

                aplicarTransformacaoComBoundingBox(bmp, targetScale, nextTransX, nextTransY)
                notificarZoomSlider()
                invalidate()
                return true
            }
        }
    )

    fun setImageBitmapParaRecorte(novoBitmap: Bitmap) {
        this.bitmap = novoBitmap
        if (width > 0 && height > 0) {
            inicializarViewportETransformacao()
            invalidate()
        }
    }

    fun rotacionar90Graus() {
        val atual = bitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        val rotacionado = Bitmap.createBitmap(atual, 0, 0, atual.width, atual.height, m, true)
        bitmap = rotacionado
        inicializarViewportETransformacao()
        invalidate()
    }

    /**
     * Define o nível de zoom via controle deslizante (0.0 = minScale, 1.0 = maxScale) mantendo o centro da máscara.
     */
    fun definirZoomNormalizado(progressoZeroAUm: Float) {
        val bmp = bitmap ?: return
        val frac = progressoZeroAUm.coerceIn(0f, 1f)
        val targetScale = minScale + (maxScale - minScale) * frac
        if (currentScale <= 0f) return

        val scaleChange = targetScale / currentScale
        val centerX = cropRect.centerX()
        val centerY = cropRect.centerY()

        val nextTransX = centerX - (centerX - currentTransX) * scaleChange
        val nextTransY = centerY - (centerY - currentTransY) * scaleChange

        aplicarTransformacaoComBoundingBox(bmp, targetScale, nextTransX, nextTransY)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            inicializarViewportETransformacao()
        }
    }

    private fun inicializarViewportETransformacao() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padding = min(w, h) * 0.08f
        val cropSide = min(w, h) - padding * 2f
        val left = (w - cropSide) / 2f
        val top = (h - cropSide) / 2f
        cropRect.set(left, top, left + cropSide, top + cropSide)

        val bmp = bitmap ?: return
        minScale = max(cropSide / bmp.width.toFloat(), cropSide / bmp.height.toFloat())
        maxScale = minScale * MAX_ZOOM_MULTIPLIER

        val initScale = minScale
        val scaledW = bmp.width * initScale
        val scaledH = bmp.height * initScale
        val initTransX = cropRect.centerX() - scaledW / 2f
        val initTransY = cropRect.centerY() - scaledH / 2f

        aplicarTransformacaoComBoundingBox(bmp, initScale, initTransX, initTransY)
        notificarZoomSlider()
    }

    private fun aplicarTransformacaoComBoundingBox(
        bmp: Bitmap,
        reqScale: Float,
        reqTransX: Float,
        reqTransY: Float
    ) {
        val restrita = restringirTransformacaoBoundingBox(
            imageWidth = bmp.width.toFloat(),
            imageHeight = bmp.height.toFloat(),
            cropLeft = cropRect.left,
            cropTop = cropRect.top,
            cropSize = cropRect.width(),
            requestedScale = reqScale,
            requestedTranslateX = reqTransX,
            requestedTranslateY = reqTransY
        )
        currentScale = restrita.scale
        currentTransX = restrita.translateX
        currentTransY = restrita.translateY

        renderMatrix.reset()
        renderMatrix.postScale(currentScale, currentScale)
        renderMatrix.postTranslate(currentTransX, currentTransY)
    }

    private fun notificarZoomSlider() {
        val denom = (maxScale - minScale)
        val norm = if (denom > 0f) ((currentScale - minScale) / denom).coerceIn(0f, 1f) else 0f
        onZoomChanged?.invoke(norm)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)

        val bmp = bitmap ?: return true

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

                        aplicarTransformacaoComBoundingBox(
                            bmp,
                            currentScale,
                            currentTransX + dx,
                            currentTransY + dy
                        )
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

        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, renderMatrix, bitmapPaint)
        }

        if (cropRect.width() <= 0f) return

        // Máscara externa escura ao redor do viewport 1:1
        overlayPath.reset()
        overlayPath.fillType = Path.FillType.EVEN_ODD
        overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        overlayPath.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(overlayPath, overlayPaint)

        // Grade 3x3 (Regra dos Terços) dentro do viewport
        val third = cropRect.width() / 3f
        for (i in 1..2) {
            val x = cropRect.left + third * i
            val y = cropRect.top + third * i
            canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint)
            canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint)
        }

        // Guia circular de avatar inscrita no quadrado 1:1
        canvas.drawCircle(
            cropRect.centerX(),
            cropRect.centerY(),
            cropRect.width() / 2f,
            circleGuidePaint
        )

        // Moldura quadrada 1:1
        canvas.drawRect(cropRect, borderPaint)
    }

    /**
     * Calcula o recorte nas coordenadas intrínsecas e retorna o Bitmap final em exatos 240x240px.
     */
    fun gerarBitmapRecortado240x240(): Bitmap? {
        val bmp = bitmap ?: return null
        val coords = calcularCoordenadasRecorte(
            imageWidth = bmp.width,
            imageHeight = bmp.height,
            cropLeft = cropRect.left,
            cropTop = cropRect.top,
            cropSize = cropRect.width(),
            transformacao = TransformacaoAfim2D(
                scale = currentScale,
                translateX = currentTransX,
                translateY = currentTransY
            )
        )
        return recortarERedimensionar240x240(bmp, coords, OUTPUT_SIZE_PX)
    }
}
