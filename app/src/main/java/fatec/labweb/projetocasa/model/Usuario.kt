package fatec.labweb.projetocasa.model

data class Usuario(
    var id: String = "",
    var nome: String = "",
    var email: String = ""
) {
    // Construtor vazio necessário para o Firebase conseguir converter os dados automaticamente
    constructor() : this("", "", "")
}