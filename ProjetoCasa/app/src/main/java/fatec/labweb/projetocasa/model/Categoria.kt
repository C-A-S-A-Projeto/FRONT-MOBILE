package fatec.labweb.projetocasa.model


enum class GeneroAudio {
    MALE, FEMALE
}

data class CategoriaItem(
    val id: String = "",
    var title: String = "",
    var imageUri: String? = null,
    var audioPath: String? = null,
    var defaultGender: GeneroAudio = GeneroAudio.FEMALE
)

data class Categoria(
    val id: String = "",
    var name: String = "",
    var imageUri: String? = null,
    var audioPath: String? = null,
    var defaultGender: GeneroAudio = GeneroAudio.FEMALE,
    val buttons: MutableList<CategoriaItem> = mutableListOf()
)