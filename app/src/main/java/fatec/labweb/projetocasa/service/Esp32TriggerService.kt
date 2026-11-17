package fatec.labweb.projetocasa.service

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Esp32TriggerService: Gerencia o disparo de flags de controle no Firebase Realtime Database
 * conforme o contrato oficial do dispositivo C.A.S.A. (ESP32-S3):
 *
 * Realtime Database: /dispositivo/controle/{MAC} = { atualizar: true, timestamp: ServerValue.TIMESTAMP }
 * ({MAC} igual ao id de dispositivos/{MAC}: maiúsculas, com ':').
 */
class Esp32TriggerService(
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "Esp32TriggerService"
    }

    /**
     * Dispara a flag de atualização para um dispositivo específico pelo MAC.
     * Grava no nó oficial: /dispositivo/controle/{MAC}
     */
    suspend fun dispararFlagMac(mac: String): Result<Unit> {
        val macNormalizado = mac.trim().uppercase()
        if (macNormalizado.isBlank()) {
            return Result.failure(IllegalArgumentException("MAC do dispositivo inválido"))
        }

        return try {
            val payload = mapOf(
                "atualizar" to true,
                "timestamp" to ServerValue.TIMESTAMP
            )
            rtdb.getReference("dispositivo/controle/$macNormalizado").setValue(payload).await()
            Log.d(TAG, "Flag RTDB disparada com sucesso em /dispositivo/controle/$macNormalizado")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar flag RTDB para MAC $macNormalizado", e)
            Result.failure(e)
        }
    }

    /**
     * Dispara a flag de atualização para TODOS os dispositivos vinculados ao usuário.
     * Consulta dispositivos/{MAC} onde owner_uid == userId e aciona /dispositivo/controle/{MAC}.
     * Também aciona /dispositivo/controle para compatibilidade.
     */
    suspend fun dispararFlagParaTodosDispositivos(userId: String): Result<Int> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("userId inválido"))
        }

        return try {
            var disparos = 0
            val snaps = firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            for (doc in snaps.documents) {
                val mac = (doc.getString("mac") ?: doc.id).trim().uppercase()
                if (mac.isNotBlank()) {
                    dispararFlagMac(mac)
                    disparos++
                }
            }

            // Fallback global em /dispositivo/controle para firmwares em transição
            try {
                val payloadGlobal = mapOf(
                    "atualizar" to true,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                rtdb.getReference("dispositivo/controle").setValue(payloadGlobal).await()
            } catch (_: Exception) {}

            Log.d(TAG, "Flags disparadas para $disparos dispositivos do usuario $userId")
            Result.success(disparos)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar flags para dispositivos do usuario $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Mantém assinatura legada direcionando para todos os dispositivos ou nó geral
     */
    suspend fun notificarAtualizacao(): Result<Unit> {
        return try {
            val payload = mapOf(
                "atualizar" to true,
                "timestamp" to ServerValue.TIMESTAMP
            )
            rtdb.getReference("dispositivo/controle").setValue(payload).await()
            Log.d(TAG, "Gatilho global RTDB enviado em /dispositivo/controle")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar gatilho global no RTDB", e)
            Result.failure(e)
        }
    }

    /**
     * Monitora em tempo real a flag de um dispositivo no RTDB (/dispositivo/controle/{MAC}/atualizar).
     * O ESP32 grava atualizar=false assim que conclui a varredura e download do Firestore.
     */
    fun monitorarStatusSincronizacao(
        mac: String,
        onStatusChange: (sincronizado: Boolean) -> Unit
    ): ValueEventListener {
        val macNorm = mac.trim().uppercase()
        val ref = rtdb.getReference("dispositivo/controle/$macNorm/atualizar")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pendente = snapshot.getValue(Boolean::class.java) ?: false
                onStatusChange(!pendente)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Monitoramento cancelado para $macNorm: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removerMonitoramento(mac: String, listener: ValueEventListener) {
        val macNorm = mac.trim().uppercase()
        rtdb.getReference("dispositivo/controle/$macNorm/atualizar").removeEventListener(listener)
    }
}
