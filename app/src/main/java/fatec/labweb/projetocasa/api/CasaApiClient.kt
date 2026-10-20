package fatec.labweb.projetocasa.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.DispositivoModel
import fatec.labweb.projetocasa.model.Usuario
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Cliente HTTP completo do FRONT-MOBILE para comunicação com a CASA-API.
 * Autentica automaticamente todas as rotas privadas com o ID Token do Firebase Auth.
 */
object CasaApiClient {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var baseUrl: String = "http://10.0.2.2:8000"

    fun init(context: Context) {
        baseUrl = context.getString(R.string.casa_api_url).trimEnd('/')
    }

    private fun obterTokenFirebase(callback: (String?, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            callback(null, "Usuário não autenticado.")
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val token = result.token
                if (token.isNullOrBlank()) {
                    callback(null, "Não foi possível obter o token do Firebase.")
                } else {
                    callback(token, null)
                }
            }
            .addOnFailureListener { e ->
                callback(null, e.message ?: "Erro ao obter token do Firebase.")
            }
    }

    private fun extrairErro(code: Int, responseText: String): String {
        return try {
            val json = JSONObject(responseText)
            when (val detail = json.opt("detail")) {
                is JSONObject -> detail.optString("mensagem", "Erro ($code)")
                is String -> detail
                is JSONArray -> detail.optJSONObject(0)?.optString("msg") ?: "Dados inválidos"
                else -> "Erro na API ($code)"
            }
        } catch (_: Exception) {
            if (responseText.isNotBlank()) responseText else "Erro na API ($code)"
        }
    }

    fun requestRaw(
        method: String,
        path: String,
        token: String?,
        body: JSONObject? = null,
        callback: (Int, String?, String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl$path")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        outputStream.use { os ->
                            os.write(body.toString().toByteArray(Charsets.UTF_8))
                        }
                    }
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""

                if (code in 200..299) {
                    mainHandler.post { callback(code, responseText, null) }
                } else {
                    val msg = extrairErro(code, responseText)
                    mainHandler.post { callback(code, null, msg) }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    callback(0, null, "Sem conexão com a CASA-API ($baseUrl).")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun requestAutenticado(
        method: String,
        path: String,
        body: JSONObject? = null,
        callback: (Int, String?, String?) -> Unit
    ) {
        obterTokenFirebase { token, erroToken ->
            if (erroToken != null || token == null) {
                mainHandler.post { callback(401, null, erroToken ?: "Não autenticado") }
                return@obterTokenFirebase
            }
            requestRaw(method, path, token, body, callback)
        }
    }

    // ───────────── Saúde e Autenticação (/saude, /auth/*) ─────────────

    fun verificarSaude(callback: (Boolean, String?) -> Unit) {
        requestRaw("GET", "/saude", null, null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun iniciarCadastro(nome: String, email: String, senha: String, callback: (Boolean, String?) -> Unit) {
        val body = JSONObject().apply {
            put("nome", nome)
            put("email", email)
            put("senha", senha)
        }
        requestRaw("POST", "/auth/cadastro", null, body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun confirmarCadastro(email: String, codigo: String, senha: String, callback: (Boolean, String?) -> Unit) {
        val body = JSONObject().apply {
            put("email", email)
            put("codigo", codigo)
            put("senha", senha)
        }
        requestRaw("POST", "/auth/confirmar", null, body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun reenviarCodigo(email: String, callback: (Boolean, String?) -> Unit) {
        val body = JSONObject().apply { put("email", email) }
        requestRaw("POST", "/auth/reenviar-codigo", null, body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    // ───────────── Perfil (/me) ─────────────

    fun sincronizarPerfil(callback: (Usuario?, String?) -> Unit) {
        requestAutenticado("GET", "/me", null) { _, resp, err ->
            if (err != null || resp == null) {
                callback(null, err)
                return@requestAutenticado
            }
            try {
                val json = JSONObject(resp)
                callback(
                    Usuario(
                        id = json.optString("uid"),
                        nome = json.optString("nome"),
                        email = json.optString("email")
                    ),
                    null
                )
            } catch (e: Exception) {
                callback(null, e.message)
            }
        }
    }

    fun atualizarPerfil(
        nome: String? = null,
        notificacoesAtivas: Boolean? = null,
        modoEscuro: Boolean? = null,
        tamanhoFonte: Int? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        val body = JSONObject().apply {
            if (nome != null) put("nome", nome)
            if (notificacoesAtivas != null) put("notificacoes_ativas", notificacoesAtivas)
            if (modoEscuro != null) put("modo_escuro", modoEscuro)
            if (tamanhoFonte != null) put("tamanho_fonte", tamanhoFonte)
        }
        requestAutenticado("PATCH", "/me", body) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun excluirConta(callback: (Boolean, String?) -> Unit) {
        requestAutenticado("DELETE", "/me", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    // ───────────── Dispositivos (/dispositivos) ─────────────

    fun listarDispositivos(callback: (List<DispositivoModel>, String?) -> Unit) {
        requestAutenticado("GET", "/dispositivos", null) { _, resp, err ->
            if (err != null || resp == null) {
                callback(emptyList(), err)
                return@requestAutenticado
            }
            try {
                val arr = JSONArray(resp)
                val lista = mutableListOf<DispositivoModel>()
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    lista.add(
                        DispositivoModel(
                            id = d.optString("mac"),
                            status = if (d.optBoolean("online", false)) "online" else "offline",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                callback(lista, null)
            } catch (e: Exception) {
                callback(emptyList(), e.message)
            }
        }
    }

    fun parearDispositivo(
        mac: String,
        nome: String? = null,
        nomePaciente: String? = null,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        val body = JSONObject().apply {
            put("mac", mac)
            if (!nome.isNullOrBlank()) put("nome", nome)
            if (!nomePaciente.isNullOrBlank()) put("nome_paciente", nomePaciente)
        }
        requestAutenticado("POST", "/dispositivos", body) { code, resp, err ->
            if (code in 200..299 && resp != null) {
                val deviceToken = try {
                    JSONObject(resp).optString("device_token")
                } catch (_: Exception) {
                    null
                }
                callback(true, deviceToken, null)
            } else {
                callback(false, null, err)
            }
        }
    }

    fun configurarDispositivo(
        mac: String,
        ajustes: JSONObject,
        callback: (Boolean, String?) -> Unit
    ) {
        val macEnc = URLEncoder.encode(mac, "UTF-8")
        requestAutenticado("PATCH", "/dispositivos/$macEnc", ajustes) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    fun removerDispositivo(mac: String, callback: (Boolean, String?) -> Unit) {
        val macEnc = URLEncoder.encode(mac, "UTF-8")
        requestAutenticado("DELETE", "/dispositivos/$macEnc", null) { code, _, err ->
            callback(code in 200..299, err)
        }
    }

    // ───────────── Categorias, Botões e Mídias (/categorias, /midias) ─────────────

    fun enviarMidia(
        nomeArquivo: String,
        mimeType: String,
        bytes: ByteArray,
        tipo: String? = null,
        callback: (String?, String?) -> Unit
    ) {
        obterTokenFirebase { token, erroToken ->
            if (erroToken != null || token == null) {
                mainHandler.post { callback(null, erroToken ?: "Não autenticado") }
                return@obterTokenFirebase
            }
            executor.execute {
                var conn: HttpURLConnection? = null
                try {
                    val boundary = "----CasaBoundary${UUID.randomUUID().toString().replace("-", "")}"
                    val url = URL("$baseUrl/midias")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    }
                    DataOutputStream(conn.outputStream).use { out ->
                        if (!tipo.isNullOrBlank()) {
                            out.writeBytes("--$boundary\r\n")
                            out.writeBytes("Content-Disposition: form-data; name=\"tipo\"\r\n\r\n")
                            out.writeBytes("$tipo\r\n")
                        }
                        out.writeBytes("--$boundary\r\n")
                        out.writeBytes("Content-Disposition: form-data; name=\"arquivo\"; filename=\"$nomeArquivo\"\r\n")
                        out.writeBytes("Content-Type: $mimeType\r\n\r\n")
                        out.write(bytes)
                        out.writeBytes("\r\n--$boundary--\r\n")
                        out.flush()
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val respText = stream?.let {
                        BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                    } ?: ""
                    if (code in 200..299) {
                        val id = JSONObject(respText).optString("id")
                        mainHandler.post { callback(id, null) }
                    } else {
                        mainHandler.post { callback(null, extrairErro(code, respText)) }
                    }
                } catch (_: Exception) {
                    mainHandler.post { callback(null, "Falha ao enviar mídia para a CASA-API.") }
                } finally {
                    conn?.disconnect()
                }
            }
        }
    }
}
