package fatec.labweb.projetocasa.dao

import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.api.CasaApiClient
import fatec.labweb.projetocasa.model.DispositivoModel
import org.json.JSONObject

class DispositivoDao {
    private val db = FirebaseFirestore.getInstance()

    /** Escuta em tempo real o documento status_casa/atual atualizado pela CASA-API no Firestore. */
    fun buscarStatus(callback: (DispositivoModel?) -> Unit) {
        db.collection("status_casa").document("atual")
            .addSnapshotListener { snapshot, _ ->
                val dados = snapshot?.toObject(DispositivoModel::class.java)
                callback(dados)
            }
    }

    /** Lista todos os dispositivos pareados do usuário via CASA-API (`GET /dispositivos`). */
    fun listarDispositivos(callback: (List<DispositivoModel>, String?) -> Unit) {
        CasaApiClient.listarDispositivos(callback)
    }

    /** Pareia um ESP32 pelo MAC via CASA-API (`POST /dispositivos`) e retorna o `device_token`. */
    fun parearDispositivo(
        mac: String,
        nome: String? = null,
        nomePaciente: String? = null,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        CasaApiClient.parearDispositivo(mac, nome, nomePaciente, callback)
    }

    /** Atualiza volume, brilho, modo ou nome do ESP32 (`PATCH /dispositivos/{mac}`). */
    fun configurarDispositivo(
        mac: String,
        volume: Int? = null,
        brilhoLeds: Int? = null,
        brilhoTelas: Int? = null,
        tempoInatividadeS: Int? = null,
        modo: String? = null,
        nome: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        val body = JSONObject().apply {
            if (volume != null) put("volume", volume)
            if (brilhoLeds != null) put("brilho_leds", brilhoLeds)
            if (brilhoTelas != null) put("brilho_telas", brilhoTelas)
            if (tempoInatividadeS != null) put("tempo_inatividade_s", tempoInatividadeS)
            if (modo != null) put("modo", modo)
            if (nome != null) put("nome", nome)
        }
        CasaApiClient.configurarDispositivo(mac, body, callback)
    }

    /** Desvincula um dispositivo da conta (`DELETE /dispositivos/{mac}`). */
    fun removerDispositivo(mac: String, callback: (Boolean, String?) -> Unit) {
        CasaApiClient.removerDispositivo(mac, callback)
    }
}
