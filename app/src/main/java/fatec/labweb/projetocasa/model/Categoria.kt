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
    var tipo_audio: String? = null,
    var ordem: Int = 0,
    @get:Exclude var defaultGender: GeneroAudio = GeneroAudio.FEMALE,
    @get:Exclude var imageUrl: String? = null,
    @get:Exclude var audioUrl: String? = null,
    @get:Exclude var localPath: String? = null
) {
    @get:Exclude
    var title: String
        get() = rotulo
        set(value) {
            rotulo = value
            if (significado.isBlank()) significado = value
        }

    @get:Exclude
    var imageUri: String?
        get() = imageUrl ?: imagem ?: imagem_id
        set(value) {
            imageUrl = value
            imagem = value
            imagem_id = value
        }

    @get:Exclude
    var audioPath: String?
        get() = audioUrl ?: audio ?: audio_id
        set(value) {
            audioUrl = value
            audio = value
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
    var imagem_id: String? = null,
    var imagem: String? = null,
    var audio_id: String? = null,
    var audio: String? = null,
    var tipo_audio: String? = null,
    var emoji: String? = null,
    val botoes: MutableList<CategoriaItem> = mutableListOf(),
    @get:Exclude var imageUri: String? = null,
    @get:Exclude var audioPath: String? = null,
    @get:Exclude var defaultGender: GeneroAudio = GeneroAudio.FEMALE,
    @get:Exclude var imageUrl: String? = null,
    @get:Exclude var audioUrl: String? = null,
    @get:Exclude var localPath: String? = null
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
