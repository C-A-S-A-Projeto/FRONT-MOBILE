package fatec.labweb.projetocasa.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.AudioDspHelper
import fatec.labweb.projetocasa.util.MidiaLocalHelper
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * ModalGravadorAudio: Gravador de áudio com roteamento para alto-falante principal (loudspeaker),
 * AGC de hardware, amplificação DSP (gain = 2.2x + compressor dinâmico soft-knee) e persistência
 * física permanente no armazenamento interno do aplicativo.
 */
class ModalGravadorAudio(
    private val activity: Activity,
    private val audioId: String,
    private val onAudioGravado: (arquivoLocal: File) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var audioRecord: AudioRecord? = null
    private var agcEffect: AutomaticGainControl? = null
    private var nsEffect: NoiseSuppressor? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var recordingThread: Thread? = null
    private var rawTempFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var arquivoDestino: File? = null
    private var sampleRateAtivo: Int = SAMPLE_RATE
    private var isGravando = false
    private var isReproduzindoPrevia = false

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val SAMPLE_RATE = 44100
        const val FALLBACK_SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }

    fun iniciar() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 301)
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_gravar_audio, null)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusGravador)
        val chronometer = dialogView.findViewById<Chronometer>(R.id.chronometerGravacao)
        val waveformView = dialogView.findViewById<AudioWaveformView>(R.id.waveformView)
        val viewGlow = dialogView.findViewById<View>(R.id.viewGlowMic)
        val btnDescartar = dialogView.findViewById<MaterialButton>(R.id.btnDescartarGravacao)
        val btnPararOuPrevia = dialogView.findViewById<MaterialButton>(R.id.btnPararOuPrevia)
        val btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarAudio)

        val pastaAudios = activity.getExternalFilesDir("botoes_audios")
            ?: File(activity.filesDir, "botoes_audios")
        if (!pastaAudios.exists()) pastaAudios.mkdirs()
        arquivoDestino = File(pastaAudios, "asset_${audioId}_${System.currentTimeMillis()}.wav")

        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog?.show()

        iniciarGravacaoNativa(chronometer, waveformView, txtStatus, viewGlow)

        btnPararOuPrevia.setOnClickListener {
            if (isGravando) {
                pararGravacaoNativa(chronometer, txtStatus, viewGlow)
                btnPararOuPrevia.text = "Ouvir"
                btnPararOuPrevia.setIconResource(android.R.drawable.ic_media_play)
            } else if (isReproduzindoPrevia) {
                pararPrevia(btnPararOuPrevia, txtStatus)
            } else {
                reproduzirPrevia(btnPararOuPrevia, txtStatus)
            }
        }

        dialogView.findViewById<View>(R.id.btnVoltarModal)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            cancelarELiberar()
            dialog?.dismiss()
        }

        btnDescartar.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            cancelarELiberar()
            dialog?.dismiss()
        }

        btnSalvar.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            if (isGravando) {
                pararGravacaoNativa(chronometer, txtStatus, viewGlow)
            }
            liberarRecursos()

            val destino = arquivoDestino
            if (destino != null && destino.exists() && destino.length() > 44) {
                val salvoPermanente = MidiaLocalHelper.salvarAudioLocalmente(activity, audioId, destino) ?: destino
                onAudioGravado(salvoPermanente)
                Toast.makeText(activity, "Áudio gravado e amplificado salvo com sucesso!", Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
            } else {
                Toast.makeText(activity, "Nenhum áudio foi gravado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarGravacaoNativa(
        chronometer: Chronometer,
        waveformView: AudioWaveformView,
        txtStatus: TextView,
        viewGlow: View
    ) {
        try {
            AudioDspHelper.configurarRoteamentoAltoFalante(activity, paraGravacao = true)
            waveformView.clear()

            var sr = SAMPLE_RATE
            var minBuf = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                sr = FALLBACK_SAMPLE_RATE
                minBuf = AudioRecord.getMinBufferSize(
                    sr,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            }
            sampleRateAtivo = sr
            val bufSize = maxOf(minBuf * 2, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(activity, "Microfone não pôde ser inicializado.", Toast.LENGTH_SHORT).show()
                return
            }

            audioRecord = record
            val (agc, ns) = AudioDspHelper.ativarAgcHardware(record.audioSessionId)
            agcEffect = agc
            nsEffect = ns

            rawTempFile = File(activity.cacheDir, "temp_rec_${System.currentTimeMillis()}.raw")
            val fos = FileOutputStream(rawTempFile)

            record.startRecording()
            isGravando = true
            chronometer.base = SystemClock.elapsedRealtime()
            chronometer.start()
            txtStatus.text = "Gravando áudio de voz (44.1 kHz Mono + Ganho DSP)..."
            viewGlow.visibility = View.VISIBLE

            recordingThread = thread(start = true, name = "AudioRecordThread") {
                val shortBuffer = ShortArray(bufSize / 2)
                try {
                    while (isGravando) {
                        val read = record.read(shortBuffer, 0, shortBuffer.size)
                        if (read > 0) {
                            // Aplica ganho 2.2x e compressor dinâmico soft-knee para evitar áudio baixo
                            val maxAmp = AudioDspHelper.processarBufferPcmComGanhoECompressor(
                                shortBuffer,
                                read,
                                AudioDspHelper.DEFAULT_GAIN
                            )
                            val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until read) {
                                byteBuffer.putShort(shortBuffer[i])
                            }
                            fos.write(byteBuffer.array())
                            handler.post { waveformView.addAmplitude(maxAmp) }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { fos.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            isGravando = false
            Toast.makeText(activity, "Falha ao iniciar microfone: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pararGravacaoNativa(
        chronometer: Chronometer,
        txtStatus: TextView,
        viewGlow: View
    ) {
        if (!isGravando) return
        try {
            chronometer.stop()
            viewGlow.visibility = View.INVISIBLE
            isGravando = false

            try { agcEffect?.release() } catch (_: Exception) {}
            agcEffect = null
            try { nsEffect?.release() } catch (_: Exception) {}
            nsEffect = null

            audioRecord?.let { record ->
                try { record.stop() } catch (_: Exception) {}
                record.release()
            }
            audioRecord = null
            recordingThread?.join(500)
            recordingThread = null

            val raw = rawTempFile
            val destino = arquivoDestino
            if (raw != null && raw.exists() && destino != null) {
                converterPcmParaWav(raw, destino, sampleRateAtivo, CHANNELS, BITS_PER_SAMPLE)
                raw.delete()
            }

            txtStatus.text = "Gravação concluída. Ouça ou salve abaixo:"
        } catch (e: Exception) {
            isGravando = false
        }
    }

    private fun reproduzirPrevia(
        btnPararOuPrevia: MaterialButton,
        txtStatus: TextView
    ) {
        val arquivo = arquivoDestino ?: return
        if (!arquivo.exists() || arquivo.length() <= 44) return

        try {
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            loudnessEnhancer = null
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                loudnessEnhancer = AudioDspHelper.configurarMediaPlayerAltoVolume(activity, this)
                setDataSource(arquivo.absolutePath)
                prepare()
                setVolume(1.0f, 1.0f)
                start()
                isReproduzindoPrevia = true
                btnPararOuPrevia.text = "Pausar"
                btnPararOuPrevia.setIconResource(android.R.drawable.ic_media_pause)
                txtStatus.text = "Reproduzindo prévia no alto-falante..."

                setOnCompletionListener {
                    pararPrevia(btnPararOuPrevia, txtStatus)
                }
            }
        } catch (e: Exception) {
            pararPrevia(btnPararOuPrevia, txtStatus)
            Toast.makeText(activity, "Erro ao reproduzir prévia: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pararPrevia(btnPararOuPrevia: MaterialButton, txtStatus: TextView) {
        try {
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            loudnessEnhancer = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isReproduzindoPrevia = false
            btnPararOuPrevia.text = "Ouvir"
            btnPararOuPrevia.setIconResource(android.R.drawable.ic_media_play)
            txtStatus.text = "Áudio pronto para salvar."
        } catch (_: Exception) {}
    }

    private fun cancelarELiberar() {
        liberarRecursos()
        try {
            rawTempFile?.delete()
            arquivoDestino?.delete()
        } catch (_: Exception) {}
    }

    private fun liberarRecursos() {
        try {
            if (isGravando) {
                isGravando = false
                try { agcEffect?.release() } catch (_: Exception) {}
                agcEffect = null
                try { nsEffect?.release() } catch (_: Exception) {}
                nsEffect = null
                audioRecord?.let {
                    try { it.stop() } catch (_: Exception) {}
                    it.release()
                }
                audioRecord = null
                recordingThread?.join(300)
                recordingThread = null
            }

            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            loudnessEnhancer = null
            mediaPlayer?.release()
            mediaPlayer = null
            isReproduzindoPrevia = false
        } catch (_: Exception) {}
    }

    private fun converterPcmParaWav(
        rawFile: File,
        wavFile: File,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val rawData = rawFile.readBytes()
        val totalAudioLen = rawData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        FileOutputStream(wavFile).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(intToByteArray(totalDataLen.toInt()))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(intToByteArray(16))
            out.write(shortToByteArray(1))
            out.write(shortToByteArray(channels.toShort()))
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate.toInt()))
            out.write(shortToByteArray((channels * bitsPerSample / 8).toShort()))
            out.write(shortToByteArray(bitsPerSample.toShort()))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(intToByteArray(totalAudioLen.toInt()))
            out.write(rawData)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        val i = value.toInt()
        return byteArrayOf(
            (i and 0xff).toByte(),
            ((i shr 8) and 0xff).toByte()
        )
    }
}
