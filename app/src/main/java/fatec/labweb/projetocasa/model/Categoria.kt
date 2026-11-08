package fatec.labweb.projetocasa.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

enum class GeneroAudio {
    MALE, FEMALE
}

@IgnoreExtraProperties
data class CategoriaItem(
    val id: String = "",
    var rotulo: String = "",
    var significado: String = "",
    var imagem_id: String? = null,
    var imagem: String? = null,
    var audio_id: String? = null,
    var audio: String? = null,
    var ordem: Int = 0,
    var defaultGender: GeneroAudio = GeneroAudio.FEMALE
) {
    // Propriedades auxiliares para compatibilidade com o código de tela existente
    @get:Exclude
    var title: String
        get() = rotulo
        set(value) {
            rotulo = value
            if (significado.isBlank()) significado = value
        }

    @get:Exclude
    var imageUri: String?
        get() = imagem_id ?: imagem
        set(value) {
            imagem_id = value
        }

    @get:Exclude
    var audioPath: String?
        get() = audio_id ?: audio
        set(value) {
            audio_id = value
        }
}

@IgnoreExtraProperties
data class Categoria(
    val id: String = "",
    var nome: String = "",
    var slug: String = "",
    var cor: String = "#4A90E2",
    var ordem: Int = 0,
    val botoes: MutableList<CategoriaItem> = mutableListOf(),
    // Propriedades legadas mantidas para não quebrar referências
    var imageUri: String? = null,
    var audioPath: String? = null,
    var defaultGender: GeneroAudio = GeneroAudio.FEMALE
) {
    @get:Exclude
    var name: String
        get() = nome
        set(value) {
            nome = value
        }

    @get:Exclude
    val buttons: MutableList<CategoriaItem>
        get() = botoes
}
