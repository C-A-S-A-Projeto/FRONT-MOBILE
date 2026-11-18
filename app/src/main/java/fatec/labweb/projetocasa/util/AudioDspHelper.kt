package fatec.labweb.projetocasa.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * AudioDspHelper: Pipeline de ganho de áudio (DSP / Hardware Gain & Routing)
 * para gravação de alta sensibilidade e reprodução via alto-falante externo (loudspeaker).
 */
object AudioDspHelper {

    const val HIGH_FIDELITY_SAMPLE_RATE = 44100
    const val HIGH_FIDELITY_BIT_RATE = 128000
    const val DEFAULT_RECORD_GAIN = 2.6f
    const val MAX_NORMALIZATION_GAIN = 6.5f
    const val MIN_NORMALIZATION_GAIN = 1.6f
    const val TARGET_PEAK_RATIO = 0.92f
    const val PLAYBACK_LOUDNESS_GAIN_MB = 700 // +7 dB

    /**
     * Configura explicitamente a sessão de áudio do dispositivo antes de gravar ou reproduzir:
     * - Desativa modo de chamada de voz (MODE_NORMAL)
     * - Desativa roteamento pelo earpiece (isSpeakerphoneOn = true / DefaultToSpeaker)
     * - Garante captura pelo microfone principal de alta sensibilidade e saída pelo alto-falante externo.
     */
    fun configurarSessaoAudio(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false

            // Garante volume audível no canal de mídia caso esteja muito baixo
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0 && currentVol < (maxVol * 0.35f).toInt()) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (maxVol * 0.7f).toInt(),
                    0
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Configura MediaRecorder com preset de alta fidelidade (AAC 44.1 kHz, 128 kbps, Mono).
     */
    fun configurarMediaRecorderAltaFidelidade(recorder: MediaRecorder, arquivoSaida: File) {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioChannels(1)
        recorder.setAudioSamplingRate(HIGH_FIDELITY_SAMPLE_RATE)
        recorder.setAudioEncodingBitRate(HIGH_FIDELITY_BIT_RATE)
        recorder.setOutputFile(arquivoSaida.absolutePath)
    }

    /**
     * Ativa efeitos de hardware DSP (AutomaticGainControl e NoiseSuppressor) na sessão do AudioRecord.
     */
    fun aplicarEfeitosHardwareGravacao(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {}

        try {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Aplica ganho em tempo real em uma amostra PCM 16-bit com soft-knee limiter para evitar clipping.
     */
    fun aplicarGanhoAmostraPcm(sample: Short, ganho: Float = DEFAULT_RECORD_GAIN): Short {
        if (sample == 0.toShort()) return 0
        val normalized = sample.toDouble() / Short.MAX_VALUE.toDouble()
        val amplified = normalized * ganho.toDouble()
        // Soft-knee saturation acima de 0.85 para evitar distorção ríspida
        val limited = if (abs(amplified) > 0.85) {
            val sign = if (amplified >= 0) 1.0 else -1.0
            val excess = abs(amplified) - 0.85
            sign * (0.85 + 0.15 * tanh(excess / 0.15))
        } else {
            amplified
        }
        val finalVal = (limited * Short.MAX_VALUE.toDouble()).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        return finalVal.toShort()
    }

    /**
     * Executa normalização de pico/RMS e ganho DSP em um buffer PCM 16-bit Little-Endian,
     * elevando gravações de baixa amplitude para o nível ideal de reprodução sem clipping.
     */
    fun normalizarPcm16Bit(
        rawPcmBytes: ByteArray,
        targetPeakRatio: Float = TARGET_PEAK_RATIO,
        maxGain: Float = MAX_NORMALIZATION_GAIN,
        minGain: Float = MIN_NORMALIZATION_GAIN
    ): ByteArray {
        if (rawPcmBytes.size < 2) return rawPcmBytes
        val numSamples = rawPcmBytes.size / 2
        val inBuffer = ByteBuffer.wrap(rawPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(numSamples)

        var maxPeak = 0
        var sumSquares = 0.0
        for (i in 0 until numSamples) {
            val s = inBuffer.short
            samples[i] = s
            val absVal = abs(s.toInt())
            if (absVal > maxPeak) maxPeak = absVal
            sumSquares += (s.toDouble() * s.toDouble())
        }

        // Se o sinal for puramente silêncio, retorna sem amplificar ruído térmico
        if (maxPeak < 80) return rawPcmBytes

        val targetPeak = (Short.MAX_VALUE * targetPeakRatio).toInt()
        val idealGain = targetPeak.toFloat() / maxPeak.toFloat()
        val effectiveGain = idealGain.coerceIn(minGain, maxGain)

        val outBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val boosted = aplicarGanhoAmostraPcm(samples[i], effectiveGain)
            outBuffer.putShort(boosted)
        }
        return outBuffer.array()
    }

    /**
     * Calcula o RMS (Root Mean Square) de um buffer PCM 16-bit Little-Endian.
     */
    fun calcularRmsPcm16Bit(rawPcmBytes: ByteArray): Double {
        if (rawPcmBytes.size < 2) return 0.0
        val numSamples = rawPcmBytes.size / 2
        val buf = ByteBuffer.wrap(rawPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        for (i in 0 until numSamples) {
            val s = buf.short.toDouble()
            sumSq += s * s
        }
        return sqrt(sumSq / numSamples)
    }

    /**
     * Configura o MediaPlayer para saída forçada no alto-falante externo (USAGE_MEDIA)
     * com volume máximo e LoudnessEnhancer acoplado à sessão de áudio.
     */
    fun configurarMediaPlayerAltoFalante(
        context: Context,
        player: MediaPlayer,
        ganhoMb: Int = PLAYBACK_LOUDNESS_GAIN_MB
    ): LoudnessEnhancer? {
        configurarSessaoAudio(context)
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            player.setAudioAttributes(attrs)
        } catch (_: Exception) {}

        try {
            player.setVolume(1.0f, 1.0f)
        } catch (_: Exception) {}

        return try {
            val sessionId = player.audioSessionId
            if (sessionId > 0) {
                LoudnessEnhancer(sessionId).apply {
                    setTargetGain(ganhoMb)
                    enabled = true
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
