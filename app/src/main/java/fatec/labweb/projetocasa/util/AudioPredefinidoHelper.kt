package fatec.labweb.projetocasa.util

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.HumanizedTtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.Normalizer
import java.util.Locale

object AudioPredefinidoHelper {

    private const val TAG = "AudioPredefHelper"

    private val MAPA_SLUGS = mapOf(
        "banheiro" to "banheiro",
        "banho" to "banheiro",
        "cozinha" to "cozinha",
        "comer" to "cozinha",
        "comida" to "cozinha",
        "lavanderia" to "lavanderia",
        "lavar" to "lavanderia",
        "quarto" to "quarto",
        "dormir" to "quarto",
        "sala" to "sala",
        "assistir" to "sala",
        "festa" to "festa",
        "comemorar" to "festa",
        "ler" to "ler",
        "livro" to "ler",
        "musica" to "musica",
        "ouvir" to "musica",
        "silencio" to "silencio",
        "calma" to "silencio",
        "ajuda" to "ajuda",
        "socorro" to "ajuda",
        "feliz" to "feliz",
        "medo" to "medo",
        "nojo" to "nojo",
        "raiva" to "raiva",
        "triste" to "triste",
        "casa" to "casa",
        "desejos" to "desejos",
        "desejo" to "desejos",
        "emocoes" to "emocoes",
        "emocao" to "emocoes"
    )

    fun normalizarTexto(texto: String): String {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
    }

    fun identificarSlug(nome: String): String? {
        val norm = normalizarTexto(nome)
        for ((chave, slug) in MAPA_SLUGS) {
            if (norm.contains(chave)) {
                return slug
            }
        }
        return null
    }

    /**
     * Obtém o áudio pré-gravado dos assets (gerados oficialmente com as vozes neurais Francisca / Antônio)
     */
    fun obterAudioPredefinido(context: Context, identificador: String, genero: GeneroAudio): File? {
        val slug = identificarSlug(identificador) ?: normalizarTexto(identificador).replace(Regex("[^a-z0-9]"), "")
        val sufixo = if (genero == GeneroAudio.MALE) "masculino" else "feminino"
        val nomeArquivo = "${slug}_$sufixo.mp3"
        val dir = context.getExternalFilesDir("botoes_audios") ?: context.filesDir
        val destino = File(dir, nomeArquivo)

        if (destino.exists() && destino.length() > 0) {
            return destino
        }

        return try {
            context.assets.open("predefinidos/audios/$nomeArquivo").use { input ->
                destino.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Audio predefinido neural extraído: ${destino.absolutePath} (${destino.length()} bytes)")
            destino
        } catch (e: Exception) {
            try {
                val fallbackNome = "$slug.mp3"
                val destFallback = File(dir, fallbackNome)
                context.assets.open("predefinidos/audios/$fallbackNome").use { input ->
                    destFallback.outputStream().use { output -> input.copyTo(output) }
                }
                destFallback
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Toca prévia do áudio com a voz humanizada (Francisca/Antônio da CASA-API ou arquivo pré-gravado)
     */
    fun tocarPreviaHumanizada(
        context: Context,
        texto: String,
        genero: GeneroAudio,
        tts: TextToSpeech?,
        onPlayerPronto: (MediaPlayer) -> Unit
    ) {
        val frase = texto.trim()
        if (frase.isBlank()) return

        // 1. Tenta áudio pré-gravado oficial dos assets
        val predef = obterAudioPredefinido(context, frase, genero)
        if (predef != null && predef.exists()) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(predef.absolutePath)
                    prepare()
                    start()
                }
                onPlayerPronto(mp)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao reproduzir predef: ${e.message}")
            }
        }

        // 2. Sintetiza na hora via CASA-API (Edge Neural: Francisca / Antônio)
        val temp = File(context.cacheDir, "previa_${System.currentTimeMillis()}.mp3")
        CoroutineScope(Dispatchers.Main).launch {
            val gerado = HumanizedTtsService.sintetizarVozHumanizada(context, frase, genero, temp)
            if (gerado != null && gerado.exists()) {
                try {
                    val mp = MediaPlayer().apply {
                        setDataSource(gerado.absolutePath)
                        prepare()
                        start()
                    }
                    onPlayerPronto(mp)
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao reproduzir áudio da CASA-API: ${e.message}")
                }
            }

            // 3. Fallback de segurança local
            falarPrevia(tts, frase, genero)
        }
    }

    /**
     * Sintetiza o áudio gerando o arquivo final para o dispositivo.
     * Prioridade: 1) Pré-gravado dos assets; 2) CASA-API (Edge Neural); 3) Fallback TTS nativo.
     */
    fun sintetizarVozParaArquivo(
        context: Context,
        tts: TextToSpeech?,
        texto: String,
        genero: GeneroAudio,
        arquivoDestino: File,
        onFinalizado: (File?) -> Unit
    ) {
        val frase = texto.trim()
        if (frase.isBlank()) {
            onFinalizado(null)
            return
        }

        // 1. Se for uma ação predefinida com arquivo oficial nos assets
        val predef = obterAudioPredefinido(context, frase, genero)
        if (predef != null && predef.exists()) {
            try {
                predef.copyTo(arquivoDestino, overwrite = true)
                onFinalizado(arquivoDestino)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao copiar predefinido: ${e.message}")
            }
        }

        // 2. Tenta gerar via CASA-API com voz neural Francisca / Antônio
        CoroutineScope(Dispatchers.Main).launch {
            val geradoApi = HumanizedTtsService.sintetizarVozHumanizada(context, frase, genero, arquivoDestino)
            if (geradoApi != null && geradoApi.exists() && geradoApi.length() > 0) {
                onFinalizado(geradoApi)
                return@launch
            }

            // 3. Fallback offline de segurança
            if (tts != null) {
                try {
                    aplicarConfiguracaoVoz(tts, genero)
                    val utteranceId = "synth_${System.currentTimeMillis()}"
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId && arquivoDestino.exists() && arquivoDestino.length() > 0) {
                                CoroutineScope(Dispatchers.Main).launch { onFinalizado(arquivoDestino) }
                            } else {
                                CoroutineScope(Dispatchers.Main).launch { onFinalizado(null) }
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            CoroutineScope(Dispatchers.Main).launch { onFinalizado(null) }
                        }
                    })

                    val params = Bundle()
                    val result = tts.synthesizeToFile(frase, params, arquivoDestino, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        onFinalizado(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no fallback TTS: ${e.message}", e)
                    onFinalizado(null)
                }
            } else {
                onFinalizado(null)
            }
        }
    }

    fun aplicarConfiguracaoVoz(tts: TextToSpeech, genero: GeneroAudio) {
        try {
            tts.language = Locale("pt", "BR")
        } catch (_: Exception) {}

        if (genero == GeneroAudio.FEMALE) {
            tts.setPitch(1.15f)
            tts.setSpeechRate(1.0f)
        } else {
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.95f)
        }
    }

    fun falarPrevia(tts: TextToSpeech?, texto: String, genero: GeneroAudio) {
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
