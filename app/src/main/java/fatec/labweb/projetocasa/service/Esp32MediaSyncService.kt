package fatec.labweb.projetocasa.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.util.AudioPredefinidoHelper
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class MidiaUploadResult(
    val midiaId: String,
    val nomeArquivo: String,
    val sha256: String,
    val tamanho: Int
)

/**
 * Esp32MediaSyncService: Implementa o contrato rigoroso de publicação de mídias e prancha
 * para o hardware C.A.S.A. (ESP32-S3):
 *
 * 1. Subir blocos Base64 em /arquivos_midia/{arquivo}/blocos/0000, 0001...
 * 2. Gravar documento /arquivos_midia/{arquivo} { total_blocos, sha256, tamanho, arquivo }
 * 3. Gravar documento /usuarios/{uid}/midias/{imagem_id|audio_id} { arquivo, sha256, tamanho, tipo }
 * 4. Gravar documento /usuarios/{uid}/categorias/{id} { ordem, nome, imagem_id, botoes: [{ordem, id, rotulo, imagem_id, audio_id}] }
 * 5. Por último, disparar a flag no Realtime Database: /dispositivo/controle/{MAC} = { atualizar: true, timestamp: ServerValue.TIMESTAMP }
 */
class Esp32MediaSyncService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val triggerService: Esp32TriggerService = Esp32TriggerService()
) {

    companion object {
        private const val TAG = "Esp32MediaSyncService"
        const val LARGURA_ESP32 = 240
        const val ALTURA_ESP32 = 240
        const val QUALIDADE_JPEG = 85
        // Chunks de 192 KB (múltiplo de 3, sem padding intermediário em Base64)
        const val TAMANHO_BLOCO = 196608
        private const val PREFS_MIDIA_CACHE = "esp32_midia_cache"
    }

    private fun obterHashCache(context: Context, midiaId: String): String? {
        if (midiaId.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_MIDIA_CACHE, Context.MODE_PRIVATE)
        return prefs.getString("hash_$midiaId", null)
    }

    private fun salvarHashCache(context: Context, midiaId: String, sha256: String) {
        if (midiaId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_MIDIA_CACHE, Context.MODE_PRIVATE)
        prefs.edit().putString("hash_$midiaId", sha256).apply()
    }

    private suspend fun midiaJaExisteNoFirestore(userId: String, midiaId: String, sha256: String): Boolean {
        if (midiaId.isBlank()) return false
        return try {
            val doc = firestore.collection("usuarios").document(userId)
                .collection("midias").document(midiaId).get().await()
            doc.exists() && doc.getString("sha256") == sha256
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Processa e publica uma imagem no Firestore conforme o contrato:
     * - JPEG baseline 240x240
     * - Novo imagem_id (UUID) apenas se a imagem mudou ou for nova
     * - Fatiamento em blocos Base64 em /arquivos_midia/{arquivo}/blocos/%04d
     * - Metadados em /usuarios/{uid}/midias/{imagem_id}
     */
    suspend fun processarESalvarImagem(
        context: Context,
        userId: String,
        fonte: Any,
        idExistente: String? = null
    ): Result<MidiaUploadResult> = withContext(Dispatchers.IO) {
        try {
            val bitmapOriginal = extrairBitmap(context, fonte)
                ?: return@withContext Result.failure(IllegalArgumentException("Não foi possível carregar o bitmap da fonte."))

            // 1. Recorta quadrado central e redimensiona para 240x240 sem distorção
            val bitmap240 = cortarERedimensionar(bitmapOriginal, LARGURA_ESP32)

            // 2. Comprime para JPEG Baseline
            val bos = ByteArrayOutputStream()
            bitmap240.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, bos)
            val bytes = bos.toByteArray()

            // 3. Calcula SHA-256 (64 hex lowercase)
            val sha256 = calcularSha256(bytes)
            val tamanho = bytes.size

            // 4. Verificação de deduplicação / cache hit: se a mídia não mudou, NÃO envia de novo
            if (!idExistente.isNullOrBlank()) {
                val hashCache = obterHashCache(context, idExistente)
                val jaValido = if (hashCache == sha256) {
                    true
                } else {
                    midiaJaExisteNoFirestore(userId, idExistente, sha256)
                }
                if (jaValido) {
                    salvarHashCache(context, idExistente, sha256)
                    val nomeArquivoExistente = "$idExistente.jpg"
                    Log.d(TAG, "Imagem inalterada (cache hit): id=$idExistente, sha256=$sha256. Reenvio dispensado.")
                    return@withContext Result.success(MidiaUploadResult(idExistente, nomeArquivoExistente, sha256, tamanho))
                }
            }

            // 5. Mídia nova ou alterada: gera novo ID e nome de arquivo único
            val midiaId = "img_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val nomeArquivo = "$midiaId.jpg"
            salvarHashCache(context, midiaId, sha256)

            // 6. Salva em cache local
            salvarCacheLocal(context, nomeArquivo, bytes)

            // 7. Sobe blocos Base64 em /arquivos_midia/{arquivo}/blocos/0000, 0001...
            val totalBlocos = (tamanho + TAMANHO_BLOCO - 1) / TAMANHO_BLOCO
            val docArquivoRef = firestore.collection("arquivos_midia").document(nomeArquivo)

            for (idx in 0 until totalBlocos) {
                val inicio = idx * TAMANHO_BLOCO
                val fim = minOf(inicio + TAMANHO_BLOCO, tamanho)
                val fatia = bytes.copyOfRange(inicio, fim)
                val b64 = Base64.encodeToString(fatia, Base64.NO_WRAP)

                docArquivoRef.collection("blocos")
                    .document(String.format(Locale.US, "%04d", idx))
                    .set(mapOf("ordem" to idx, "dados_b64" to b64))
                    .await()
            }

            // 8. Grava documento de metadados do arquivo em /arquivos_midia/{arquivo}
            docArquivoRef.set(
                mapOf(
                    "arquivo" to nomeArquivo,
                    "tamanho" to tamanho,
                    "sha256" to sha256,
                    "total_blocos" to totalBlocos
                )
            ).await()

            // 9. Grava metadados da mídia do usuário em /usuarios/{uid}/midias/{midiaId}
            firestore.collection("usuarios").document(userId)
                .collection("midias").document(midiaId)
                .set(
                    mapOf(
                        "arquivo" to nomeArquivo,
                        "sha256" to sha256,
                        "tamanho" to tamanho,
                        "tipo" to "imagem"
                    )
                ).await()

            Log.d(TAG, "Imagem processada e salva: id=$midiaId, arquivo=$nomeArquivo, sha256=$sha256, tamanho=$tamanho")
            Result.success(MidiaUploadResult(midiaId, nomeArquivo, sha256, tamanho))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar imagem", e)
            Result.failure(e)
        }
    }

    /**
     * Processa e publica um áudio (MP3 ou WAV) no Firestore conforme o contrato:
     * - Áudio MP3 ou WAV mono
     * - Novo audio_id (UUID) apenas se o áudio mudou ou for novo
     * - Fatiamento em blocos Base64 em /arquivos_midia/{arquivo}/blocos/%04d
     * - Metadados em /usuarios/{uid}/midias/{audio_id}
     */
    suspend fun processarESalvarAudio(
        context: Context,
        userId: String,
        fonte: Any,
        idExistente: String? = null
    ): Result<MidiaUploadResult> = withContext(Dispatchers.IO) {
        try {
            val (bytes, extensao) = extrairBytesAudio(context, fonte)
                ?: return@withContext Result.failure(IllegalArgumentException("Não foi possível ler os bytes do áudio."))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Arquivo de áudio vazio."))
            }

            val sha256 = calcularSha256(bytes)
            val tamanho = bytes.size

            // Verificação de deduplicação / cache hit: se o áudio não mudou, NÃO envia de novo
            if (!idExistente.isNullOrBlank()) {
                val hashCache = obterHashCache(context, idExistente)
                val jaValido = if (hashCache == sha256) {
                    true
                } else {
                    midiaJaExisteNoFirestore(userId, idExistente, sha256)
                }
                if (jaValido) {
                    salvarHashCache(context, idExistente, sha256)
                    val nomeArquivoExistente = "$idExistente.$extensao"
                    Log.d(TAG, "Áudio inalterado (cache hit): id=$idExistente, sha256=$sha256. Reenvio dispensado.")
                    return@withContext Result.success(MidiaUploadResult(idExistente, nomeArquivoExistente, sha256, tamanho))
                }
            }

            // Mídia nova ou alterada: gera novo ID e nome de arquivo único
            val midiaId = "aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val nomeArquivo = "$midiaId.$extensao"
            salvarHashCache(context, midiaId, sha256)

            salvarCacheLocal(context, nomeArquivo, bytes)

            val totalBlocos = (tamanho + TAMANHO_BLOCO - 1) / TAMANHO_BLOCO
            val docArquivoRef = firestore.collection("arquivos_midia").document(nomeArquivo)

            for (idx in 0 until totalBlocos) {
                val inicio = idx * TAMANHO_BLOCO
                val fim = minOf(inicio + TAMANHO_BLOCO, tamanho)
                val fatia = bytes.copyOfRange(inicio, fim)
                val b64 = Base64.encodeToString(fatia, Base64.NO_WRAP)

                docArquivoRef.collection("blocos")
                    .document(String.format(Locale.US, "%04d", idx))
                    .set(mapOf("ordem" to idx, "dados_b64" to b64))
                    .await()
            }

            docArquivoRef.set(
                mapOf(
                    "arquivo" to nomeArquivo,
                    "tamanho" to tamanho,
                    "sha256" to sha256,
                    "total_blocos" to totalBlocos
                )
            ).await()

            firestore.collection("usuarios").document(userId)
                .collection("midias").document(midiaId)
                .set(
                    mapOf(
                        "arquivo" to nomeArquivo,
                        "sha256" to sha256,
                        "tamanho" to tamanho,
                        "tipo" to "audio"
                    )
                ).await()

            Log.d(TAG, "Áudio processado e salvo: id=$midiaId, arquivo=$nomeArquivo, sha256=$sha256, tamanho=$tamanho")
            Result.success(MidiaUploadResult(midiaId, nomeArquivo, sha256, tamanho))
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar áudio", e)
            Result.failure(e)
        }
    }

    /**
     * Sincroniza a prancha completa do usuário no Cloud Firestore e notifica os dispositivos via RTDB:
     * Pipeline estrito:
     * 1. Subir blocos
     * 2. arquivos_midia
     * 3. midias
     * 4. categorias (/usuarios/{uid}/categorias/{id})
     * 5. Disparar a flag em /dispositivo/controle/{MAC} para cada aparelho
     */
    suspend fun sincronizarPranchaCompleta(
        context: Context,
        userId: String,
        categorias: List<Categoria>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val limiteCategorias = minOf(categorias.size, 3)

            for (catIdx in 0 until limiteCategorias) {
                val cat = categorias[catIdx]
                cat.ordem = catIdx

                // 1. Processa imagem do ícone da categoria se houver
                var catImagemId = cat.imagem_id ?: ""
                val imgLocalCat = MidiaLocalHelper.obterArquivoLocal(context, cat.id)
                val fonteImgCat: Any? = when {
                    imgLocalCat != null && imgLocalCat.exists() -> imgLocalCat
                    !cat.localPath.isNullOrBlank() && File(cat.localPath!!).exists() -> File(cat.localPath!!)
                    DesenhoBotao.obterDrawable(cat.nome, cat.slug) != null -> DesenhoBotao.obterDrawable(cat.nome, cat.slug)
                    else -> null
                }

                if (fonteImgCat != null) {
                    val resImg = processarESalvarImagem(context, userId, fonteImgCat, cat.imagem_id)
                    if (resImg.isSuccess) {
                        catImagemId = resImg.getOrThrow().midiaId
                        cat.imagem_id = catImagemId
                    }
                }

                // 1.2 Processa áudio da categoria
                var catAudioId = cat.audio_id ?: ""
                val audioLocalCat = MidiaLocalHelper.obterAudioLocal(context, cat.id)
                val fonteAudioCat: Any? = when {
                    audioLocalCat != null && audioLocalCat.exists() -> audioLocalCat
                    !cat.audioUrl.isNullOrBlank() && File(cat.audioUrl!!).exists() -> File(cat.audioUrl!!)
                    else -> {
                        val audioPredef = AudioPredefinidoHelper.obterAudioPredefinido(context, cat.nome, cat.defaultGender)
                        if (audioPredef != null && audioPredef.exists()) {
                            MidiaLocalHelper.salvarAudioLocalmente(context, cat.id, audioPredef)
                            audioPredef
                        } else null
                    }
                }

                if (fonteAudioCat != null) {
                    val resAudio = processarESalvarAudio(context, userId, fonteAudioCat, cat.audio_id)
                    if (resAudio.isSuccess) {
                        catAudioId = resAudio.getOrThrow().midiaId
                        cat.audio_id = catAudioId
                    }
                }

                // 2. Processa cada botão da categoria (máx 5)
                val botoesLista = mutableListOf<Map<String, Any>>()
                val limiteBotoes = minOf(cat.botoes.size, 5)

                for (btnIdx in 0 until limiteBotoes) {
                    val btn = cat.botoes[btnIdx]
                    btn.ordem = btnIdx

                    // 2.1 Imagem do botão
                    var btnImagemId = btn.imagem_id ?: ""
                    val imgLocalBtn = MidiaLocalHelper.obterArquivoLocal(context, btn.id)
                    val fonteImgBtn: Any? = when {
                        imgLocalBtn != null && imgLocalBtn.exists() -> imgLocalBtn
                        !btn.localPath.isNullOrBlank() && File(btn.localPath!!).exists() -> File(btn.localPath!!)
                        DesenhoBotao.obterDrawable(btn.rotulo) != null -> DesenhoBotao.obterDrawable(btn.rotulo)
                        else -> null
                    }

                    if (fonteImgBtn != null) {
                        val resImg = processarESalvarImagem(context, userId, fonteImgBtn, btn.imagem_id)
                        if (resImg.isSuccess) {
                            btnImagemId = resImg.getOrThrow().midiaId
                            btn.imagem_id = btnImagemId
                        }
                    }

                    // 2.2 Áudio do botão
                    var btnAudioId = btn.audio_id ?: ""
                    val audioLocalBtn = MidiaLocalHelper.obterAudioLocal(context, btn.id)
                    val fonteAudioBtn: Any? = when {
                        audioLocalBtn != null && audioLocalBtn.exists() -> audioLocalBtn
                        !btn.audioUrl.isNullOrBlank() && File(btn.audioUrl!!).exists() -> File(btn.audioUrl!!)
                        else -> {
                            val audioPredef = AudioPredefinidoHelper.obterAudioPredefinido(context, btn.rotulo, btn.defaultGender)
                            if (audioPredef != null && audioPredef.exists()) audioPredef else null
                        }
                    }

                    if (fonteAudioBtn != null) {
                        val resAudio = processarESalvarAudio(context, userId, fonteAudioBtn, btn.audio_id)
                        if (resAudio.isSuccess) {
                            btnAudioId = resAudio.getOrThrow().midiaId
                            btn.audio_id = btnAudioId
                        }
                    }

                    botoesLista.add(
                        mapOf(
                            "ordem" to btnIdx,
                            "id" to btn.id,
                            "rotulo" to btn.rotulo,
                            "imagem_id" to btnImagemId,
                            "audio_id" to btnAudioId
                        )
                    )
                }

                // 3. Salva categoria no Firestore canônico: /usuarios/{uid}/categorias/{id}
                val docCategoriaMap = mapOf(
                    "id" to cat.id,
                    "nome" to cat.nome,
                    "slug" to cat.slug,
                    "cor" to cat.cor,
                    "ordem" to catIdx,
                    "imagem_id" to catImagemId,
                    "audio_id" to catAudioId,
                    "botoes" to botoesLista
                )

                firestore.collection("usuarios").document(userId)
                    .collection("categorias").document(cat.id)
                    .set(docCategoriaMap, SetOptions.merge())
                    .await()
            }

            // 4. Dispara a flag no Realtime Database para TODOS os dispositivos vinculados ao usuário
            // no nó oficial: /dispositivo/controle/{MAC} = { atualizar: true, timestamp: ServerValue.TIMESTAMP }
            val countDisparos = triggerService.dispararFlagParaTodosDispositivos(userId).getOrDefault(0)

            Log.d(TAG, "Prancha sincronizada com sucesso para $limiteCategorias categorias. Flags enviadas: $countDisparos")
            Result.success(countDisparos)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar prancha completa", e)
            Result.failure(e)
        }
    }

    private fun extrairBitmap(context: Context, fonte: Any): Bitmap? {
        return try {
            when (fonte) {
                is Bitmap -> fonte
                is File -> if (fonte.exists()) BitmapFactory.decodeFile(fonte.absolutePath) else null
                is Uri -> {
                    context.contentResolver.openInputStream(fonte)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                is Int -> {
                    val drawable = ContextCompat.getDrawable(context, fonte) ?: return null
                    if (drawable is BitmapDrawable && drawable.bitmap != null) {
                        drawable.bitmap
                    } else {
                        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 240
                        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 240
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                }
                is Drawable -> {
                    if (fonte is BitmapDrawable && fonte.bitmap != null) {
                        fonte.bitmap
                    } else {
                        val width = if (fonte.intrinsicWidth > 0) fonte.intrinsicWidth else 240
                        val height = if (fonte.intrinsicHeight > 0) fonte.intrinsicHeight else 240
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        fonte.setBounds(0, 0, canvas.width, canvas.height)
                        fonte.draw(canvas)
                        bmp
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extrairBytesAudio(context: Context, fonte: Any): Pair<ByteArray, String>? {
        return try {
            when (fonte) {
                is ByteArray -> {
                    val ext = if (fonte.size > 4 && fonte[0] == 'R'.code.toByte() && fonte[1] == 'I'.code.toByte()) "wav" else "mp3"
                    Pair(fonte, ext)
                }
                is File -> {
                    if (!fonte.exists()) return null
                    val bytes = fonte.readBytes()
                    val ext = when (fonte.extension.lowercase()) {
                        "wav" -> "wav"
                        else -> "mp3"
                    }
                    Pair(bytes, ext)
                }
                is Uri -> {
                    val stream: InputStream? = context.contentResolver.openInputStream(fonte)
                    val bytes = stream?.use { it.readBytes() } ?: return null
                    val ext = if (fonte.path?.endsWith(".wav", true) == true) "wav" else "mp3"
                    Pair(bytes, ext)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun salvarCacheLocal(context: Context, nomeArquivo: String, bytes: ByteArray) {
        try {
            val dir = File(context.filesDir, "uploads")
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, nomeArquivo)).use { fos ->
                fos.write(bytes)
            }
        } catch (_: Exception) {}
    }

    private fun cortarERedimensionar(src: Bitmap, targetSize: Int): Bitmap {
        val width = src.width
        val height = src.height
        val minDim = minOf(width, height)
        val xOffset = (width - minDim) / 2
        val yOffset = (height - minDim) / 2

        val quadrado = Bitmap.createBitmap(src, xOffset, yOffset, minDim, minDim)
        return if (minDim != targetSize) {
            Bitmap.createScaledBitmap(quadrado, targetSize, targetSize, true)
        } else {
            quadrado
        }
    }

    private fun calcularSha256(dados: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(dados)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
