package fatec.labweb.projetocasa.dao

import fatec.labweb.projetocasa.api.CasaApiClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * DAO de Histórico, Desempenho e Notificações conectado à CASA-API.
 */
class HistoricoDao {

    fun listarHistorico(periodo: String = "tudo", busca: String = "", callback: (JSONArray?, String?) -> Unit) {
        val p = URLEncoder.encode(periodo, "UTF-8")
        val b = URLEncoder.encode(busca, "UTF-8")
        CasaApiClient.requestAutenticado("GET", "/historico?periodo=$p&busca=$b", null) { _, resp, err ->
            if (err != null || resp == null) callback(null, err)
            else callback(JSONArray(resp), null)
        }
    }

    fun registrarAcionamento(botaoId: String, callback: (Boolean, String?) -> Unit) {
        val body = JSONObject().apply { put("botao_id", botaoId) }
        CasaApiClient.requestAutenticado("POST", "/historico/acionamentos", body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun excluirItemHistorico(id: String, callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("DELETE", "/historico/$id", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun limparHistorico(callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("DELETE", "/historico", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun obterDesempenho(periodo: String = "semana", callback: (JSONObject?, String?) -> Unit) {
        val p = URLEncoder.encode(periodo, "UTF-8")
        CasaApiClient.requestAutenticado("GET", "/desempenho?periodo=$p", null) { _, resp, err ->
            if (err != null || resp == null) callback(null, err)
            else callback(JSONObject(resp), null)
        }
    }

    fun listarNotificacoes(callback: (JSONArray?, String?) -> Unit) {
        CasaApiClient.requestAutenticado("GET", "/notificacoes", null) { _, resp, err ->
            if (err != null || resp == null) callback(null, err)
            else callback(JSONArray(resp), null)
        }
    }

    fun marcarNotificacoesComoLidas(callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("POST", "/notificacoes/lidas", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun limparNotificacoes(callback: (Boolean, String?) -> Unit) {
        CasaApiClient.requestAutenticado("DELETE", "/notificacoes", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }
}
