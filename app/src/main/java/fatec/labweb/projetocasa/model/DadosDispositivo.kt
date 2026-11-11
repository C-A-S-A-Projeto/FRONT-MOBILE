package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.IgnoreExtraProperties

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
}
