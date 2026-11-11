package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Notificacao: Modelo de dados para alertas e avisos em tempo real do Projeto C.A.S.A.
 * Mapeia eventos de "dispositivo conectado", "nivelBateria" e "interacao em tempo real" (acionamento).
 */
@IgnoreExtraProperties
data class Notificacao(
    var id: String = "",
    val titulo: String = "",
    val mensagem: String = "",
    val data: Long = 0L,              // timestamp em milissegundos
    val lida: Boolean = false,
    val tipo: String = "info",        // "info", "clique", "aviso", "dispositivo", "bateria", "interacao"
    val dispositivoId: String = "",   // MAC ou ID do dispositivo
    val nivelBateria: Int? = null,    // Percentual de bateria se aplicável
    val statusOnline: Boolean? = null,// Status online do hardware
    val timestamp: Any? = null
)
