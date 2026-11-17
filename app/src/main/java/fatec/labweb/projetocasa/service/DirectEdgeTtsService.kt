package fatec.labweb.projetocasa.service

import android.content.Context
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * DirectEdgeTtsService:
 * Sintetizador neural direto no app via protocolo WebSocket do Microsoft Edge TTS.
 * Gera vozes neurais ultra-realistas em português do Brasil:
 * - Francisca (pt-BR-FranciscaNeural) para feminino
 * - Antônio (pt-BR-AntonioNeural) para masculino
 *
 * Funciona de forma totalmente autônoma dentro do app, sem depender de servidor externo.
 */
object DirectEdgeTtsService {

    private const val TAG = "DirectEdgeTts"
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val BASE_WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"

    const val VOICE_FEMALE = "pt-BR-FranciscaNeural"
    const val VOICE_MALE = "pt-BR-AntonioNeural"

    private fun logD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG][D] $msg")
        }
    }

    private fun logW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: Throwable) {
            System.err.println("[$TAG][W] $msg")
        }
    }

    private fun logE(msg: String, t: Throwable? = null) {
        try {
            Log.e(TAG, msg, t)
        } catch (_: Throwable) {
            System.err.println("[$TAG][E] $msg: ${t?.message}")
            t?.printStackTrace()
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun obterDataFormatada(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun escaparXml(texto: String): String {
        return texto
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Sintetiza o texto para o arquivo de destino de forma síncrona/bloqueante com timeout.
     * Retorna true em caso de sucesso (arquivo gravado com bytes de áudio MP3).
     */
    fun sintetizarParaArquivo(
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File,
        timeoutSegundos: Long = 15
    ): Boolean {
        val textoLimpo = texto.trim()
        if (textoLimpo.isBlank()) return false

        val voz = if (genero == GeneroAudio.MALE) VOICE_MALE else VOICE_FEMALE
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = EdgeDrm.generateSecMsGecV2()
        val secMsGecVersion = "2-${EdgeDrm.CHROMIUM_FULL_VERSION}"

        val url = "$BASE_WSS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId&Sec-MS-GEC=$secMsGec&Sec-MS-GEC-Version=$secMsGecVersion"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .build()

        val latch = CountDownLatch(1)
        val audioBuffer = ByteArrayOutputStream()
        var sucesso = false
        var erroOcorrido: String? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                try {
                    val timestamp = obterDataFormatada()

                    // 1. Configuração do formato de áudio (MP3 mono 24khz)
                    val configMsg = "X-Timestamp:$timestamp\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"

                    webSocket.send(configMsg)

                    // 2. SSML com texto e voz neural
                    val requestId = UUID.randomUUID().toString().replace("-", "")
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='pt-BR'>" +
                            "<voice name='$voz'>" +
                            "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>" +
                            escaparXml(textoLimpo) +
                            "</prosody>" +
                            "</voice>" +
                            "</speak>"

                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${timestamp}Z\r\n" +
                            "Path:ssml\r\n\r\n" +
                            ssml

                    webSocket.send(ssmlMsg)
                } catch (e: Exception) {
                    erroOcorrido = e.message
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    sucesso = true
                    webSocket.close(1000, "Normal closure")
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 2) {
                    val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val headerOffset = 2 + headerLen
                    if (data.size > headerOffset) {
                        val headerStr = String(data, 2, headerLen, Charsets.UTF_8)
                        if (headerStr.contains("Path:audio")) {
                            audioBuffer.write(data, headerOffset, data.size - headerOffset)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                erroOcorrido = t.message
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        }

        val ws = client.newWebSocket(request, listener)

        try {
            val concluido = latch.await(timeoutSegundos, TimeUnit.SECONDS)
            if (!concluido) {
                logW("Timeout ao sintetizar voz Edge TTS para: $textoLimpo")
                ws.cancel()
                return false
            }

            if (sucesso && audioBuffer.size() > 0) {
                arquivoDestino.parentFile?.mkdirs()
                FileOutputStream(arquivoDestino).use { fos ->
                    fos.write(audioBuffer.toByteArray())
                    fos.flush()
                }
                logD("Áudio sintetizado com sucesso: ${arquivoDestino.absolutePath} (${arquivoDestino.length()} bytes)")
                return true
            } else {
                logW("Falha na síntese Edge TTS: erro=$erroOcorrido bytes=${audioBuffer.size()}")
            }
        } catch (e: Exception) {
            logE("Erro durante síntese Edge TTS", e)
        }

        return false
    }

    /**
     * Gera ou obtém do cache local o áudio sintetizado para o texto e gênero especificados.
     * Retorna o arquivo gerado ou nulo se falhar.
     */
    fun obterOuGerarAudio(
        context: Context,
        texto: String,
        genero: GeneroAudio,
        onResultado: (File?) -> Unit
    ) {
        val textoLimpo = texto.trim()
        if (textoLimpo.isBlank()) {
            onResultado(null)
            return
        }

        // Cache baseado no hash do texto e gênero
        val hash = (textoLimpo.lowercase() + "_" + genero.name).hashCode().toString().replace("-", "n")
        val cacheDir = File(context.cacheDir, "edge_tts")
        cacheDir.mkdirs()
        val arquivoCache = File(cacheDir, "voz_${hash}.mp3")

        if (arquivoCache.exists() && arquivoCache.length() > 0) {
            onResultado(arquivoCache)
            return
        }

        Thread {
            val ok = sintetizarParaArquivo(textoLimpo, genero, arquivoCache)
            val res = if (ok && arquivoCache.exists() && arquivoCache.length() > 0) arquivoCache else null
            onResultado(res)
        }.start()
    }
}
