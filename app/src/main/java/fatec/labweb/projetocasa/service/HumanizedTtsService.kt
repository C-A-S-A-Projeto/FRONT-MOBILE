package fatec.labweb.projetocasa.service

import android.content.Context
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Serviço de síntese de voz neural humanizada integrado à CASA-API (FastAPI).
 * Utiliza as vozes neurais de altíssima qualidade do Microsoft Edge Neural TTS:
 * - Feminina: Francisca (pt-BR-FranciscaNeural - suave, natural e acolhedora)
 * - Masculina: Antônio (pt-BR-AntonioNeural - natural, claro e amigável)
 */
object HumanizedTtsService {

    private const val TAG = "HumanizedTts"
    private const val PREFS_NAME = "casa_api_prefs"
    private const val KEY_CUSTOM_URL = "casa_api_base_url"

    // Endereços para alcançar a CASA-API dependendo do ambiente (Emulador, Wi-Fi local ou Localhost)
    private val CANDIDATOS_BASE_URL = listOf(
        "http://10.0.2.2:8000",       // Emulador Android apontando para o PC host
        "http://192.168.15.18:8000",  // IP da rede local da máquina host
        "http://127.0.0.1:8000",      // Localhost direto
        "http://localhost:8000"
    )

    @Volatile
    private var urlAtiva: String? = null

    fun getVoiceId(genero: GeneroAudio): String {
        return if (genero == GeneroAudio.MALE) "pt-BR-AntonioNeural" else "pt-BR-FranciscaNeural"
    }

    fun configurarUrlApi(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_URL, url.trim().removeSuffix("/")).apply()
        urlAtiva = url.trim().removeSuffix("/")
    }

    fun obterUrlApi(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_URL, null) ?: urlAtiva
    }

    /**
     * Sintetiza o áudio via endpoint /tts/preview da CASA-API e salva como arquivo MP3.
     * Retorna o File gerado em caso de sucesso ou null se a API estiver inacessível.
     */
    suspend fun sintetizarVozHumanizada(
        context: Context,
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File
    ): File? = withContext(Dispatchers.IO) {
        val frase = texto.trim()
        if (frase.isBlank()) return@withContext null

        val vozId = getVoiceId(genero)
        val jsonPayload = JSONObject().apply {
            put("texto", frase)
            put("voz", vozId)
        }.toString()

        val urlsParaTentar = mutableListOf<String>()
        obterUrlApi(context)?.let { if (it.isNotBlank()) urlsParaTentar.add(it) }
        CANDIDATOS_BASE_URL.forEach { if (!urlsParaTentar.contains(it)) urlsParaTentar.add(it) }

        for (baseUrl in urlsParaTentar) {
            try {
                val endpoint = URL("$baseUrl/tts/preview")
                val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "audio/mpeg")
                    connectTimeout = 3500
                    readTimeout = 8000
                    doOutput = true
                }

                conn.outputStream.use { os ->
                    os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                if (conn.responseCode == 200) {
                    arquivoDestino.parentFile?.mkdirs()
                    conn.inputStream.use { input ->
                        FileOutputStream(arquivoDestino).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (arquivoDestino.exists() && arquivoDestino.length() > 0) {
                        urlAtiva = baseUrl
                        Log.d(TAG, "Áudio humanizado sintetizado via $baseUrl ($vozId): ${arquivoDestino.length()} bytes")
                        return@withContext arquivoDestino
                    }
                } else {
                    Log.w(TAG, "CASA-API em $baseUrl retornou HTTP ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao conectar em $baseUrl/tts/preview: ${e.message}")
            }
        }

        null
    }
}
