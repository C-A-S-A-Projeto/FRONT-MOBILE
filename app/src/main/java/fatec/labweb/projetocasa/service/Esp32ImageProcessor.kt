package fatec.labweb.projetocasa.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class ProcessamentoMidiaResult(
    val midiaId: String,
    val nomeArquivo: String,
    val arquivoLocal: File,
    val sha256: String,
    val tamanho: Int
)

class Esp32ImageProcessor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        const val LARGURA_ESP32 = 240
        const val ALTURA_ESP32 = 240
        const val QUALIDADE_JPEG = 85
        const val TAMANHO_BLOCO = 600 * 1024 // 600 KB
    }

    /**
     * Processa a imagem para o padrão ESP32 (JPEG Baseline 240x240),
     * salva no Firestore fatiada em blocos Base64 (/arquivos_midia),
     * registra metadados em /usuarios/{uid}/midias e mantém cópia local em cache.
     */
    suspend fun processarESalvarImagem(
        context: Context,
        userId: String,
        imagemUri: Uri,
        nomeOriginal: String = "imagem.jpg"
    ): Result<ProcessamentoMidiaResult> = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(imagemUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Não foi possível abrir o arquivo."))

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return@withContext Result.failure(IllegalArgumentException("Imagem corrompida ou formato inválido."))
            }

            // 1. Recorta quadrado centralizado e redimensiona para 240x240 sem distorção
            val bitmap240 = cortarERedimensionar(originalBitmap, LARGURA_ESP32)

            // 2. Comprime para JPEG Baseline (Android comprime em formato padrão baseline, 4:2:0)
            val outputStream = ByteArrayOutputStream()
            bitmap240.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, outputStream)
            val bytes = outputStream.toByteArray()

            // 3. Calcula SHA-256
            val sha256 = calcularSha256(bytes)

            // 4. Salva cópia local em filesDir/uploads
            val uploadsDir = File(context.filesDir, "uploads")
            if (!uploadsDir.exists()) uploadsDir.mkdirs()

            val nomeArquivo = "${UUID.randomUUID().toString().replace("-", "")}.jpg"
            val arquivoLocal = File(uploadsDir, nomeArquivo)
            FileOutputStream(arquivoLocal).use { fos ->
                fos.write(bytes)
            }

            // 5. Salva no Cloud Firestore na coleção /arquivos_midia em blocos Base64
            val totalBlocos = (bytes.size + TAMANHO_BLOCO - 1) / TAMANHO_BLOCO
            val docArquivoRef = firestore.collection("arquivos_midia").document(nomeArquivo)
            docArquivoRef.set(
                mapOf(
                    "arquivo" to nomeArquivo,
                    "tamanho" to bytes.size,
                    "sha256" to sha256,
                    "total_blocos" to totalBlocos
                )
            ).await()

            for (idx in 0 until totalBlocos) {
                val inicio = idx * TAMANHO_BLOCO
                val fim = minOf(inicio + TAMANHO_BLOCO, bytes.size)
                val fatia = bytes.copyOfRange(inicio, fim)
                val b64 = Base64.encodeToString(fatia, Base64.NO_WRAP)

                docArquivoRef.collection("blocos")
                    .document(String.format(Locale.US, "%04d", idx))
                    .set(
                        mapOf(
                            "ordem" to idx,
                            "dados_b64" to b64
                        )
                    ).await()
            }

            // 6. Registra metadados na subcoleção do usuário /usuarios/{uid}/midias
            val midiaId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val meta = mapOf(
                "tipo" to "imagem",
                "nome_original" to nomeOriginal,
                "arquivo" to nomeArquivo,
                "mime" to "image/jpeg",
                "tamanho" to bytes.size,
                "sha256" to sha256,
                "criado_em" to formatoIso.format(Date())
            )
            firestore.collection("usuarios")
                .document(userId)
                .collection("midias")
                .document(midiaId)
                .set(meta)
                .await()

            Result.success(
                ProcessamentoMidiaResult(
                    midiaId = midiaId,
                    nomeArquivo = nomeArquivo,
                    arquivoLocal = arquivoLocal,
                    sha256 = sha256,
                    tamanho = bytes.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recupera o arquivo em disco. Se não existir no cache local, remonta a partir dos blocos Base64 do Firestore.
     */
    suspend fun obterArquivoLocalOuRestaurar(
        context: Context,
        nomeArquivo: String
    ): File? = withContext(Dispatchers.IO) {
        val uploadsDir = File(context.filesDir, "uploads")
        if (!uploadsDir.exists()) uploadsDir.mkdirs()
        val local = File(uploadsDir, nomeArquivo)
        if (local.exists()) return@withContext local

        try {
            val doc = firestore.collection("arquivos_midia").document(nomeArquivo).get().await()
            if (!doc.exists()) return@withContext null

            val blocosSnap = firestore.collection("arquivos_midia")
                .document(nomeArquivo)
                .collection("blocos")
                .get()
                .await()

            val docsOrdenados = blocosSnap.documents.sortedBy { it.getLong("ordem") ?: 0L }
            val bos = ByteArrayOutputStream()
            for (bloco in docsOrdenados) {
                val b64 = bloco.getString("dados_b64") ?: continue
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                bos.write(bytes)
            }

            FileOutputStream(local).use { fos ->
                fos.write(bos.toByteArray())
            }
            local
        } catch (e: Exception) {
            null
        }
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
