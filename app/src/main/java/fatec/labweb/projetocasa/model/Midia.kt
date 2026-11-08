package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Midia(
    val id: String = "",
    val tipo: String = "imagem", // "imagem" ou "audio"
    val nome_original: String = "",
    val arquivo: String = "",
    val mime: String = "image/jpeg",
    val tamanho: Int = 0,
    val sha256: String = "",
    val criado_em: String = ""
)
