package fatec.labweb.projetocasa.util

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.DirectEdgeTtsService
import java.io.File
import java.text.Normalizer
import java.util.Locale

object AudioPredefinidoHelper {

    private const val TAG = "AudioPredefHelper"

    // Identificadores comuns de vozes masculinas e femininas em motores TTS (Google, Samsung, Android)
    private val MARCADORES_VOZ_MASCULINA = listOf(
        "pt-br-x-ptd",
        "ptd-local",
        "ptd-network",
        "male",
        "masculin",
        "homem",
        "antonio",
        "daniel",
        "felipe",
        "ricardo",
        "cfb",
        "#male"
    )

    private val MARCADORES_VOZ_FEMININA = listOf(
        "pt-br-x-ptc",
        "pt-br-x-pta",
        "pt-br-x-afs",
        "ptc-local",
        "pta-local",
        "afs-local",
        "female",
        "feminin",
        "mulher",
        "francisca",
        "luciana",
        "maria",
        "vitoria",
        "#female"
    )

    fun normalizarTexto(texto: String): String {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    /**
     * Verifica se o nome de uma voz TTS corresponde ao gênero solicitado.
     */
    fun identificarGeneroDaVozTts(nomeVoz: String, genero: GeneroAudio): Boolean {
        val lower = nomeVoz.lowercase()
        return if (genero == GeneroAudio.MALE) {
            MARCADORES_VOZ_MASCULINA.any { lower.contains(it) } && !lower.contains("female")
        } else {
            MARCADORES_VOZ_FEMININA.any { lower.contains(it) }
        }
    }

    /**
     * Retorna os parâmetros acústicos de pitch e speechRate para diferenciar claramente
     * voz masculina (grave: pitch 0.80f) e voz feminina (aguda: pitch 1.12f).
     */
    fun obterPitchParaGenero(genero: GeneroAudio): Float {
        return if (genero == GeneroAudio.MALE) 0.80f else 1.12f
    }

    fun obterSpeechRateParaGenero(genero: GeneroAudio): Float {
        return if (genero == GeneroAudio.MALE) 0.95f else 1.0f
    }

    /**
     * Obtém ou sintetiza de forma síncrona/em cache o áudio neural (Microsoft Edge TTS: Francisca/Antônio).
     */
    fun obterAudioPredefinido(context: Context, identificador: String, genero: GeneroAudio): File? {
        val textoLimpo = identificador.trim()
        if (textoLimpo.isBlank()) return null

        val slug = normalizarTexto(textoLimpo).replace(Regex("[^a-z0-9]"), "")
        val sufixo = if (genero == GeneroAudio.MALE) "masculino" else "feminino"
        val nomeArquivo = "${slug}_$sufixo.mp3"
        val dir = context.getExternalFilesDir("botoes_audios") ?: context.filesDir
        val destino = File(dir, nomeArquivo)

        if (destino.exists() && destino.length() > 0) {
            return destino
        }

        val sucesso = DirectEdgeTtsService.sintetizarParaArquivo(textoLimpo, genero, destino)
        if (sucesso && destino.exists() && destino.length() > 0) {
            return destino
        }

        return null
    }

    /**
     * Obtém áudio neural de alta qualidade (Microsoft Edge TTS: Francisca para FEMALE / Antônio para MALE)
     * de forma assíncrona para pré-visualização ou reprodução.
     */
    fun obterAudioVozNeural(
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

        DirectEdgeTtsService.obterOuGerarAudio(context, textoLimpo, genero, onResultado)
    }

    /**
     * Sintetiza o áudio neural oficial para o arquivo destino respeitando estritamente o gênero selecionado.
     * Caso o aparelho esteja sem internet, aplica fallback para o sintetizador local do Android com voz/pitch do gênero.
     */
    fun sintetizarVozOficialParaArquivo(
        context: Context,
        ttsFallback: TextToSpeech?,
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File,
        onFinalizado: (File?) -> Unit
    ) {
        val textoLimpo = texto.trim()
        if (textoLimpo.isBlank()) {
            onFinalizado(null)
            return
        }

        Thread {
            // 1. Tenta sintetizar com a voz neural oficial do Edge TTS (Antônio para MALE / Francisca para FEMALE)
            val sucessoNeural = DirectEdgeTtsService.sintetizarParaArquivo(textoLimpo, genero, arquivoDestino)
            if (sucessoNeural && arquivoDestino.exists() && arquivoDestino.length() > 0) {
                onFinalizado(arquivoDestino)
                return@Thread
            }

            Log.w(TAG, "Edge TTS offline, executando fallback local Android para genero=${genero.name}...")

            // 2. Fallback offline caso o dispositivo esteja sem conexão
            sintetizarVozAndroidFallback(ttsFallback, textoLimpo, genero, arquivoDestino, onFinalizado)
        }.start()
    }

    /**
     * Configura o motor TextToSpeech nativo selecionando explicitamente uma voz pt-BR do gênero escolhido
     * (masculina vs. feminina) e ajustando o pitch (0.80f para masculino, 1.12f para feminino) para
     * garantir diferenciação real e audível em qualquer dispositivo.
     */
    fun aplicarConfiguracaoVoz(tts: TextToSpeech, genero: GeneroAudio) {
        try {
            tts.language = Locale("pt", "BR")
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val todasVozes = tts.voices?.toList() ?: emptyList()
                val vozesPt = todasVozes.filter {
                    val loc = it.locale
                    loc != null && (loc.language.equals("pt", ignoreCase = true) || loc.country.equals("BR", ignoreCase = true))
                }

                // Prioriza vozes locais que não exigem rede, mas aceita todas as vozes pt-BR disponíveis
                val vozesPtLocais = vozesPt.filter { !it.isNetworkConnectionRequired }
                val poolBusca: List<Voice> = if (vozesPtLocais.isNotEmpty()) (vozesPtLocais + vozesPt).distinct() else vozesPt

                val vozSelecionada = poolBusca.firstOrNull { voice ->
                    identificarGeneroDaVozTts(voice.name, genero) ||
                        (voice.features?.any { feat -> identificarGeneroDaVozTts(feat, genero) } == true)
                }

                if (vozSelecionada != null) {
                    tts.voice = vozSelecionada
                }
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao selecionar Voice específica no TTS: ${e.message}")
            }
        }

        // Aplica SEMPRE o pitch e speechRate DEPOIS de tts.voice para garantir diferenciação audível
        tts.setPitch(obterPitchParaGenero(genero))
        tts.setSpeechRate(obterSpeechRateParaGenero(genero))
    }

    fun sintetizarVozParaArquivo(
        tts: TextToSpeech?,
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File,
        onFinalizado: (File?) -> Unit
    ) {
        sintetizarVozAndroidFallback(tts, texto, genero, arquivoDestino, onFinalizado)
    }

    private fun sintetizarVozAndroidFallback(
        tts: TextToSpeech?,
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File,
        onFinalizado: (File?) -> Unit
    ) {
        if (tts == null) {
            onFinalizado(null)
            return
        }
        try {
            aplicarConfiguracaoVoz(tts, genero)
            val utteranceId = "synth_${genero.name.lowercase()}_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId && arquivoDestino.exists() && arquivoDestino.length() > 0) {
                        onFinalizado(arquivoDestino)
                    } else {
                        onFinalizado(null)
                    }
                }
                override fun onError(utteranceId: String?) {
                    onFinalizado(null)
                }
            })

            val params = AudioDspHelper.criarParametrosTtsAltoVolume()
            val result = tts.synthesizeToFile(texto, params, arquivoDestino, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                onFinalizado(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sintetizar voz no Android TTS: ${e.message}", e)
            onFinalizado(null)
        }
    }

    fun falarPrevia(tts: TextToSpeech?, texto: String, genero: GeneroAudio, context: Context? = null) {
        if (context != null) {
            AudioDspHelper.configurarRoteamentoAltoFalante(context, paraGravacao = false)
        }
        falarPreviaFallback(tts, texto, genero)
    }

    fun falarPreviaFallback(tts: TextToSpeech?, texto: String, genero: GeneroAudio) {
        if (tts == null || texto.isBlank()) return
        try {
            aplicarConfiguracaoVoz(tts, genero)
            val utteranceId = "previa_${genero.name.lowercase()}_${System.currentTimeMillis()}"
            val params = AudioDspHelper.criarParametrosTtsAltoVolume()
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir fala TTS fallback: ${e.message}", e)
        }
    }
}
