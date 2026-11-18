package fatec.labweb.projetocasa.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Pipeline de Roteamento de Hardware (Loudspeaker) e DSP de Ganho/Compressão de Áudio.
 * - Garante saída exclusiva pelo alto-falante principal (nunca pelo auricular de chamadas).
 * - Aplica ganho de 2.2x (~+6.8 dB) com compressor dinâmico soft-knee na captura PCM e
 *   LoudnessEnhancer na reprodução de MediaPlayer.
 */
object AudioDspHelper {

    private const val TAG = "AudioDspHelper"
    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_CHANNELS = 1
    const val DEFAULT_BIT_RATE = 128000
    const val DEFAULT_GAIN = 2.2f
    private const val LOUDNESS_GAIN_MB = 680 // ~2.2x de ganho perceptual em miliBels

    fun criarAudioAttributesAltoFalante(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    /**
     * Configura o roteamento de áudio antes de gravar ou reproduzir para forçar o uso
     * do alto-falante externo principal (loudspeaker) e evitar o auricular (earpiece).
     */
    fun configurarRoteamentoAltoFalante(context: Context, paraGravacao: Boolean = false) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !paraGravacao) {
                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    try {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } catch (_: Exception) {}
                }
            }

            // Garante que o volume de mídia do sistema não esteja mudo/muito baixo durante o uso
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0 && curVol < (maxVol * 0.5f).toInt()) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (maxVol * 0.8f).toInt().coerceAtMost(maxVol),
                    0
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao configurar roteamento de alto-falante: ${e.message}")
        }
    }

    /**
     * Ativa AGC (Automatic Gain Control) e NoiseSuppressor de hardware na sessão de AudioRecord.
     */
    fun ativarAgcHardware(audioSessionId: Int): Pair<AutomaticGainControl?, NoiseSuppressor?> {
        var agc: AutomaticGainControl? = null
        var ns: NoiseSuppressor? = null
        try {
            if (AutomaticGainControl.isAvailable() && audioSessionId > 0) {
                agc = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AGC indisponível: ${e.message}")
        }
        try {
            if (NoiseSuppressor.isAvailable() && audioSessionId > 0) {
                ns = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoiseSuppressor indisponível: ${e.message}")
        }
        return Pair(agc, ns)
    }

    /**
     * Aplica ganho de volume (gain = 2.2) + compressor dinâmico soft-knee (tanh limiter)
     * diretamente no buffer PCM 16-bit para aumentar o volume da voz gravada sem distorção.
     */
    fun processarBufferPcmComGanhoECompressor(
        buffer: ShortArray,
        length: Int,
        gain: Float = DEFAULT_GAIN
    ): Float {
        if (length <= 0) return 0f
        var peak = 0
        for (i in 0 until length) {
            val a = abs(buffer[i].toInt())
            if (a > peak) peak = a
        }

        // Ganho adaptativo com teto seguro para evitar clipping em sinais já altos
        val effectiveGain = if (peak > 18000) {
            (28000f / peak.toFloat()).coerceIn(1.2f, gain)
        } else {
            gain
        }

        var maxAmpOut = 0f
        val threshold = 0.72f
        for (i in 0 until length) {
            val normalized = buffer[i].toFloat() / 32768f
            val amplified = normalized * effectiveGain

            // Compressor dinâmico soft-knee para evitar clipping acima do threshold
            val compressed = when {
                amplified > threshold -> {
                    val excess = amplified - threshold
                    threshold + (1f - threshold) * tanh(excess / (1f - threshold))
                }
                amplified < -threshold -> {
                    val excess = -amplified - threshold
                    -(threshold + (1f - threshold) * tanh(excess / (1f - threshold)))
                }
                else -> amplified
            }

            val sampleOut = (compressed * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i] = sampleOut
            val absOut = abs(sampleOut.toInt()).toFloat()
            if (absOut > maxAmpOut) maxAmpOut = absOut
        }
        return maxAmpOut
    }

    /**
     * Configura um MediaPlayer com atributos de alto-falante, volume máximo (1.0f) e
     * LoudnessEnhancer DSP (+6.8 dB / gain ~2.2x).
     */
    fun configurarMediaPlayerAltoVolume(context: Context, player: MediaPlayer): LoudnessEnhancer? {
        configurarRoteamentoAltoFalante(context, paraGravacao = false)
        try {
            player.setAudioAttributes(criarAudioAttributesAltoFalante())
        } catch (_: Exception) {}
        try {
            player.setVolume(1.0f, 1.0f)
        } catch (_: Exception) {}

        return try {
            val sessionId = player.audioSessionId
            if (sessionId > 0) {
                LoudnessEnhancer(sessionId).apply {
                    setTargetGain(LOUDNESS_GAIN_MB)
                    enabled = true
                }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer não suportado na sessão atual: ${e.message}")
            null
        }
    }

    /**
     * Configura o TextToSpeech para falar sempre no alto-falante principal com volume máximo.
     */
    fun criarParametrosTtsAltoVolume(): Bundle {
        return Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
    }
}
