package fatec.labweb.projetocasa.view

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import fatec.labweb.projetocasa.R

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilSenha: TextInputLayout
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtSenha: TextInputEditText
    private lateinit var txtMensagem: TextView
    private lateinit var btnEntrar: MaterialButton

    private enum class Tipo { ERRO, AVISO, SUCESSO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        tilEmail = findViewById(R.id.tilEmail)
        tilSenha = findViewById(R.id.tilSenha)
        edtEmail = findViewById(R.id.edtEmailLogin)
        edtSenha = findViewById(R.id.edtSenhaLogin)
        txtMensagem = findViewById(R.id.txtMensagem)
        btnEntrar = findViewById(R.id.btnEntrar)

        findViewById<ImageButton>(R.id.btnVoltar).setOnClickListener { finish() }

        intent.getStringExtra("email_cadastrado")?.let { emailCadastrado ->
            edtEmail.setText(emailCadastrado)
        }

        findViewById<TextView>(R.id.txtEsqueciSenha).setOnClickListener {
            enviarEmailRecuperacao()
        }

        findViewById<TextView>(R.id.txtIrParaCadastro).setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        btnEntrar.setOnClickListener { fazerLogin() }
    }

    private fun enviarEmailRecuperacao() {
        limparErros()
        val email = edtEmail.text.toString().trim()

        if (email.isEmpty()) {
            tilEmail.error = "Informe seu e-mail"
            tratarErroTemporario("Digite seu e-mail no campo acima para redefinir a senha.", Tipo.AVISO)
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "E-mail inválido"
            tratarErroTemporario("Erro no campo E-mail: formato inválido.", Tipo.ERRO)
            return
        }

        bloquearCampos(false)
        btnEntrar.isEnabled = false

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                bloquearCampos(true)
                btnEntrar.isEnabled = true

                if (task.isSuccessful) {
                    mostrarMensagem("Link de redefinição enviado para $email.", Tipo.SUCESSO)
                } else {
                    val erroMsg = when (task.exception) {
                        is FirebaseAuthInvalidUserException -> {
                            tilEmail.error = "E-mail não cadastrado"
                            "Conta não encontrada para este e-mail."
                        }
                        is FirebaseNetworkException ->
                            "Erro de conexão. Verifique sua internet."
                        else ->
                            "Não foi possível enviar o e-mail de redefinição."
                    }
                    tratarErroTemporario(erroMsg, Tipo.ERRO)
                }
            }
    }

    private fun fazerLogin() {
        limparErros()

        val email = edtEmail.text.toString().trim()
        val senha = edtSenha.text.toString()

        if (email.isEmpty() && senha.isEmpty()) {
            tilEmail.error = "Campo obrigatório"
            tilSenha.error = "Campo obrigatório"
            tratarErroTemporario("Preencha o e-mail e a senha.", Tipo.AVISO)
            return
        }
        if (email.isEmpty()) {
            tilEmail.error = "Campo obrigatório"
            tratarErroTemporario("Preencha o campo de e-mail.", Tipo.AVISO)
            return
        }
        if (senha.isEmpty()) {
            tilSenha.error = "Campo obrigatório"
            tratarErroTemporario("Preencha o campo de senha.", Tipo.AVISO)
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "E-mail inválido"
            tratarErroTemporario("Erro no campo E-mail: formato inválido.", Tipo.ERRO)
            return
        }

        // Bloqueia interações enquanto valida no Firebase
        bloquearCampos(false)
        btnEntrar.isEnabled = false

        auth.signInWithEmailAndPassword(email, senha)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // 🟢 Sucesso: Mantém bloqueado e vai para a Home
                    mostrarMensagem("Login realizado com sucesso!", Tipo.SUCESSO)
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }, 1000)
                } else {
                    // 🔴 Erro do Firebase: Trata com o temporizador de 5 segundos
                    tratarErroFirebase(task.exception)
                }
            }
    }

    private fun tratarErroFirebase(e: Exception?) {
        val mensagemErro = when (e) {
            is FirebaseAuthInvalidUserException -> {
                tilEmail.error = "E-mail não cadastrado"
                "Erro no campo E-mail: conta não encontrada."
            }
            is FirebaseAuthInvalidCredentialsException -> {
                when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> {
                        tilEmail.error = "E-mail inválido"
                        "Erro no campo E-mail: formato inválido."
                    }
                    "ERROR_WRONG_PASSWORD" -> {
                        tilSenha.error = "Senha incorreta"
                        "Erro no campo Senha: senha incorreta."
                    }
                    else -> {
                        tilEmail.error = " "
                        tilSenha.error = "E-mail ou senha incorretos"
                        "Erro: e-mail ou senha incorretos."
                    }
                }
            }
            is FirebaseNetworkException ->
                "Erro de conexão. Verifique sua internet."
            else ->
                "Erro ao entrar: ${e?.localizedMessage ?: "tente novamente."}"
        }

        tratarErroTemporario(mensagemErro, Tipo.ERRO)
    }

    private fun tratarErroTemporario(mensagem: String, tipo: Tipo) {
        // Exibe a mensagem de erro/aviso e bloqueia os campos
        mostrarMensagem(mensagem, tipo)
        bloquearCampos(false)
        btnEntrar.isEnabled = false

        // Aguarda exatamente 5 segundos (5000ms)
        Handler(Looper.getMainLooper()).postDelayed({
            limparErros()         // Some com a mensagem e os erros visuais dos TextInputLayouts
            bloquearCampos(true)  // Libera os campos para o usuário editar (o texto digitado é mantido)
            btnEntrar.isEnabled = true
        }, 5000)
    }

    private fun bloquearCampos(habilitar: Boolean) {
        edtEmail.isEnabled = habilitar
        edtSenha.isEnabled = habilitar
    }

    private fun limparErros() {
        tilEmail.error = null
        tilSenha.error = null
        txtMensagem.visibility = View.GONE
    }

    private fun mostrarMensagem(texto: String, tipo: Tipo) {
        val (fundo, textoCor) = when (tipo) {
            Tipo.ERRO -> Pair("#FEE2E2", "#B91C1C")     // vermelho
            Tipo.AVISO -> Pair("#FEF9C3", "#A16207")    // amarelo
            Tipo.SUCESSO -> Pair("#DCFCE7", "#166534")  // verde
        }

        val bg = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(Color.parseColor(fundo))
            setStroke(2, Color.parseColor(textoCor))
        }

        txtMensagem.background = bg
        txtMensagem.setTextColor(Color.parseColor(textoCor))
        txtMensagem.text = texto
        txtMensagem.visibility = View.VISIBLE
    }
}