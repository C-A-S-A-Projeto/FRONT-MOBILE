package fatec.labweb.projetocasa.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.DirectEdgeTtsService
import java.io.File
import java.text.Normalizer
import java.util.Locale

object AudioPredefinidoHelper {

    private const val TAG = "AudioPredefHelper"

    fun normalizarTexto(texto: String): String {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    /**
     * Obtém ou sintetiza de forma síncrona/em cache o áudio neural (Microsoft Edge TTS: Francisca/Antônio).
     * Não utiliza gravações estáticas obsoletas, garantindo voz bonita e natural uniforme.
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
     * Obtém áudio neural de alta qualidade (Microsoft Edge TTS: Francisca/Antônio)
     * de forma assíncrona para pré-visualização ou carregamento instantâneo.
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
     * Sintetiza o áudio neural oficial para o arquivo destino.
     * Caso o aparelho esteja sem internet, aplica fallback para o sintetizador local do Android.
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
            // 1. Tenta sintetizar com a voz neural oficial do Edge TTS
            val sucessoNeural = DirectEdgeTtsService.sintetizarParaArquivo(textoLimpo, genero, arquivoDestino)
            if (sucessoNeural && arquivoDestino.exists() && arquivoDestino.length() > 0) {
                onFinalizado(arquivoDestino)
                return@Thread
            }

            Log.w(TAG, "Edge TTS offline, executando fallback local Android...")

            // 2. Fallback offline caso o dispositivo esteja sem conexão
            sintetizarVozAndroidFallback(ttsFallback, textoLimpo, genero, arquivoDestino, onFinalizado)
        }.start()
    }

    fun aplicarConfiguracaoVoz(tts: TextToSpeech, genero: GeneroAudio) {
        try {
            tts.language = Locale("pt", "BR")
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val vozesPt = tts.voices?.filter {
                    val loc = it.locale
                    (loc.language.equals("pt", ignoreCase = true) || loc.country.equals("BR", ignoreCase = true)) &&
                            !it.isNetworkConnectionRequired
                } ?: emptyList()

                val vozSelecionada = if (genero == GeneroAudio.MALE) {
                    vozesPt.firstOrNull { it.name.lowercase().contains("male") || it.name.lowercase().contains("ptd") || it.name.lowercase().contains("homem") }
                } else {
                    vozesPt.firstOrNull { it.name.lowercase().contains("female") || it.name.lowercase().contains("ptc") || it.name.lowercase().contains("mulher") }
                }

                if (vozSelecionada != null) {
                    tts.voice = vozSelecionada
                }
            } catch (_: Exception) {}
        }

        if (genero == GeneroAudio.FEMALE) {
            tts.setPitch(1.15f)
            tts.setSpeechRate(1.0f)
        } else {
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.95f)
        }
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
            val utteranceId = "synth_${System.currentTimeMillis()}"
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

            val params = Bundle()
            val result = tts.synthesizeToFile(texto, params, arquivoDestino, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                onFinalizado(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sintetizar voz no Android TTS: ${e.message}", e)
            onFinalizado(null)
        }
    }

    fun falarPrevia(tts: TextToSpeech?, texto: String, genero: GeneroAudio) {
        falarPreviaFallback(tts, texto, genero)
    }

    fun falarPreviaFallback(tts: TextToSpeech?, texto: String, genero: GeneroAudio) {
        if (tts == null || texto.isBlank()) return
        try {
            aplicarConfiguracaoVoz(tts, genero)
            val utteranceId = "previa_${System.currentTimeMillis()}"
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir fala TTS fallback: ${e.message}", e)
        }
    }
}
