package fatec.labweb.projetocasa.view

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import fatec.labweb.projetocasa.R

class CadastroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var scroll: ScrollView

    private lateinit var tilNome: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilSenha: TextInputLayout
    private lateinit var tilConfirmar: TextInputLayout

    private lateinit var edtNome: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtSenha: TextInputEditText
    private lateinit var edtConfirmar: TextInputEditText

    private lateinit var reqTamanho: TextView
    private lateinit var reqMaiuscula: TextView
    private lateinit var reqNumero: TextView
    private lateinit var reqIgual: TextView

    private lateinit var txtMensagem: TextView
    private lateinit var btnCadastrar: MaterialButton

    private enum class Tipo { ERRO, AVISO, SUCESSO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        auth = FirebaseAuth.getInstance()

        scroll = findViewById(R.id.scrollCadastro)

        tilNome = findViewById(R.id.tilNome)
        tilEmail = findViewById(R.id.tilEmailCadastro)
        tilSenha = findViewById(R.id.tilSenhaCadastro)
        tilConfirmar = findViewById(R.id.tilConfirmar)

        edtNome = findViewById(R.id.edtNomeCadastro)
        edtEmail = findViewById(R.id.edtEmailCadastro)
        edtSenha = findViewById(R.id.edtSenhaCadastro)
        edtConfirmar = findViewById(R.id.edtConfirmarCadastro)

        reqTamanho = findViewById(R.id.reqTamanho)
        reqMaiuscula = findViewById(R.id.reqMaiuscula)
        reqNumero = findViewById(R.id.reqNumero)
        reqIgual = findViewById(R.id.reqIgual)

        txtMensagem = findViewById(R.id.txtMensagemCadastro)
        btnCadastrar = findViewById(R.id.btnCadastrarConta)

        findViewById<ImageButton>(R.id.btnVoltarCadastro).setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtIrParaLogin).setOnClickListener {
            irParaLogin(null)
        }


        edtNome.doAfterTextChanged { tilNome.error = null }
        edtEmail.doAfterTextChanged { tilEmail.error = null }
        edtSenha.doAfterTextChanged {
            tilSenha.error = null
            atualizarRequisitos()
        }
        edtConfirmar.doAfterTextChanged {
            tilConfirmar.error = null
            atualizarRequisitos()
        }

        btnCadastrar.setOnClickListener { fazerCadastro() }
    }


    private fun temNumeroOuEspecial(senha: String) =
        senha.any { it.isDigit() || (!it.isLetterOrDigit() && !it.isWhitespace()) }

    private fun senhaValida(senha: String) =
        senha.length >= 8 &&
                senha.any { it.isUpperCase() } &&
                temNumeroOuEspecial(senha)

    private fun atualizarRequisitos() {
        val senha = edtSenha.text.toString()
        val confirmar = edtConfirmar.text.toString()

        marcar(reqTamanho, senha.length >= 8, "Mínimo de 8 caracteres")
        marcar(reqMaiuscula, senha.any { it.isUpperCase() }, "Uma letra maiúscula")
        marcar(reqNumero, temNumeroOuEspecial(senha), "Um número ou caractere especial")
        marcar(reqIgual, senha.isNotEmpty() && senha == confirmar, "Senhas iguais")
    }

    private fun marcar(tv: TextView, ok: Boolean, texto: String) {
        if (ok) {
            tv.text = "✔ $texto"
            tv.setTextColor(Color.parseColor("#166534")) // verde
        } else {
            tv.text = "✖ $texto"
            tv.setTextColor(Color.parseColor("#B91C1C")) // vermelho
        }
    }

    private fun fazerCadastro() {
        limparErros()
        esconderTeclado()

        val nome = edtNome.text.toString().trim()
        val email = edtEmail.text.toString().trim()
        val senha = edtSenha.text.toString()
        val confirmar = edtConfirmar.text.toString()


        val vazios = mutableListOf<String>()
        if (nome.isEmpty()) { tilNome.error = "Campo obrigatório"; vazios.add("Nome") }
        if (email.isEmpty()) { tilEmail.error = "Campo obrigatório"; vazios.add("E-mail") }
        if (senha.isEmpty()) { tilSenha.error = "Campo obrigatório"; vazios.add("Senha") }
        if (confirmar.isEmpty()) { tilConfirmar.error = "Campo obrigatório"; vazios.add("Confirmar senha") }

        if (vazios.isNotEmpty()) {
            atualizarRequisitos()
            mostrarMensagem("Preencha: ${vazios.joinToString(", ")}.", Tipo.AVISO)
            return
        }


        val erros = mutableListOf<String>()

        if (nome.length < 3) {
            tilNome.error = "Mínimo de 3 caracteres"
            erros.add("Nome")
        } else if (!nome.all { it.isLetter() || it.isWhitespace() }) {
            tilNome.error = "Use apenas letras"
            erros.add("Nome")
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "E-mail inválido"
            erros.add("E-mail")
        }

        if (!senhaValida(senha)) {
            tilSenha.error = "A senha não atende aos requisitos"
            erros.add("Senha")
        }

        if (senha != confirmar) {
            tilConfirmar.error = "As senhas não coincidem"
            erros.add("Confirmar senha")
        }

        if (erros.isNotEmpty()) {
            atualizarRequisitos()
            mostrarMensagem("Corrija o(s) campo(s): ${erros.joinToString(", ")}.", Tipo.ERRO)
            return
        }


        definirCarregando(true)

        auth.createUserWithEmailAndPassword(email, senha)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val usuario = task.result?.user


                    val perfil = UserProfileChangeRequest.Builder()
                        .setDisplayName(nome)
                        .build()

                    if (usuario != null) {
                        usuario.updateProfile(perfil).addOnCompleteListener {
                            finalizarCadastro(email)
                        }
                    } else {
                        finalizarCadastro(email)
                    }
                } else {
                    definirCarregando(false)
                    tratarErroFirebase(task.exception)
                }
            }
    }
    private fun finalizarCadastro(email: String) {

        auth.signOut()

        mostrarMensagem("Conta criada com sucesso! Redirecionando para o login...", Tipo.SUCESSO)

        Handler(Looper.getMainLooper()).postDelayed({
            irParaLogin(email)
        }, 1500)
    }

    private fun irParaLogin(email: String?) {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (email != null) intent.putExtra("email_cadastrado", email)
        startActivity(intent)
        finish()
    }

    private fun tratarErroFirebase(e: Exception?) {
        when (e) {
            is FirebaseAuthUserCollisionException -> {
                tilEmail.error = "E-mail já cadastrado"
                mostrarMensagem("Erro no campo E-mail: já existe uma conta com este e-mail.", Tipo.ERRO)
            }
            is FirebaseAuthWeakPasswordException -> {
                tilSenha.error = "Senha muito fraca"
                mostrarMensagem("Erro no campo Senha: escolha uma senha mais forte.", Tipo.ERRO)
            }
            is FirebaseAuthInvalidCredentialsException -> {
                tilEmail.error = "E-mail inválido"
                mostrarMensagem("Erro no campo E-mail: formato inválido.", Tipo.ERRO)
            }
            is FirebaseNetworkException ->
                mostrarMensagem("Erro de conexão. Verifique sua internet.", Tipo.ERRO)
            else ->
                mostrarMensagem("Erro ao cadastrar: ${e?.localizedMessage ?: "tente novamente."}", Tipo.ERRO)
        }
    }

    // ---------- Auxiliares ----------
    private fun definirCarregando(carregando: Boolean) {
        btnCadastrar.isEnabled = !carregando
        btnCadastrar.text = if (carregando) "Cadastrando..." else "Cadastrar"
    }

    private fun limparErros() {
        tilNome.error = null
        tilEmail.error = null
        tilSenha.error = null
        tilConfirmar.error = null
        txtMensagem.visibility = View.GONE
    }

    private fun esconderTeclado() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun mostrarMensagem(texto: String, tipo: Tipo) {
        val (fundo, corTexto) = when (tipo) {
            Tipo.ERRO -> Pair("#FEE2E2", "#B91C1C")     // vermelho
            Tipo.AVISO -> Pair("#FEF9C3", "#A16207")    // amarelo
            Tipo.SUCESSO -> Pair("#DCFCE7", "#166534")  // verde
        }

        val bg = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(Color.parseColor(fundo))
            setStroke(2, Color.parseColor(corTexto))
        }

        txtMensagem.background = bg
        txtMensagem.setTextColor(Color.parseColor(corTexto))
        txtMensagem.text = texto
        txtMensagem.visibility = View.VISIBLE

        // Garante que a mensagem apareça, mesmo com o teclado ou a tela rolada
        scroll.post { scroll.smoothScrollTo(0, txtMensagem.top) }
    }
}