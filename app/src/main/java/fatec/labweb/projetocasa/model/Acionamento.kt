package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Acionamento(
    val botao_id: String = "",
    val botao_nome: String = "",
    val categoria_nome: String = "",
    val dispositivo: String? = null,
    val origem: String = "app", // "app" ou "dispositivo"
    val acionado_em: String = ""
)
