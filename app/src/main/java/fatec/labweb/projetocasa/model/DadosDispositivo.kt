package fatec.labweb.projetocasa.model

data class DadosDispositivo(
    val id: String = "",
    val nome: String = "",
    val tipo: String = "",
    val ativo: Boolean = false,

    // Clique
    val ultimoClique: Long = 0L,
    val totalCliques: Int = 0,

    // Personalização
    val imagemUrl: String = "",
    val audioUrl: String = "",
    val mensagem: String = "",
    val categoriaId: String = ""
)