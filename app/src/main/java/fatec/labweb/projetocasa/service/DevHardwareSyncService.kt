package fatec.labweb.projetocasa.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DevHardwareSyncService: Sincronização direta em Modo Desenvolvedor.
 * Grava as mídias em Base64 na coleção 'arquivos_esp' do Cloud Firestore (foto1..5, som1..5)
 * e aciona a flag 'atualizar = true' no Firebase Realtime Database (/dispositivo/controle),
 * exatamente como o firmware casa_firmware_v8_dev espera e como funciona no SaaS Web.
 */
class DevHardwareSyncService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val imageProcessor: Esp32ImageProcessor = Esp32ImageProcessor()
) {

    companion object {
        private const val TAG = "DevHardwareSync"
        const val COLECAO_ARQUIVOS_ESP = "arquivos_esp"
        const val NODO_RTDB_CONTROLE = "dispositivo/controle"
        const val LARGURA_IMG_ESP = 240
        const val ALTURA_IMG_ESP = 240
    }

    /**
     * Sincroniza os botões da categoria com a coleção 'arquivos_esp' e dispara o Realtime Database.
     * Mapeamento direto:
     * - Botão 1 -> /arquivos_esp/foto1 e /arquivos_esp/som1 (acionado por BTN_1 no ESP32 v8)
     * - Botão 2 -> /arquivos_esp/foto2 e /arquivos_esp/som2 (acionado por BTN_2 no ESP32 v8)
     * - Botões 3..5 -> foto3..5 e som3..5
     */
    suspend fun sincronizarComHardwareDev(
        context: Context,
        botoes: List<CategoriaItem>,
        corCategoria: String = "#2563EB"
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var totalSalvos = 0
            val limite = minOf(botoes.size, 5)

            for (idx in 0 until limite) {
                val slot = idx + 1
                val item = botoes[idx]

                // 1. Processar Foto (foto1..5)
                val b64Foto = obterBase64Imagem(context, item, corCategoria)
                if (b64Foto != null) {
                    val docId = "foto$slot"
                    try {
                        firestore.collection(COLECAO_ARQUIVOS_ESP)
                            .document(docId)
                            .set(mapOf("b64" to b64Foto))
                            .await()
                        totalSalvos++
                        Log.d(TAG, "[$docId] Foto gravada em $COLECAO_ARQUIVOS_ESP (${b64Foto.length} caracteres)")
                    } catch (eFs: Exception) {
                        Log.w(TAG, "[$docId] Foto não gravada diretamente no Firestore (tolerante a regras v2): ${eFs.message}")
                    }
                }

                // 2. Processar Áudio (som1..5)
                val b64Som = obterBase64Audio(context, item, slot)
                if (b64Som != null) {
                    val docId = "som$slot"
                    try {
                        firestore.collection(COLECAO_ARQUIVOS_ESP)
                            .document(docId)
                            .set(mapOf("b64" to b64Som))
                            .await()
                        totalSalvos++
                        Log.d(TAG, "[$docId] Áudio gravado em $COLECAO_ARQUIVOS_ESP (${b64Som.length} caracteres)")
                    } catch (eFs: Exception) {
                        Log.w(TAG, "[$docId] Áudio não gravado diretamente no Firestore (tolerante a regras v2): ${eFs.message}")
                    }
                }
            }

            // 3. Disparar gatilho no Realtime Database (/dispositivo/controle -> { atualizar: true })
            try {
                val timestamp = System.currentTimeMillis() / 1000
                val payloadRtdb = mapOf(
                    "atualizar" to true,
                    "timestamp" to timestamp
                )
                rtdb.getReference(NODO_RTDB_CONTROLE).setValue(payloadRtdb).await()
                Log.d(TAG, ">> [RTDB] Gatilho disparado em $NODO_RTDB_CONTROLE com timestamp $timestamp")
            } catch (eRtdb: Exception) {
                Log.w(TAG, ">> [RTDB] Gatilho ignorado ou sem permissão direta: ${eRtdb.message}")
            }

            // 4. Sincronizar dispositivos vinculados via incremento de config_versao (padrão ESP32 v8)
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (authUser != null) {
                try {
                    DispositivoService().sincronizarDispositivos(authUser.uid)
                } catch (eDisp: Exception) {
                    Log.w(TAG, "Erro ao disparar sincronizarDispositivos: ${eDisp.message}")
                }
            }

            Result.success(limite)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar com hardware dev: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun obterBase64Imagem(
        context: Context,
        item: CategoriaItem,
        corCategoria: String
    ): String? {
        try {
            var bitmap: Bitmap? = null

            // 1. Tenta carregar do cache físico local gerenciado por MidiaLocalHelper
            val arquivoLocal = MidiaLocalHelper.obterArquivoLocal(context, item.id)
            if (arquivoLocal != null && arquivoLocal.exists() && arquivoLocal.length() > 0) {
                bitmap = BitmapFactory.decodeFile(arquivoLocal.absolutePath)
            }

            // 2. Tenta carregar via imageUri / imageUrl
            if (bitmap == null) {
                val uriStr = item.imageUrl ?: item.imageUri
                if (!uriStr.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(uriStr)
                        if (uri.scheme == "content" || uri.scheme == "file") {
                            val stream: InputStream? = context.contentResolver.openInputStream(uri)
                            bitmap = BitmapFactory.decodeStream(stream)
                            stream?.close()
                        } else if (uriStr.startsWith("/")) {
                            val f = File(uriStr)
                            if (f.exists()) bitmap = BitmapFactory.decodeFile(f.absolutePath)
                        }
                    } catch (ignored: Exception) {}
                }
            }

            // 3. Se não houver arquivo físico, gera um Bitmap nítido 240x240 com alto contraste para o OLED
            if (bitmap == null) {
                bitmap = gerarBitmapPictogramaFallback(item.rotulo, corCategoria)
            }

            // Recorta quadrado centralizado e redimensiona para 240x240
            val quadrado = imageProcessor.cortarERedimensionar(bitmap, LARGURA_IMG_ESP)
            val bos = ByteArrayOutputStream()
            // JPEG Baseline (compatível com TJpg_Decoder do ESP32 v8)
            quadrado.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            val bytes = bos.toByteArray()

            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao converter imagem do botão ${item.rotulo} para Base64", e)
            return null
        }
    }

    private fun obterBase64Audio(
        context: Context,
        item: CategoriaItem,
        slot: Int
    ): String? {
        try {
            // 1. Tenta carregar áudio físico gravado via microfone (m4a ou mp3)
            val arquivoLocal = MidiaLocalHelper.obterAudioLocal(context, item.id)
            if (arquivoLocal != null && arquivoLocal.exists() && arquivoLocal.length() > 0) {
                val bytes = arquivoLocal.readBytes()
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            // 2. Tenta carregar de caminho direto
            val caminho = item.audioUrl ?: item.audioPath ?: item.audio
            if (!caminho.isNullOrBlank()) {
                val f = File(caminho)
                if (f.exists() && f.length() > 0) {
                    val bytes = f.readBytes()
                    return Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }

            // 3. Fallback: gera um áudio WAV sintético válido (beep com tom distinto por slot)
            // para que o ESP32 sempre tenha som audível no MAX98357A ao acionar o botão
            val freq = when (slot) {
                1 -> 523.25 // Dó (C5)
                2 -> 659.25 // Mi (E5)
                3 -> 783.99 // Sol (G5)
                4 -> 880.00 // Lá (A5)
                else -> 1046.50 // Dó agudo (C6)
            }
            val wavBytes = gerarTomWavSintetico(frequencia = freq, duracaoSegundos = 0.45)
            return Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao converter áudio do botão ${item.rotulo} para Base64", e)
            return null
        }
    }

    /**
     * Gera um pictograma 240x240 com o rótulo legível e contraste otimizado
     * para o algoritmo de dithering do SSD1306 OLED (128x32).
     */
    private fun gerarBitmapPictogramaFallback(rotulo: String, corFundoHex: String): Bitmap {
        val bitmap = Bitmap.createBitmap(LARGURA_IMG_ESP, ALTURA_IMG_ESP, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fundo sólido
        val corBg = try { Color.parseColor(corFundoHex) } catch (_: Exception) { Color.parseColor("#2563EB") }
        canvas.drawColor(corBg)

        // Moldura branca interna
        val paintBorda = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }
        canvas.drawRect(12f, 12f, 228f, 228f, paintBorda)

        // Letra inicial grande no centro
        val paintLetra = Paint().apply {
            color = Color.WHITE
            textSize = 96f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        val inicial = rotulo.trim().take(1).uppercase()
        canvas.drawText(inicial.ifBlank { "C" }, 120f, 115f, paintLetra)

        // Rótulo completo na parte inferior
        val paintTexto = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        val rotuloCurto = if (rotulo.length > 12) rotulo.substring(0, 10) + ".." else rotulo
        canvas.drawText(rotuloCurto, 120f, 190f, paintTexto)

        return bitmap
    }

    /**
     * Gera um arquivo WAV PCM 16-bit Mono (44.1 kHz) sintético em memória.
     */
    private fun gerarTomWavSintetico(frequencia: Double, duracaoSegundos: Double): ByteArray {
        val sampleRate = 44100
        val totalSamples = (sampleRate * duracaoSegundos).toInt()
        val dataSize = totalSamples * 2 // 16 bits = 2 bytes por amostra
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Cabeçalho RIFF WAV
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size (16 para PCM)
        buffer.putShort(1) // AudioFormat (1 = PCM)
        buffer.putShort(1) // NumChannels (1 = Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // ByteRate
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        // Amostras senoidais com envelope suave para evitar cliques
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < 1000 -> i / 1000.0
                i > totalSamples - 1000 -> (totalSamples - i) / 1000.0
                else -> 1.0
            }
            val sampleVal = (Math.sin(2.0 * Math.PI * frequencia * t) * 32767 * 0.7 * envelope).toInt()
            buffer.putShort(sampleVal.coerceIn(-32768, 32767).toShort())
        }

        return buffer.array()
    }
}
