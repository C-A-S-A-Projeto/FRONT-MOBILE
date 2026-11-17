package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@IgnoreExtraProperties
data class DadosDispositivo(
    val id: String = "",
    val mac: String = "",
    val nome: String = "",
    val tipo: String = "",
    val ativo: Boolean = false,
    val statusOnline: Boolean = false,
    val nivelBateria: Int = 100,

    // Métricas
    val ultimoClique: Long = 0L,
    val totalCliques: Int = 0,

    // Telemetria e compatibilidade CASA-API
    val owner_uid: String = "",
    val modo: String = "casa",
    val config_versao: Int = 1,
    val ultima_sincronizacao: String = "",

    // Conectividade e Hardware v7+
    val ssid: String? = null,
    val sinal_wifi: Int? = null,
    val ip: String? = null,
    val firmware: String? = null,
    val bateria_pct: Int? = null,
    val volume: Int? = null,

    // Gestão de Wi-Fi no dispositivo (v7+)
    val wifi_versao: Int = 1,
    val wifi_versao_aplicada: Int = 1,
    val wifi_status: String = "ok",
    val wifi_erro: String? = null,
    val wifi_redes: List<Map<String, Any>> = emptyList(),
    val chave_wifi: String? = null,
    val criado_em: String? = null,

    // Personalização
    val imagemUrl: String = "",
    val audioUrl: String = "",
    val mensagem: String = "",
    val categoriaId: String = ""
) {
    // Bateria efetiva priorizando telemetria da Web API (bateria_pct) e fallback para nivelBateria
    val bateriaEfetiva: Int
        get() = bateria_pct ?: nivelBateria

    /**
     * Calcula se o hardware ESP32 está Online com base no timestamp de 'ultima_sincronizacao'.
     * Conforme o contrato C.6: "online se ultima_sincronizacao < 8 min" (480.000 ms).
     */
    val isOnline: Boolean
        get() {
            if (ultima_sincronizacao.isBlank()) {
                return false
            }

            // 1. Caso seja epoch numérico
            val epochNumerico = ultima_sincronizacao.toLongOrNull()
            if (epochNumerico != null) {
                val epochMs = if (epochNumerico < 10_000_000_000L) epochNumerico * 1000L else epochNumerico
                val diffMs = System.currentTimeMillis() - epochMs
                return diffMs in -120_000L..(8 * 60 * 1000L)
            }

            // 2. Caso seja ISO 8601 (padrão do firmware: 2026-11-15T04:09:40Z)
            return try {
                val formatos = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                )
                var dataSync: Date? = null
                for (fmt in formatos) {
                    try {
                        dataSync = fmt.parse(ultima_sincronizacao)
                        if (dataSync != null) break
                    } catch (_: Exception) {}
                }
                if (dataSync == null) return false
                val diffMs = System.currentTimeMillis() - dataSync.time
                diffMs in -120_000L..(8 * 60 * 1000L)
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Conforme contrato C.6: "Wi-Fi aplicado quando wifi_versao_aplicada == wifi_versao e wifi_status == 'ok'"
     */
    val wifiAplicado: Boolean
        get() = wifi_versao_aplicada == wifi_versao && wifi_status.equals("ok", ignoreCase = true)
}
