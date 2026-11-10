package fatec.labweb.projetocasa.view

import android.content.Context
import android.os.Bundle
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
import com.google.firebase.messaging.FirebaseMessaging
import fatec.labweb.projetocasa.CasaApplication
import fatec.labweb.projetocasa.R
import kotlin.math.abs

class ConfiguracoesActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int = R.layout.activity_configuracoes
    override fun getCurrentMenuItemId(): Int = R.id.menuConfiguracoes

    private lateinit var switchNotificacoes: SwitchMaterial
    private lateinit var switchModoEscuro: SwitchMaterial
    private lateinit var btnDiminuirFonte: MaterialButton
    private lateinit var btnAumentarFonte: MaterialButton
    private lateinit var txtTamanhoFonte: TextView
    private lateinit var sliderTamanhoFonte: Slider
    private lateinit var btnTermosPrivacidade: MaterialCardView
    private lateinit var btnInformacoes: MaterialCardView
    private lateinit var btnSuporte: MaterialCardView

    private val escalasFonte = listOf(0.85f, 1.0f, 1.15f, 1.30f)
    private val rotulosFonte = listOf("85% (Pequeno)", "100% (Padrão)", "115% (Médio)", "130% (Grande)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        carregarPreferencias()
        setupListeners()
    }

    private fun initViews() {
        switchNotificacoes = findViewById(R.id.switchNotificacoes)
        switchModoEscuro = findViewById(R.id.switchModoEscuro)
        btnDiminuirFonte = findViewById(R.id.btnDiminuirFonte)
        btnAumentarFonte = findViewById(R.id.btnAumentarFonte)
        txtTamanhoFonte = findViewById(R.id.txtTamanhoFonte)
        sliderTamanhoFonte = findViewById(R.id.sliderTamanhoFonte)
        btnTermosPrivacidade = findViewById(R.id.btnTermosPrivacidade)
        btnInformacoes = findViewById(R.id.btnInformacoes)
        btnSuporte = findViewById(R.id.btnSuporte)
    }

    private fun carregarPreferencias() {
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Notificações FCM
        val notificacoesAtivas = prefs.getBoolean(CasaApplication.KEY_NOTIFICACOES, true)
        switchNotificacoes.setOnCheckedChangeListener(null)
        switchNotificacoes.isChecked = notificacoesAtivas

        // 2. Modo Escuro
        val modoEscuroAtivo = prefs.getBoolean("KEY_DARK_MODE", prefs.getBoolean(CasaApplication.KEY_DARK_MODE, false))
        switchModoEscuro.setOnCheckedChangeListener(null)
        switchModoEscuro.isChecked = modoEscuroAtivo

        // 3. Tamanho da Fonte
        val fontScale = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)
        atualizarRotuloESlider(fontScale)
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Notificações com Firebase Cloud Messaging (FCM)
        switchNotificacoes.setOnCheckedChangeListener { _, isChecked ->
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
                    .update("notificacoes_ativas", isChecked)
            }
        }

        // 2. Modo Escuro Global com AppCompatDelegate e persistência imediata
        switchModoEscuro.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean("KEY_DARK_MODE", isChecked)
                .putBoolean("dark_mode", isChecked)
                .commit()

            val modoDesejado = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            AppCompatDelegate.setDefaultNightMode(modoDesejado)
            recreate()
        }

        // 3. Controle de Tamanho da Fonte com Slider
        sliderTamanhoFonte.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val escalaMaisProxima = escalasFonte.minByOrNull { abs(it - value) } ?: 1.0f
                atualizarRotulo(escalaMaisProxima)
            }
        }

        sliderTamanhoFonte.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                val escalaMaisProxima = escalasFonte.minByOrNull { abs(it - slider.value) } ?: 1.0f
                aplicarNovaEscalaFonte(escalaMaisProxima)
            }
        })

        // 4. Botões de Incremento e Decremento de Fonte
        btnDiminuirFonte.setOnClickListener {
            alterarEscalaPorPasso(-1)
        }

        btnAumentarFonte.setOnClickListener {
            alterarEscalaPorPasso(+1)
        }

        // 5. Modais Informativos
        btnTermosPrivacidade.setOnClickListener {
            mostrarDialog("Termos de Privacidade", "O aplicativo Projeto C.A.S.A. respeita sua privacidade. Seus dados de acionamento e mídias são criptografados e protegidos em conformidade com as diretrizes de segurança da informação.")
        }

        btnInformacoes.setOnClickListener {
            mostrarDialog("Informações do App", "Projeto C.A.S.A. - Comunicação Alternativa Simplificada e Acessível\nVersão: 1.0.0 (Build Native)\nDesenvolvido com carinho para inclusão e acessibilidade.")
        }

        btnSuporte.setOnClickListener {
            mostrarDialog("Suporte Técnico", "Precisa de ajuda com o Projeto C.A.S.A.?\nEntre em contato pelo e-mail: suporte@projetocasa.org ou consulte a documentação oficial.")
        }
    }

    private fun alterarEscalaPorPasso(direcao: Int) {
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val escalaAtual = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)
        val indiceAtual = escalasFonte.indexOfFirst { abs(it - escalaAtual) < 0.05f }.let { if (it == -1) 1 else it }

        val novoIndice = (indiceAtual + direcao).coerceIn(0, escalasFonte.size - 1)
        if (novoIndice != indiceAtual) {
            val novaEscala = escalasFonte[novoIndice]
            aplicarNovaEscalaFonte(novaEscala)
        }
    }

    private fun aplicarNovaEscalaFonte(novaEscala: Float) {
        val prefs = getSharedPreferences(CasaApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val escalaAtual = prefs.getFloat(CasaApplication.KEY_FONT_SCALE, 1.0f)

        if (abs(escalaAtual - novaEscala) > 0.01f) {
            prefs.edit().putFloat(CasaApplication.KEY_FONT_SCALE, novaEscala).commit()
            atualizarRotuloESlider(novaEscala)
            Toast.makeText(this, "Tamanho de fonte atualizado com sucesso", Toast.LENGTH_SHORT).show()
            recreate()
        } else {
            atualizarRotuloESlider(novaEscala)
        }
    }

    private fun atualizarRotuloESlider(fontScale: Float) {
        val indice = escalasFonte.indexOfFirst { abs(it - fontScale) < 0.05f }.let { if (it == -1) 1 else it }
        txtTamanhoFonte.text = rotulosFonte[indice]

        val valorSlider = when (indice) {
            0 -> 0f
            1 -> 33.33f
            2 -> 66.66f
            else -> 100f
        }
        sliderTamanhoFonte.value = valorSlider
    }

    private fun atualizarRotulo(fontScale: Float) {
        val indice = escalasFonte.indexOfFirst { abs(it - fontScale) < 0.05f }.let { if (it == -1) 1 else it }
        txtTamanhoFonte.text = rotulosFonte[indice]
    }

    private fun mostrarDialog(titulo: String, mensagem: String) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensagem)
            .setPositiveButton("Fechar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
