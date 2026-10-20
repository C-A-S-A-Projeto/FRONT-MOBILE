package fatec.labweb.projetocasa.dao

import fatec.labweb.projetocasa.api.CasaApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * DAO de Categorias, Botões e Mídias conectado à CASA-API (/categorias e /midias).
 */
class CategoriaDao {

    fun listarCategorias(callback: (JSONArray?, String?) -> Unit) {
        CasaApiClient.requestAutenticado("GET", "/categorias", null) { _, resp, err ->
            if (err != null || resp == null) {
                callback(null, err)
            } else {
                callback(JSONArray(resp), null)
            }
        }
    }

    fun criarCategoria(nome: String, cor: String? = null, callback: (JSONObject?, String?) -> Unit) {
        val body = JSONObject().apply {
            put("nome", nome)
            if (cor != null) put("cor", cor)
        }
        CasaApiClient.requestAutenticado("POST", "/categorias", body) { _, resp, err ->
            if (err != null || resp == null) callback(null, err)
            else callback(JSONObject(resp), null)
        }
    }

    fun renomearCategoria(idCategoria: String, nome: String, cor: String? = null, callback: (Boolean, String?) -> Unit) {
        val body = JSONObject().apply {
            put("nome", nome)
            if (cor != null) put("cor", cor)
        }
        CasaApiClient.requestAutenticado("PATCH", "/categorias/$idCategoria", body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun excluirCategoria(idCategoria: String, callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("DELETE", "/categorias/$idCategoria", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun criarBotao(
        idCategoria: String,
        rotulo: String,
        significado: String? = null,
        imagemId: String? = null,
        audioId: String? = null,
        callback: (JSONObject?, String?) -> Unit
    ) {
        val body = JSONObject().apply {
            put("rotulo", rotulo)
            if (significado != null) put("significado", significado)
            if (imagemId != null) put("imagem_id", imagemId)
            if (audioId != null) put("audio_id", audioId)
        }
        CasaApiClient.requestAutenticado("POST", "/categorias/$idCategoria/botoes", body) { _, resp, err ->
            if (err != null || resp == null) callback(null, err)
            else callback(JSONObject(resp), null)
        }
    }

    fun excluirBotao(idCategoria: String, idBotao: String, callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("DELETE", "/categorias/$idCategoria/botoes/$idBotao", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun enviarMidia(
        nomeArquivo: String,
        mimeType: String,
        bytes: ByteArray,
        tipo: String? = null,
        callback: (String?, String?) -> Unit
    ) {
        CasaApiClient.enviarMidia(nomeArquivo, mimeType, bytes, tipo, callback)
    }
}
