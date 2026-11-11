package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Usuario(
    val uid: String = "",
    val id: String = "",
    val nome: String = "",
    val email: String = "",
    @get:Exclude
    val senha: String = "",
    val criado_em: String = "",
    val dispositivos: List<String> = emptyList(),
    val dispositivosVinculados: List<String> = emptyList(),
    val notificacoes_ativas: Boolean = true,
    val modo_escuro: Boolean = false,
    val tamanho_fonte: Int = 10,
    val feedback_sonoro: Boolean = true,
    val audio_cues: Boolean = true
) {
    @get:Exclude
    val idUsuario: String
        get() = uid.ifBlank { id }
}
