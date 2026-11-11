package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Notificacao: Modelo de dados para alertas e avisos em tempo real do Projeto C.A.S.A.
 * Mapeia eventos da API Web (FastAPI) e mobile.
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
    val timestamp: Any? = null,
    var criada_em: String = "",       // Formato ISO 8601 da API Web (ex: 2026-04-12T15:30:00Z)
    var meta: Map<String, Any>? = null
) {
    @get:Exclude
    val timestampMillis: Long
        get() {
            if (data > 0L) return data
            if (criada_em.isNotBlank()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val limpa = criada_em.replace("Z", "").substringBefore(".")
                    return sdf.parse(limpa)?.time ?: 0L
                } catch (ignored: Exception) {}
            }
            return 0L
        }
}
