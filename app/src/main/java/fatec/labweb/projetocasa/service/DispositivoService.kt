package fatec.labweb.projetocasa.service

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fatec.labweb.projetocasa.model.DadosDispositivo
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DispositivoService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val formatoIso: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

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

    /**
     * Pareia o ESP32 através do código de 6 dígitos exibido no OLED
     * Segue exatamente as regras e fluxos de /dispositivos/parear da API Web FastAPI e do firmware v8.
     */
    suspend fun parearPorCodigo(codigo: String, nome: String, userId: String): Result<DadosDispositivo> {
        val codLimpo = codigo.trim()
        if (!codLimpo.matches(Regex("^[0-9]{6}$"))) {
            return Result.failure(IllegalArgumentException("O código de pareamento deve conter exatamente 6 dígitos numéricos."))
        }

        return try {
            // 1. Busca código temporário gravado pelo ESP32
            val refCodigo = firestore.collection("pareamentos").document(codLimpo)
            val snapCodigo = refCodigo.get().await()
            if (!snapCodigo.exists()) {
                return Result.failure(IllegalStateException("Código de pareamento inválido ou expirado. Verifique o visor do aparelho."))
            }

            val mac = (snapCodigo.getString("mac") ?: "").trim().uppercase()
            if (mac.length != 17) {
                try { refCodigo.delete().await() } catch (_: Exception) {}
                return Result.failure(IllegalStateException("Registro de pareamento corrompido no aparelho."))
            }

            // 2. Verifica se o aparelho já pertence a outra conta
            val refDisp = firestore.collection("dispositivos").document(mac)
            val snapDisp = refDisp.get().await()
            if (snapDisp.exists()) {
                val donoAtual = snapDisp.getString("owner_uid")
                if (donoAtual != null && donoAtual != userId) {
                    return Result.failure(IllegalStateException("Este dispositivo já está vinculado a outra conta."))
                }
            }

            // 3. Criação ou atualização do vínculo
            val agora = formatoIso.format(Date())
            val nomeFinal = nome.trim().ifBlank { "ESP32 - C.A.S.A." }
            val dadosDisp = hashMapOf(
                "mac" to mac,
                "owner_uid" to userId,
                "config_versao" to 1,
                "modo" to "casa",
                "volume" to 70,
                "nome" to nomeFinal,
                "criado_em" to agora,
                "wifi_versao" to 0,
                "wifi_redes" to emptyList<Any>(),
                "statusOnline" to true
            )
            refDisp.set(dadosDisp, SetOptions.merge()).await()

            // 4. Remove o código de pareamento já utilizado
            try {
                refCodigo.delete().await()
            } catch (_: Exception) {}

            // 5. Adiciona MAC à lista de dispositivos vinculados do usuário
            firestore.collection("usuarios").document(userId)
                .update("dispositivosVinculados", FieldValue.arrayUnion(mac))
                .await()

            // 6. Notificação no Firestore
            val notif = hashMapOf(
                "tipo" to "pareamento",
                "titulo" to "Dispositivo Conectado",
                "mensagem" to "$nomeFinal pareado com sucesso.",
                "criada_em" to agora,
                "lida" to false
            )
            firestore.collection("usuarios").document(userId)
                .collection("notificacoes")
                .add(notif)
                .await()

            val dispCriado = refDisp.get().await().toObject(DadosDispositivo::class.java)
                ?: DadosDispositivo(mac = mac, nome = nomeFinal, owner_uid = userId)
            Result.success(dispCriado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atualiza nome e volume (0-100) do dispositivo
     */
    suspend fun atualizarDispositivo(mac: String, nome: String, volume: Int): Result<Unit> {
        return try {
            val volumeValido = volume.coerceIn(0, 100)
            val atualizacao = mapOf(
                "nome" to nome.trim(),
                "volume" to volumeValido
            )
            firestore.collection("dispositivos").document(mac)
                .update(atualizacao)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove / Desvincula o dispositivo da conta do usuário
     */
    suspend fun removerDispositivo(mac: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("dispositivos").document(mac).delete().await()
            firestore.collection("usuarios").document(userId)
                .update("dispositivosVinculados", FieldValue.arrayRemove(mac))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Incrementa config_versao (bump_dispositivos) para forçar o ESP32 a sincronizar
     */
    suspend fun sincronizarDispositivos(userId: String): Result<Int> {
        return try {
            val snapshot = firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            var count = 0
            for (doc in snapshot.documents) {
                doc.reference.update("config_versao", FieldValue.increment(1)).await()
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
