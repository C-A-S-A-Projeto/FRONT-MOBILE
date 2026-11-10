package fatec.labweb.projetocasa

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class CasaApplication : Application() {

    companion object {
        const val PREFS_NAME = "app_config"
        const val KEY_DARK_MODE = "KEY_DARK_MODE"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_NOTIFICACOES = "notificacoes_ativas"
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, prefs.getBoolean("dark_mode", false))
        val targetMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }
}
