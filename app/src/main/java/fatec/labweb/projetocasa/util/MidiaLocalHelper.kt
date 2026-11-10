package fatec.labweb.projetocasa.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

object MidiaLocalHelper {

    fun salvarImagemLocalmente(context: Context, botaoId: String, fonte: Any): File? {
        val pastaDestino = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        if (!pastaDestino.exists()) {
            pastaDestino.mkdirs()
        }

        val arquivoDestino = File(pastaDestino, "$botaoId.jpg")

        return try {
            when (fonte) {
                is Uri -> {
                    context.contentResolver.openInputStream(fonte)?.use { input ->
                        FileOutputStream(arquivoDestino).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                is File -> {
                    if (fonte.exists() && fonte.absolutePath != arquivoDestino.absolutePath) {
                        fonte.copyTo(arquivoDestino, overwrite = true)
                    }
                }
                is Int -> {
                    val drawable = ContextCompat.getDrawable(context, fonte) ?: return null
                    salvarDrawableComoJpg(drawable, arquivoDestino)
                }
                is Bitmap -> {
                    FileOutputStream(arquivoDestino).use { out ->
                        fonte.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }
                else -> return null
            }
            if (arquivoDestino.exists() && arquivoDestino.length() > 0) arquivoDestino else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun salvarDrawableComoJpg(drawable: Drawable, arquivoDestino: File) {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 300
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 300
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        FileOutputStream(arquivoDestino).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }

    fun obterArquivoLocal(context: Context, botaoId: String): File? {
        val pasta = context.getExternalFilesDir("botoes_imagens")
            ?: File(context.filesDir, "botoes_imagens")
        val arquivo = File(pasta, "$botaoId.jpg")
        return if (arquivo.exists()) arquivo else null
    }
}
