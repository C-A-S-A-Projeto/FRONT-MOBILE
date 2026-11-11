package fatec.labweb.projetocasa.view

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.SoundManager
import java.io.File

/**
 * ModalGravadorAudio: Gravador de áudio estilo WhatsApp com Cronômetro em tempo real (00:00),
 * animação de onda sonora (waveform) reagindo à amplitude da voz e persistência física local.
 */
class ModalGravadorAudio(
    private val activity: Activity,
    private val audioId: String,
    private val onAudioGravado: (arquivoLocal: File) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var arquivoDestino: File? = null
    private var isGravando = false
    private var isReproduzindoPrevia = false

    private val handler = Handler(Looper.getMainLooper())
    private var waveformRunnable: Runnable? = null

    fun iniciar() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 301)
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_gravar_audio, null)
        val txtTitulo = dialogView.findViewById<TextView>(R.id.txtTituloGravador)
        val txtStatus = dialogView.findViewById<TextView>(R.id.txtStatusGravador)
        val chronometer = dialogView.findViewById<Chronometer>(R.id.chronometerGravacao)
        val waveformView = dialogView.findViewById<AudioWaveformView>(R.id.waveformView)
        val viewGlow = dialogView.findViewById<View>(R.id.viewGlowMic)
        val imgMic = dialogView.findViewById<ImageView>(R.id.imgMicGravador)
        val btnDescartar = dialogView.findViewById<MaterialButton>(R.id.btnDescartarGravacao)
        val btnPararOuPrevia = dialogView.findViewById<MaterialButton>(R.id.btnPararOuPrevia)
        val btnSalvar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarAudio)

        // Configuração do arquivo local em getExternalFilesDir("botoes_audios")
        val pastaAudios = activity.getExternalFilesDir("botoes_audios")
            ?: File(activity.filesDir, "botoes_audios")
        if (!pastaAudios.exists()) pastaAudios.mkdirs()
        arquivoDestino = File(pastaAudios, "$audioId.m4a")

        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog?.show()

        // Inicia gravação
        iniciarGravacaoNativa(chronometer, waveformView, txtStatus, viewGlow)

        btnPararOuPrevia.setOnClickListener {
            if (isGravando) {
                // Parar gravação e habilitar modo prévia
                pararGravacaoNativa(chronometer, txtStatus, viewGlow)
                btnPararOuPrevia.text = "Ouvir"
                btnPararOuPrevia.setIconResource(android.R.drawable.ic_media_play)
            } else if (isReproduzindoPrevia) {
                // Parar reprodução da prévia
                pararPrevia(btnPararOuPrevia, txtStatus)
            } else {
                // Reproduzir áudio gravado
                reproduzirPrevia(btnPararOuPrevia, txtStatus, waveformView)
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

        btnSalvar.setOnClickListener {
            if (isGravando) {
                pararGravacaoNativa(chronometer, txtStatus, viewGlow)
            }
            pararPrevia(btnPararOuPrevia, txtStatus)

            val arquivoFinal = arquivoDestino
            if (arquivoFinal != null && arquivoFinal.exists() && arquivoFinal.length() > 0) {
                onAudioGravado(arquivoFinal)
                liberarRecursos()
                dialog?.dismiss()
            } else {
                Toast.makeText(activity, "Nenhum áudio foi registrado.", Toast.LENGTH_SHORT).show()
                cancelarELiberar()
                dialog?.dismiss()
            }
        }
    }

    private fun iniciarGravacaoNativa(
        chronometer: Chronometer,
        waveformView: AudioWaveformView,
        txtStatus: TextView,
        viewGlow: View
    ) {
        try {
            waveformView.clear()
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(arquivoDestino?.absolutePath)
                prepare()
                start()
            }

            isGravando = true
            chronometer.base = SystemClock.elapsedRealtime()
            chronometer.start()
            txtStatus.text = "Gravando áudio de voz..."
            viewGlow.visibility = View.VISIBLE

            // Loop de amostragem de amplitude para alimentar o waveform
            waveformRunnable = object : Runnable {
                override fun run() {
                    if (isGravando && mediaRecorder != null) {
                        try {
                            val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                            waveformView.addAmplitude(maxAmp.toFloat())
                        } catch (_: Exception) {}
                        handler.postDelayed(this, 50)
                    }
                }
            }
            waveformRunnable?.let { handler.post(it) }

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
            waveformRunnable?.let { handler.removeCallbacks(it) }

            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isGravando = false

            txtStatus.text = "Gravação concluída. Ouça ou salve abaixo:"
        } catch (e: Exception) {
            isGravando = false
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun reproduzirPrevia(
        btnPararOuPrevia: MaterialButton,
        txtStatus: TextView,
        waveformView: AudioWaveformView
    ) {
        val arquivo = arquivoDestino ?: return
        if (!arquivo.exists()) return

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(arquivo.absolutePath)
                prepare()
                start()
                isReproduzindoPrevia = true
                btnPararOuPrevia.text = "Pausar"
                btnPararOuPrevia.setIconResource(android.R.drawable.ic_media_pause)
                txtStatus.text = "Reproduzindo prévia..."

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
            arquivoDestino?.delete()
        } catch (_: Exception) {}
    }

    private fun liberarRecursos() {
        waveformRunnable?.let { handler.removeCallbacks(it) }
        try {
            if (isGravando) {
                mediaRecorder?.stop()
            }
            mediaRecorder?.release()
            mediaRecorder = null
            isGravando = false

            mediaPlayer?.release()
            mediaPlayer = null
            isReproduzindoPrevia = false
        } catch (_: Exception) {}
    }
}
