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
 * SoundManager: Utilitário Singleton de efeito sonoro utilizando SoundPool para alta performance
 * e baixa latência ao clicar em componentes de interface (Acessibilidade & UX).
 */
object SoundManager {

    private var soundPool: SoundPool? = null
    private var soundClickId: Int = 0
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

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
     * Executa feedback sonoro instantâneo ao clicar em um botão ou card.
     * Respeita a preferência do usuário configurada em ConfiguracoesActivity.
     */
    fun playClickSound(view: View? = null, context: Context? = view?.context) {
        val ctx = context ?: view?.context ?: return
        if (!isSoundFeedbackEnabled(ctx)) return

        try {
            // 1. Toca pelo SoundPool para latência mínima e som dedicado
            if (soundClickId != 0 && soundPool != null) {
                soundPool?.play(soundClickId, 0.9f, 0.9f, 1, 0, 1.0f)
            }

            // 2. Feedback sonoro nativo do sistema Android
            if (view != null) {
                view.isSoundEffectsEnabled = true
                view.playSoundEffect(SoundEffectConstants.CLICK)
            } else {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundClickId = 0
        isInitialized = false
    }
}
