package fatec.labweb.projetocasa.view

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.util.SoundManager
import kotlin.math.abs

class ConfiguracoesActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_configuracoes
    override fun getCurrentMenuItemId(): Int = R.id.menuConfiguracoes

    private var switchNotificacoes: SwitchMaterial? = null
    private var switchModoEscuro: SwitchMaterial? = null
    private var switchSoundFeedback: SwitchMaterial? = null
    private var btnDiminuirFonte: MaterialButton? = null
    private var btnAumentarFonte: MaterialButton? = null
    private var txtTamanhoFonte: TextView? = null
    private var sliderTamanhoFonte: Slider? = null
    private var btnTermosPrivacidade: MaterialCardView? = null
    private var btnInformacoes: MaterialCardView? = null
    private var btnSuporte: MaterialCardView? = null

    private val escalasFonte = listOf(0.85f, 1.0f, 1.15f, 1.30f)
    private val rotulosFonte = listOf("85% (Pequeno)", "100% (Padrão)", "115% (Médio)", "130% (Grande)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Garante que o layout foi inflado sem duplo setContentView
            garantirLayoutInflado()

            // Inicialização resiliente das Views e mapeamento de IDs
            initViews()

            // Carregamento de preferências do SharedPreferences com tratamento de nulos
            carregarPreferencias()

            // Associação de ouvintes de eventos e ações de clique
            setupListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Erro seguro durante onCreate de ConfiguracoesActivity: ${e.message}", e)
        }
    }

    private fun garantirLayoutInflado() {
        // Se a View ainda não estiver presente no contentFrame, infla de maneira segura
        if (findViewById<View?>(R.id.cardBannerConfig) == null && findViewById<View?>(R.id.switchModoEscuro) == null) {
            val contentFrame = findViewById<ViewGroup?>(R.id.contentFrame)
            if (contentFrame != null) {
                layoutInflater.inflate(getContentLayoutId(), contentFrame, true)
            } else {
                setContentView(getContentLayoutId())
            }
        }
    }

    private fun initViews() {
        try {
            switchNotificacoes = findViewById(R.id.switchNotificacoes) ?: findViewById(R.id.switchNotifications)
            switchModoEscuro = findViewById(R.id.switchModoEscuro) ?: findViewById(R.id.switchDarkMode)
            switchSoundFeedback = findViewById(R.id.switchSoundFeedback)
            btnDiminuirFonte = findViewById(R.id.btnDiminuirFonte)
            btnAumentarFonte = findViewById(R.id.btnAumentarFonte)
            txtTamanhoFonte = findViewById(R.id.txtTamanhoFonte)
            sliderTamanhoFonte = findViewById(R.id.sliderTamanhoFonte) ?: findViewById(R.id.seekBarFontSize)
            btnTermosPrivacidade = findViewById(R.id.btnTermosPrivacidade)
            btnInformacoes = findViewById(R.id.btnInformacoes)
            btnSuporte = findViewById(R.id.btnSuporte)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mapear componentes visuais: ${e.message}", e)
        }
    }

    private fun carregarPreferencias() {
        try {
            val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)

            // 1. Notificações FCM
            val notificacoesAtivas = prefs.getBoolean(CasaApplication.KEY_NOTIFICACOES, true)
            switchNotificacoes?.let {
                it.setOnCheckedChangeListener(null)
                it.isChecked = notificacoesAtivas
            }

            // 2. Modo Escuro
            val isDarkMode = prefs.getBoolean("KEY_DARK_MODE", false)
            switchModoEscuro?.let {
                it.setOnCheckedChangeListener(null)
                it.isChecked = isDarkMode
            }

            // 3. Tamanho da Fonte
            val fontScale = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)
            atualizarRotuloESlider(fontScale)

            // 4. Feedback Auditivo (Audio Cues) nos Botões
            val audioCuesAtivo = prefs.getBoolean(CasaApplication.KEY_AUDIO_CUES, prefs.getBoolean(CasaApplication.KEY_SOUND_FEEDBACK, true))
            switchSoundFeedback?.let {
                it.setOnCheckedChangeListener(null)
                it.isChecked = audioCuesAtivo
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro seguro ao carregar preferências: ${e.message}", e)
        }
    }

    private fun setupListeners() {
        try {
            val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)

            // 1. Notificações com Firebase Cloud Messaging (FCM)
            switchNotificacoes?.setOnCheckedChangeListener { _, isChecked ->
                try {
                    prefs.edit().putBoolean(CasaApplication.KEY_NOTIFICACOES, isChecked).apply()

                    if (isChecked) {
                        FirebaseMessaging.getInstance().subscribeToTopic("todos_usuarios")
                        FirebaseMessaging.getInstance().isAutoInitEnabled = true
                        Toast.makeText(this, "Notificações push ativadas com sucesso", Toast.LENGTH_SHORT).show()
                    } else {
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("todos_usuarios")
                        FirebaseMessaging.getInstance().isAutoInitEnabled = false
                        Toast.makeText(this, "Notificações push desativadas", Toast.LENGTH_SHORT).show()
                    }

                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                            .set(mapOf("notificacoes_ativas" to isChecked), SetOptions.merge())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao atualizar configuração de notificações: ${e.message}", e)
                }
            }

            // 2. Modo Escuro Global com AppCompatDelegate e persistência imediata
            switchModoEscuro?.setOnCheckedChangeListener { _, isChecked ->
                try {
                    prefs.edit()
                        .putBoolean("KEY_DARK_MODE", isChecked)
                        .putBoolean("dark_mode", isChecked)
                        .commit()

                    val modoDesejado = if (isChecked) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }

                    // Sincroniza preferência no Firestore /usuarios/{uid} compatível com Web API
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                            .set(mapOf("modo_escuro" to isChecked), SetOptions.merge())
                    }

                    AppCompatDelegate.setDefaultNightMode(modoDesejado)
                    recreate()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao alternar modo escuro: ${e.message}", e)
                }
            }

            // 3. Feedback Auditivo (Audio Cues) nos Botões (Acessibilidade)
            switchSoundFeedback?.setOnCheckedChangeListener { view, isChecked ->
                try {
                    prefs.edit()
                        .putBoolean(CasaApplication.KEY_AUDIO_CUES, isChecked)
                        .putBoolean(CasaApplication.KEY_SOUND_FEEDBACK, isChecked)
                        .apply()

                    if (isChecked) {
                        SoundManager.dispararAudioCue(view)
                        Toast.makeText(this, "Feedback auditivo (audio cues) ativado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Feedback auditivo (audio cues) desativado", Toast.LENGTH_SHORT).show()
                    }

                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                            .set(mapOf(
                                "feedback_sonoro" to isChecked,
                                "audio_cues" to isChecked
                            ), SetOptions.merge())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao atualizar feedback auditivo: ${e.message}", e)
                }
            }

            // 4. Controle de Tamanho da Fonte com Slider
            sliderTamanhoFonte?.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    val escalaMaisProxima = escalasFonte.minByOrNull { abs(it - value) } ?: 1.0f
                    atualizarRotulo(escalaMaisProxima)
                }
            }

            sliderTamanhoFonte?.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    val escalaMaisProxima = escalasFonte.minByOrNull { abs(it - slider.value) } ?: 1.0f
                    aplicarNovaEscalaFonte(escalaMaisProxima)
                }
            })

            // 5. Botões de Incremento e Decremento de Fonte
            btnDiminuirFonte?.setOnClickListener {
                SoundManager.dispararAudioCue(it)
                alterarEscalaPorPasso(-1)
            }

            btnAumentarFonte?.setOnClickListener {
                SoundManager.dispararAudioCue(it)
                alterarEscalaPorPasso(+1)
            }

            // 6. Modais Informativos
            btnTermosPrivacidade?.setOnClickListener {
                SoundManager.dispararAudioCue(it)
                mostrarDialog(
                    "Termos de Privacidade",
                    "O aplicativo Projeto C.A.S.A. respeita sua privacidade. Seus dados de acionamento e mídias são criptografados e protegidos em conformidade com as diretrizes de segurança da informação."
                )
            }

            btnInformacoes?.setOnClickListener {
                SoundManager.dispararAudioCue(it)
                mostrarDialog(
                    "Informações do App",
                    "Projeto C.A.S.A. - Comunicação Alternativa Simplificada e Acessível\nVersão: 1.0.0 (Build Native)\nDesenvolvido com carinho para inclusão e acessibilidade."
                )
            }

            btnSuporte?.setOnClickListener {
                SoundManager.dispararAudioCue(it)
                mostrarDialog(
                    "Suporte Técnico",
                    "Precisa de ajuda com o Projeto C.A.S.A.?\nEntre em contato pelo e-mail: suporte@projetocasa.org ou consulte a documentação oficial."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar listeners de eventos: ${e.message}", e)
        }
    }

    private fun alterarEscalaPorPasso(direcao: Int) {
        try {
            val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)
            val escalaAtual = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)
            val indiceAtual = escalasFonte.indexOfFirst { abs(it - escalaAtual) < 0.05f }.let { if (it == -1) 1 else it }

            val novoIndice = (indiceAtual + direcao).coerceIn(0, escalasFonte.size - 1)
            if (novoIndice != indiceAtual) {
                val novaEscala = escalasFonte[novoIndice]
                aplicarNovaEscalaFonte(novaEscala)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar escala por botões: ${e.message}", e)
        }
    }

    private fun aplicarNovaEscalaFonte(novaEscala: Float) {
        try {
            val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)
            val escalaAtual = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)

            if (abs(escalaAtual - novaEscala) > 0.01f) {
                prefs.edit().putFloat(CasaApplication.KEY_FONT_SCALE, novaEscala).commit()
                atualizarRotuloESlider(novaEscala)

                // Mapeia escala float para tamanho_fonte inteiro compatível com a Web API (8..14)
                val tamanhoFonteInt = when {
                    novaEscala <= 0.85f -> 8
                    novaEscala <= 1.0f -> 10
                    novaEscala <= 1.15f -> 12
                    else -> 14
                }
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                        .set(mapOf("tamanho_fonte" to tamanhoFonteInt), SetOptions.merge())
                }

                Toast.makeText(this, "Tamanho de fonte atualizado com sucesso", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                atualizarRotuloESlider(novaEscala)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar nova escala de fonte: ${e.message}", e)
        }
    }

    private fun atualizarRotuloESlider(fontScale: Float) {
        try {
            val indice = escalasFonte.indexOfFirst { abs(it - fontScale) < 0.05f }.let { if (it == -1) 1 else it }
            txtTamanhoFonte?.text = rotulosFonte.getOrElse(indice) { "100% (Padrão)" }

            val escalaValida = escalasFonte.getOrElse(indice) { 1.0f }.coerceIn(0.85f, 1.30f)
            sliderTamanhoFonte?.let { slider ->
                if (abs(slider.value - escalaValida) > 0.01f) {
                    slider.value = escalaValida
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro seguro ao atualizar rótulo e slider: ${e.message}", e)
        }
    }

    private fun atualizarRotulo(fontScale: Float) {
        try {
            val indice = escalasFonte.indexOfFirst { abs(it - fontScale) < 0.05f }.let { if (it == -1) 1 else it }
            txtTamanhoFonte?.text = rotulosFonte.getOrElse(indice) { "100% (Padrão)" }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar rótulo de fonte: ${e.message}", e)
        }
    }

    private fun mostrarDialog(titulo: String, mensagem: String) {
        try {
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this)
                    .setTitle(titulo)
                    .setMessage(mensagem)
                    .setPositiveButton("Fechar") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exibir diálogo de informação: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ConfiguracoesActivity"
    }
}
