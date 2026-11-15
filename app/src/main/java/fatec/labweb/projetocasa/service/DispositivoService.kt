package fatec.labweb.projetocasa.service

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.model.RedeWifiItem
import fatec.labweb.projetocasa.util.WifiCryptoHelper
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

    suspend fun obterDispositivoPorMac(mac: String): DadosDispositivo? = obterStatusDispositivo(mac)

    /**
     * Pareia o ESP32 através do código de 6 dígitos exibido no OLED
     * Segue exatamente as regras e fluxos de /dispositivos/parear da API Web FastAPI e do firmware v11.
     */
    suspend fun parearPorCodigo(
        codigo: String,
        nome: String,
        userId: String,
        chaveWifi: String? = null,
        redesReservas: List<RedeWifiItem>? = null
    ): Result<DadosDispositivo> {
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
            val dadosDisp = hashMapOf<String, Any>(
                "mac" to mac,
                "owner_uid" to userId,
                "config_versao" to 1,
                "modo" to "casa",
                "volume" to 70,
                "nome" to nomeFinal,
                "criado_em" to agora,
                "wifi_versao" to if (!redesReservas.isNullOrEmpty()) 1 else 0,
                "wifi_redes" to (redesReservas?.map { it.toMap() } ?: emptyList<Any>()),
                "statusOnline" to true
            )
            if (!chaveWifi.isNullOrBlank()) {
                dadosDisp["chave_wifi"] = chaveWifi
            }
            refDisp.set(dadosDisp, SetOptions.merge()).await()

            // 4. Remove o código de pareamento já utilizado
            try {
                refCodigo.delete().await()
            } catch (_: Exception) {}

            // 5. Adiciona MAC à lista de dispositivos vinculados do usuário (cria o documento se não existir)
            firestore.collection("usuarios").document(userId)
                .set(mapOf("dispositivosVinculados" to FieldValue.arrayUnion(mac)), SetOptions.merge())
                .await()

            // 6. Notificação no Firestore
            try {
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
            } catch (_: Exception) {}

            val dispCriado = refDisp.get().await().toObject(DadosDispositivo::class.java)
                ?: DadosDispositivo(mac = mac, nome = nomeFinal, owner_uid = userId)
            Result.success(dispCriado)
        } catch (e: Exception) {
            val erroMsg = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                e.message?.contains("insufficient permissions", ignoreCase = true) == true) {
                "Permissão negada no Firestore: Atualize as Regras no Console do Firebase (aba Firestore -> Regras) com o arquivo backend/firestore.rules."
            } else {
                e.message ?: "Falha ao parear dispositivo."
            }
            Result.failure(Exception(erroMsg, e))
        }
    }

    /**
     * Aguarda o ESP32 reiniciar, conectar ao Wi-Fi e publicar pareamentos/{pin} no Firestore.
     * Quando o documento aparecer, vincula o aparelho automaticamente sem nova ação do usuário.
     */
    suspend fun aguardarEFinalizarPareamento(
        pin: String,
        macConhecido: String,
        nomeAparelho: String,
        userId: String,
        chaveWifi: String? = null,
        redesReservas: List<RedeWifiItem>? = null,
        timeoutMs: Long = 75000,
        intervaloPollMs: Long = 2000,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<DadosDispositivo> {
        val inicio = System.currentTimeMillis()
        val codLimpo = pin.trim()
        val refCodigo = firestore.collection("pareamentos").document(codLimpo)

        while (System.currentTimeMillis() - inicio < timeoutMs) {
            val decorridoSegundos = (System.currentTimeMillis() - inicio) / 1000
            onStatusUpdate("Aguardando ESP32 conectar ao Wi-Fi (${decorridoSegundos}s)...")

            try {
                val snap = refCodigo.get().await()
                if (snap.exists()) {
                    onStatusUpdate("ESP32 conectado à nuvem! Vinculando à sua conta...")
                    return parearPorCodigo(codLimpo, nomeAparelho, userId, chaveWifi, redesReservas)
                }
            } catch (e: Exception) {
                if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                    e.message?.contains("insufficient permissions", ignoreCase = true) == true) {
                    return Result.failure(Exception("Permissão negada no Firestore: Atualize as Regras no Console do Firebase (aba Firestore -> Regras) com o arquivo backend/firestore.rules."))
                }
            }

            // Verifica se o documento do dispositivo já foi criado diretamente
            if (macConhecido.isNotBlank()) {
                try {
                    val snapDisp = firestore.collection("dispositivos").document(macConhecido.trim().uppercase()).get().await()
                    if (snapDisp.exists() && snapDisp.getString("owner_uid") == userId) {
                        val disp = snapDisp.toObject(DadosDispositivo::class.java)
                        if (disp != null) return Result.success(disp)
                    }
                } catch (_: Exception) {}
            }

            kotlinx.coroutines.delay(intervaloPollMs)
        }

        return Result.failure(
            IllegalStateException("Tempo limite excedido aguardando o ESP32 conectar à rede Wi-Fi. Verifique se o SSID e senha estão corretos e se o sinal da rede alcança o aparelho.")
        )
    }

    /**
     * Atualiza a lista de redes Wi-Fi (reservas de contingência) do dispositivo ESP32.
     * Cifra as senhas utilizando a chave AES do dispositivo e incrementa wifi_versao no Firestore.
     */
    suspend fun atualizarRedesWifi(
        mac: String,
        redes: List<RedeWifiItem>,
        chaveWifiB64: String
    ): Result<Unit> {
        val macLimpo = mac.trim().uppercase()
        if (redes.isEmpty() || redes.size > 4) {
            return Result.failure(IllegalArgumentException("A lista de redes reservas deve conter entre 1 e 4 redes Wi-Fi."))
        }

        return try {
            val ssidsVistos = mutableSetOf<String>()
            val redesProcessadas = mutableListOf<Map<String, Any>>()

            for (rede in redes) {
                val ssidLimpo = rede.ssid.trim()
                if (ssidLimpo.isEmpty() || ssidLimpo.toByteArray(Charsets.UTF_8).size > 32) {
                    return Result.failure(IllegalArgumentException("O SSID '$ssidLimpo' deve ter entre 1 e 32 bytes."))
                }
                if (!ssidsVistos.add(ssidLimpo)) {
                    return Result.failure(IllegalArgumentException("O SSID '$ssidLimpo' está duplicado na lista."))
                }

                val senhaCifrada = if (rede.senhaCifrada.isNotBlank()) {
                    rede.senhaCifrada
                } else if (rede.senha.isNotEmpty()) {
                    if (rede.senha.length !in 8..63) {
                        return Result.failure(IllegalArgumentException("A senha da rede '$ssidLimpo' deve ter entre 8 e 63 caracteres."))
                    }
                    WifiCryptoHelper.cifrarSenhaWifi(rede.senha, chaveWifiB64)
                } else {
                    "" // Rede aberta
                }

                redesProcessadas.add(
                    mapOf(
                        "ssid" to ssidLimpo,
                        "senha_cifrada" to senhaCifrada
                    )
                )
            }

            val docRef = firestore.collection("dispositivos").document(macLimpo)
            val updates = mapOf(
                "wifi_redes" to redesProcessadas,
                "wifi_versao" to FieldValue.increment(1)
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtém as redes Wi-Fi cadastradas no documento do dispositivo
     */
    suspend fun obterRedesWifi(mac: String): Result<List<RedeWifiItem>> {
        return try {
            val snap = firestore.collection("dispositivos").document(mac.trim().uppercase()).get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Dispositivo não encontrado."))
            }
            @Suppress("UNCHECKED_CAST")
            val redesRaw = snap.get("wifi_redes") as? List<Map<String, Any>> ?: emptyList()
            val lista = redesRaw.mapIndexed { idx, map ->
                RedeWifiItem(
                    ssid = map["ssid"] as? String ?: "",
                    senhaCifrada = map["senha_cifrada"] as? String ?: "",
                    principal = (idx == 0)
                )
            }
            Result.success(lista)
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
                .set(mapOf("dispositivosVinculados" to FieldValue.arrayRemove(mac)), SetOptions.merge())
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
