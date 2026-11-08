package fatec.labweb.projetocasa.service

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fatec.labweb.projetocasa.model.Acionamento
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class HistoricoService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun colecaoAcionamentos(userId: String) =
        firestore.collection("usuarios").document(userId).collection("acionamentos")

    suspend fun registrarAcionamento(
        userId: String,
        botaoId: String,
        botaoNome: String,
        categoriaNome: String,
        origem: String = "app",
        dispositivoMac: String? = null
    ): Result<Acionamento> {
        return try {
            val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val acionamento = Acionamento(
                botao_id = botaoId,
                botao_nome = botaoNome,
                categoria_nome = categoriaNome,
                dispositivo = dispositivoMac,
                origem = origem,
                acionado_em = formatoIso.format(Date())
            )

            val docId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            colecaoAcionamentos(userId).document(docId).set(acionamento).await()
            Result.success(acionamento)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listarAcionamentos(userId: String, limite: Long = 50): List<Acionamento> {
        return try {
            val snapshot = colecaoAcionamentos(userId)
                .orderBy("acionado_em", Query.Direction.DESCENDING)
                .limit(limite)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.toObject(Acionamento::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
