package fatec.labweb.projetocasa.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.SoundManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * ModalRecorteAvatar: Modal de recorte espacial estilo WhatsApp (1:1) com Pinch-to-Zoom, Pan,
 * controle deslizante de zoom, rotação 90° e saída exata de 240x240px JPEG (qualidade 85%).
 */
class ModalRecorteAvatar(
    private val activity: Activity,
    private val onCropConfirm: (arquivoFinal240: File, bytesJpeg240: ByteArray, bitmap240: Bitmap) -> Unit
) {

    fun exibirParaUri(uri: Uri, userId: String = "usuario_local") {
        val bitmapOriginal = carregarBitmapSeguroDeUri(uri)
        if (bitmapOriginal == null) {
            Toast.makeText(activity, "Não foi possível carregar a imagem selecionada.", Toast.LENGTH_SHORT).show()
            return
        }
        exibirModal(bitmapOriginal, userId)
    }

    fun exibirParaArquivo(arquivo: File, userId: String = "usuario_local") {
        if (!arquivo.exists() || arquivo.length() == 0L) {
            Toast.makeText(activity, "Arquivo de imagem inválido.", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmapOriginal = carregarBitmapSeguroDeArquivo(arquivo)
        if (bitmapOriginal == null) {
            Toast.makeText(activity, "Não foi possível processar a foto capturada.", Toast.LENGTH_SHORT).show()
            return
        }
        exibirModal(bitmapOriginal, userId)
    }

    private fun exibirModal(bitmapOriginal: Bitmap, userId: String) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val rootLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitulo = TextView(activity).apply {
            text = "Ajustar e Recortar Foto (240x240)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        rootLayout.addView(tvTitulo)

        val tvSubtitulo = TextView(activity).apply {
            text = "Arraste e faça pinça (zoom) para enquadrar dentro da máscara 1:1"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        rootLayout.addView(tvSubtitulo)

        val cropView = AvatarCropView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(300)
            )
            setImageBitmapParaRecorte(bitmapOriginal)
        }
        rootLayout.addView(cropView)

        // Barra de controle de Zoom + Botão Girar 90°
        val controlsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }

        val tvZoomLabel = TextView(activity).apply {
            text = "Zoom"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, dp(8), 0)
        }
        controlsRow.addView(tvZoomLabel)

        var atualizandoSeekInterno = false
        val seekZoom = SeekBar(activity).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !atualizandoSeekInterno) {
                        cropView.definirZoomNormalizado(progress / 100f)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        controlsRow.addView(seekZoom)

        cropView.onZoomChanged = { norm ->
            atualizandoSeekInterno = true
            seekZoom.progress = (norm * 100f).toInt().coerceIn(0, 100)
            atualizandoSeekInterno = false
        }

        val btnGirar = MaterialButton(activity).apply {
            text = "Girar 90°"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginStart = dp(8)
            }
            setOnClickListener { view ->
                SoundManager.dispararAudioCue(view)
                cropView.rotacionar90Graus()
            }
        }
        controlsRow.addView(btnGirar)
        rootLayout.addView(controlsRow)

        // Linha de Botões: Cancelar e Confirmar Recorte
        val actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(rootLayout)
            .setCancelable(false)
            .create()

        val btnCancelar = MaterialButton(activity).apply {
            text = "Cancelar"
            isAllCaps = false
            setTextColor(Color.parseColor("#CBD5E1"))
            setBackgroundColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { view ->
                SoundManager.dispararAudioCue(view)
                dialog.dismiss()
            }
        }

        val btnConfirmar = MaterialButton(activity).apply {
            text = "Confirmar Recorte"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener { view ->
                SoundManager.dispararAudioCue(view)
                val bitmap240 = cropView.gerarBitmapRecortado240x240()
                if (bitmap240 == null) {
                    Toast.makeText(activity, "Erro ao recortar a imagem.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Compressão JPEG (qualidade 85% / 0.85) em exatos 240x240px
                val baos = ByteArrayOutputStream()
                bitmap240.compress(Bitmap.CompressFormat.JPEG, AvatarCropView.JPEG_QUALITY, baos)
                val bytesJpeg = baos.toByteArray()

                // Persiste no diretório permanente da aplicação
                val pastaExterna = activity.getExternalFilesDir("perfil") ?: File(activity.filesDir, "perfil")
                if (!pastaExterna.exists()) pastaExterna.mkdirs()
                val arquivoPrincipal = File(pastaExterna, "foto_perfil.jpg")
                FileOutputStream(arquivoPrincipal).use { it.write(bytesJpeg) }
                arquivoPrincipal.setLastModified(System.currentTimeMillis())

                // Espelha também em filesDir/perfil/$userId.jpg para o menu lateral (BaseDrawerActivity)
                try {
                    val pastaInterna = File(activity.filesDir, "perfil")
                    if (!pastaInterna.exists()) pastaInterna.mkdirs()
                    val arquivoDrawer = File(pastaInterna, "$userId.jpg")
                    FileOutputStream(arquivoDrawer).use { it.write(bytesJpeg) }
                    arquivoDrawer.setLastModified(System.currentTimeMillis())
                } catch (_: Exception) {}

                dialog.dismiss()
                onCropConfirm(arquivoPrincipal, bytesJpeg, bitmap240)
            }
        }

        actionsRow.addView(btnCancelar)
        actionsRow.addView(btnConfirmar)
        rootLayout.addView(actionsRow)

        dialog.show()
    }

    private fun carregarBitmapSeguroDeUri(uri: Uri): Bitmap? {
        return try {
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            activity.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, optsBounds)
            }
            val sampleSize = calcularInSampleSize(optsBounds.outWidth, optsBounds.outHeight, 1440, 1440)
            val optsDecode = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            activity.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, optsDecode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun carregarBitmapSeguroDeArquivo(file: File): Bitmap? {
        return try {
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, optsBounds)
            val sampleSize = calcularInSampleSize(optsBounds.outWidth, optsBounds.outHeight, 1440, 1440)
            val optsDecode = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, optsDecode)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calcularInSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (rawH > reqH || rawW > reqW) {
            val halfH = rawH / 2
            val halfW = rawW / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
