package fatec.labweb.projetocasa.util

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import fatec.labweb.projetocasa.model.GeneroAudio
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
            Log.d(TAG, "Audio predefinido extraído: ${destino.absolutePath} (${destino.length()} bytes)")
            destino
        } catch (e: Exception) {
            // Tenta fallback sem sufixo se houver
            try {
                val fallbackNome = "$slug.mp3"
                val destFallback = File(dir, fallbackNome)
                context.assets.open("predefinidos/audios/$fallbackNome").use { input ->
                    destFallback.outputStream().use { output -> input.copyTo(output) }
                }
                destFallback
            } catch (eFallback: Exception) {
                Log.w(TAG, "Arquivo de audio predefinido não encontrado no assets: $nomeArquivo")
                null
            }
        }
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
            Log.e(TAG, "Erro ao sintetizar voz: ${e.message}", e)
            onFinalizado(null)
        }
    }

    fun falarPrevia(tts: TextToSpeech?, texto: String, genero: GeneroAudio) {
        if (tts == null || texto.isBlank()) return
        try {
            aplicarConfiguracaoVoz(tts, genero)
            val utteranceId = "previa_${System.currentTimeMillis()}"
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir fala TTS: ${e.message}", e)
        }
    }
}
