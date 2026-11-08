package fatec.labweb.projetocasa.service

import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.DadosDispositivo
import kotlinx.coroutines.tasks.await

class DispositivoService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun listarDispositivosDoUsuario(userId: String): List<DadosDispositivo> {
        return try {
            val snapshot = firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(DadosDispositivo::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun obterStatusDispositivo(mac: String): DadosDispositivo? {
        return try {
            val doc = firestore.collection("dispositivos").document(mac).get().await()
            doc.toObject(DadosDispositivo::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
