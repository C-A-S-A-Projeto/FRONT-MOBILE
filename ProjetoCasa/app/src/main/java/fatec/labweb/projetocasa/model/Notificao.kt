package fatec.labweb.projetocasa.model

data class Notificacao(
    val id: String = "",
    val titulo: String = "",
    val mensagem: String = "",
    val data: Long = 0L,             // timestamp em milissegundos
    val lida: Boolean = false,
    val tipo: String = "info",       // "info", "clique", "aviso"
    val dispositivoId: String = ""   // "" = não é de um botão específico
)