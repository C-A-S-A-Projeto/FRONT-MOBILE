package fatec.labweb.projetocasa.service

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class Esp32TriggerService {

    private val rtdb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    /**
     * Dispara o alerta no Realtime Database no nó /dispositivo/controle
     * para que o ESP32 recarregue imediatamente as telas, mídias e configurações.
     */
    suspend fun notificarAtualizacao(): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis() / 1000
            val payload = mapOf(
                "atualizar" to true,
                "timestamp" to timestamp
            )
            rtdb.getReference("dispositivo/controle").setValue(payload).await()
            Log.d("Esp32TriggerService", "Gatilho de atualizacao enviado ao RTDB com sucesso: timestamp=$timestamp")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("Esp32TriggerService", "Erro ao disparar gatilho no RTDB", e)
            Result.failure(e)
        }
    }
}
