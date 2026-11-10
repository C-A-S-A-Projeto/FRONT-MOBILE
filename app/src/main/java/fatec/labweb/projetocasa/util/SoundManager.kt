package fatec.labweb.projetocasa.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.view.SoundEffectConstants
import android.view.View
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R

/**
 * SoundManager: Gerenciador de Feedback Auditivo (Audio Cues) e efeitos sonoros nativos
 * (SoundEffectConstants.CLICK) utilizando SoundPool e AudioManager para acessibilidade e resposta auditiva imediata.
 */
object SoundManager {

    private var soundPool: SoundPool? = null
    private var soundClickId: Int = 0
    private var isSoundLoaded: Boolean = false
    private var isInitialized: Boolean = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == soundClickId) {
                    isSoundLoaded = true
                }
            }

            soundClickId = soundPool?.load(context.applicationContext, R.raw.sound_click, 1) ?: 0
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Verifica se o feedback auditivo (Audio Cues) está ativado nas preferências.
     */
    fun isAudioCuesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(CasaApplication.KEY_AUDIO_CUES, prefs.getBoolean(CasaApplication.KEY_SOUND_FEEDBACK, true))
    }

    fun isSoundFeedbackEnabled(context: Context): Boolean = isAudioCuesEnabled(context)

    /**
     * Método utilitário reutilizável: Dispara a audio cue nativa do Android (SoundEffectConstants.CLICK)
     * garantindo a ativação dos efeitos sonoros na View e com suporte a SoundPool/AudioManager.
     */
    fun dispararAudioCue(view: View) {
        val context = view.context
        if (!isAudioCuesEnabled(context)) return

        try {
            // 1. Ativa efeitos sonoros na View e dispara a audio cue nativa
            view.isSoundEffectsEnabled = true
            view.playSoundEffect(SoundEffectConstants.CLICK)

            // 2. Garantia de feedback sonoro físico audível
            if (soundPool != null && soundClickId != 0 && isSoundLoaded) {
                soundPool?.play(soundClickId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Métodos utilitários de conveniência e compatibilidade.
     */
    fun tocarSomClique(view: View) {
        dispararAudioCue(view)
    }

    fun playClickSound(view: View? = null, context: Context? = view?.context) {
        if (view != null) {
            dispararAudioCue(view)
        } else if (context != null && isAudioCuesEnabled(context)) {
            try {
                if (soundPool != null && soundClickId != 0 && isSoundLoaded) {
                    soundPool?.play(soundClickId, 1.0f, 1.0f, 1, 0, 1.0f)
                } else {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundClickId = 0
        isSoundLoaded = false
        isInitialized = false
    }
}
