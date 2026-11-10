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
 * SoundManager: Utilitário Singleton de efeito sonoro e feedback tátil/auditivo nativo (SoundEffectConstants.CLICK)
 * com suporte a SoundPool e AudioManager para garantia absoluta de reprodução.
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
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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

    fun isSoundFeedbackEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(CasaApplication.KEY_SOUND_FEEDBACK, true)
    }

    /**
     * Requisito Obrigatório: Executa o feedback sonoro nativo de toque do Android (SoundEffectConstants.CLICK)
     * e garante a execução audível mesmo que o sistema do dispositivo esteja com efeitos de toque desativados.
     */
    fun tocarSomClique(view: View) {
        val context = view.context
        if (!isSoundFeedbackEnabled(context)) return

        try {
            // 1. Força a ativação de efeitos sonoros na View e executa o som nativo
            view.isSoundEffectsEnabled = true
            view.playSoundEffect(SoundEffectConstants.CLICK)

            // 2. Fallback sonoro imediato via SoundPool ou AudioManager para garantir reprodução física
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
     * Executa feedback sonoro instantâneo ao clicar em um botão ou card.
     */
    fun playClickSound(view: View? = null, context: Context? = view?.context) {
        if (view != null) {
            tocarSomClique(view)
        } else if (context != null && isSoundFeedbackEnabled(context)) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
                if (soundPool != null && soundClickId != 0 && isSoundLoaded) {
                    soundPool?.play(soundClickId, 1.0f, 1.0f, 1, 0, 1.0f)
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
