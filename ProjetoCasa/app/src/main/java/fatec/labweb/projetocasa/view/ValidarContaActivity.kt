package fatec.labweb.projetocasa.view

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import fatec.labweb.projetocasa.R

class ValidarContaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORIGEM = "origem"
        const val EXTRA_EMAIL = "email"
        const val ORIGEM_CADASTRO = "CADASTRO"
        const val ORIGEM_REDEFINIR = "REDEFINIR_SENHA"
        private const val TEMPO_REENVIO_MS = 60_000L
        private const val CODIGO_TESTE = "123456" // REMOVER quando ligar o backend
    }

    private enum class Tipo { ERRO, AVISO, SUCESSO }

    private lateinit var origem: String
    private lateinit var email: String
    private var timer: CountDownTimer? = null
    private var podeReenviar = false

    private lateinit var tilCodigo: TextInputLayout
    private lateinit var edtCodigo: TextInputEditText
    private lateinit var txtTempo: TextView
    private lateinit var txtMensagem: TextView
    private lateinit var progresso: LinearProgressIndicator
    private lateinit var btnValidar: MaterialButton
    private lateinit var btnReenviar: MaterialButton
    private lateinit var overlay: FrameLayout
    private lateinit var cardSucesso: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_validarconta)

        origem = intent.getStringExtra(EXTRA_ORIGEM) ?: ORIGEM_CADASTRO
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""

        tilCodigo = findViewById(R.id.tilCodigo)
        edtCodigo = findViewById(R.id.edtCodigo)
        txtTempo = findViewById(R.id.txtTempo)
        txtMensagem = findViewById(R.id.txtMensagem)
        progresso = findViewById(R.id.progresso)
        btnValidar = findViewById(R.id.btnValidar)
        btnReenviar = findViewById(R.id.btnReenviar)
        overlay = findViewById(R.id.overlaySucesso)
        cardSucesso = findViewById(R.id.cardSucesso)

        findViewById<TextView>(R.id.txtEmailDestino).text = email

        edtCodigo.doAfterTextChanged {
            tilCodigo.error = null
            btnValidar.isEnabled = (it?.length ?: 0) == 6
        }

        findViewById<ImageButton>(R.id.btnVoltarTopo).setOnClickListener { finish() }
        btnValidar.setOnClickListener { validar() }
        btnReenviar.setOnClickListener { reenviar() }

        // Com o flutuante aberto, o botão voltar do sistema não fecha a tela
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (overlay.visibility == View.VISIBLE) return
                finish()
            }
        })

        iniciarTemporizador()
    }

    // ---------- Temporizador e reenvio ----------
    private fun iniciarTemporizador() {
        timer?.cancel()
        podeReenviar = false
        btnReenviar.isEnabled = false
        timer = object : CountDownTimer(TEMPO_REENVIO_MS, 1000) {
            override fun onTick(restante: Long) {
                val s = (restante / 1000).toInt()
                txtTempo.text = "Você poderá reenviar em %02d:%02d".format(s / 60, s % 60)
            }

            override fun onFinish() {
                txtTempo.text = "Não recebeu o código?"
                podeReenviar = true
                btnReenviar.isEnabled = true
            }
        }.start()
    }

    private fun reenviar() {
        carregando(true)
        esconderMensagem()
        enviarCodigo(email) { ok ->
            carregando(false)
            if (ok) {
                edtCodigo.text?.clear()
                mostrarMensagem(Tipo.SUCESSO, "Enviamos um novo código para $email.")
                iniciarTemporizador()
            } else {
                mostrarMensagem(Tipo.ERRO, "Não foi possível reenviar. Tente novamente.")
            }
        }
    }

    // ---------- Validação ----------
    private fun validar() {
        val codigo = edtCodigo.text.toString()
        carregando(true)
        esconderMensagem()

        verificarCodigo(email, codigo) { ok ->
            carregando(false)
            if (ok) {
                timer?.cancel()
                mostrarFlutuante()
            } else {
                tilCodigo.error = "Código incorreto ou expirado"
                mostrarMensagem(Tipo.ERRO, "Confira o código no seu e-mail e tente de novo.")
            }
        }
    }

    // ---------- Flutuante de sucesso ----------
    private fun mostrarFlutuante() {
        val titulo = findViewById<TextView>(R.id.txtSucessoTitulo)
        val texto = findViewById<TextView>(R.id.txtSucessoTexto)
        val botao = findViewById<MaterialButton>(R.id.btnContinuar)

        if (origem == ORIGEM_CADASTRO) {
            titulo.text = "Cadastro realizado!"
            texto.text = "Sua conta foi criada com sucesso. Vamos para o início."
            botao.text = "Ir para o início"
            botao.setOnClickListener { irPara(HomeActivity::class.java, limparPilha = true) }
        } else {
            titulo.text = "Código confirmado!"
            texto.text = "Agora você já pode criar uma nova senha."
            botao.text = "Criar nova senha"
            botao.setOnClickListener { irPara(RedefinirSenhaActivity::class.java, limparPilha = false) }
        }

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        cardSucesso.scaleX = 0.85f
        cardSucesso.scaleY = 0.85f
        overlay.animate().alpha(1f).setDuration(200).start()
        cardSucesso.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
    }

    private fun irPara(destino: Class<*>, limparPilha: Boolean) {
        val intent = Intent(this, destino)
        if (limparPilha) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // ---------- LIGAR AO SEU BACKEND ----------
    // TODO: chamar sua Cloud Function que gera e envia o código por e-mail
    private fun enviarCodigo(email: String, aoTerminar: (Boolean) -> Unit) {
        aoTerminar(true)
    }

    // TODO: chamar sua Cloud Function que confere o código digitado
    private fun verificarCodigo(email: String, codigo: String, aoTerminar: (Boolean) -> Unit) {
        aoTerminar(codigo == CODIGO_TESTE)
    }

    // ---------- Interface ----------
    private fun carregando(ativo: Boolean) {
        progresso.visibility = if (ativo) View.VISIBLE else View.GONE
        edtCodigo.isEnabled = !ativo
        if (ativo) {
            btnValidar.isEnabled = false
            btnReenviar.isEnabled = false
        } else {
            btnValidar.isEnabled = edtCodigo.text.toString().length == 6
            btnReenviar.isEnabled = podeReenviar
        }
    }

    private fun mostrarMensagem(tipo: Tipo, mensagem: String) {
        val cores = when (tipo) {
            Tipo.ERRO -> listOf("#FEF2F2", "#FECACA", "#B91C1C", "✕ ")
            Tipo.AVISO -> listOf("#FFF9C4", "#FDE68A", "#92400E", "⚠ ")
            Tipo.SUCESSO -> listOf("#ECFDF5", "#A7F3D0", "#047857", "✓ ")
        }
        val d = resources.displayMetrics.density
        txtMensagem.background = GradientDrawable().apply {
            cornerRadius = 12f * d
            setColor(Color.parseColor(cores[0]))
            setStroke((1 * d).toInt(), Color.parseColor(cores[1]))
        }
        txtMensagem.setTextColor(Color.parseColor(cores[2]))
        txtMensagem.text = cores[3] + mensagem
        txtMensagem.visibility = View.VISIBLE
    }

    private fun esconderMensagem() {
        txtMensagem.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}