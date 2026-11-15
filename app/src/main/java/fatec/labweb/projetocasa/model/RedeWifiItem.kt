package fatec.labweb.projetocasa.model

/**
 * Representa uma rede Wi-Fi configurada ou em edição para o hardware ESP32.
 * A posição 0 no hardware é a rede Principal; as posições 1 a 4 são redes Reservas.
 */
data class RedeWifiItem(
    val ssid: String = "",
    val senha: String = "",
    val senhaCifrada: String = "",
    val principal: Boolean = false
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "ssid" to ssid.trim(),
            "senha_cifrada" to senhaCifrada
        )
    }
}
