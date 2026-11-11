package fatec.labweb.projetocasa.view

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.service.PerfilService
import fatec.labweb.projetocasa.util.SoundManager

class RedefinirSenhaActivity : AppCompatActivity() {

    private lateinit var perfilService: PerfilService
    private lateinit var scroll: ScrollView

    private lateinit var tilSenhaAtual: TextInputLayout
    private lateinit var tilNovaSenha: TextInputLayout
    private lateinit var tilConfirmar: TextInputLayout
    private lateinit var edtSenhaAtual: TextInputEditText
    private lateinit var edtNovaSenha: TextInputEditText
    private lateinit var edtConfirmar: TextInputEditText

    private lateinit var reqTamanho: TextView
    private lateinit var reqMaiuscula: TextView
    private lateinit var reqNumero: TextView
    private lateinit var reqIgual: TextView

    private lateinit var txtMensagem: TextView
    private lateinit var progresso: LinearProgressIndicator
    private lateinit var btnSalvar: MaterialButton

    private enum class Tipo { ERRO, AVISO, SUCESSO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_redefinirsenha)

        perfilService = PerfilService()

        scroll = findViewById(R.id.scroll)
        tilSenhaAtual = findViewById(R.id.tilSenhaAtual)
        tilNovaSenha = findViewById(R.id.tilNovaSenha)
        tilConfirmar = findViewById(R.id.tilConfirmar)
        edtSenhaAtual = findViewById(R.id.edtSenhaAtual)
        edtNovaSenha = findViewById(R.id.edtNovaSenha)
        edtConfirmar = findViewById(R.id.edtConfirmar)

        reqTamanho = findViewById(R.id.reqTamanho)
        reqMaiuscula = findViewById(R.id.reqMaiuscula)
        reqNumero = findViewById(R.id.reqNumero)
        reqIgual = findViewById(R.id.reqIgual)

        txtMensagem = findViewById(R.id.txtMensagem)
        progresso = findViewById(R.id.progresso)
        btnSalvar = findViewById(R.id.btnSalvar)

        findViewById<ImageButton>(R.id.btnVoltarTopo)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            finish()
        }
        findViewById<View>(R.id.btnVoltar)?.setOnClickListener { view ->
            SoundManager.dispararAudioCue(view)
            finish()
        }

        edtSenhaAtual.doAfterTextChanged {
            tilSenhaAtual.error = null
            atualizarRequisitos()
        }

        edtNovaSenha.doAfterTextChanged {
            tilNovaSenha.error = null
            atualizarRequisitos()
        }

        edtConfirmar.doAfterTextChanged {
            tilConfirmar.error = null
            atualizarRequisitos()
        }

        btnSalvar.setOnClickListener { salvarNovaSenha() }

        atualizarRequisitos()
    }

    private fun temNumeroOuEspecial(senha: String) =
        senha.any { it.isDigit() || (!it.isLetterOrDigit() && !it.isWhitespace()) }

    private fun senhaValida(senha: String) =
        senha.length >= 8 &&
                senha.any { it.isUpperCase() } &&
                temNumeroOuEspecial(senha)

    private fun atualizarRequisitos() {
        val atual = edtSenhaAtual.text.toString()
        val senha = edtNovaSenha.text.toString()
        val confirmar = edtConfirmar.text.toString()

        val okTamanho = senha.length >= 8
        val okMaiuscula = senha.any { it.isUpperCase() }
        val okNumero = temNumeroOuEspecial(senha)
        val okIgual = senha.isNotEmpty() && senha == confirmar

        marcar(reqTamanho, okTamanho, "Mínimo de 8 caracteres")
        marcar(reqMaiuscula, okMaiuscula, "Uma letra maiúscula")
        marcar(reqNumero, okNumero, "Um número ou caractere especial")
        marcar(reqIgual, okIgual, "Senhas iguais")

        btnSalvar.isEnabled =
            atual.isNotEmpty() && okTamanho && okMaiuscula && okNumero && okIgual
    }

    private fun marcar(tv: TextView, ok: Boolean, texto: String) {
        if (ok) {
            tv.text = "✔ $texto"
            tv.setTextColor(Color.parseColor("#166534"))
        } else {
            tv.text = "○ $texto"
            tv.setTextColor(Color.parseColor("#6B7280"))
        }
    }

    private fun salvarNovaSenha() {
        limparErros()
        esconderTeclado()

        val senhaAtual = edtSenhaAtual.text.toString()
        val novaSenha = edtNovaSenha.text.toString()
        val confirmar = edtConfirmar.text.toString()

        if (senhaAtual.isEmpty()) {
            tilSenhaAtual.error = "Informe a senha atual"
            mostrarMensagem("Digite sua senha atual.", Tipo.ERRO)
            return
        }

        if (!senhaValida(novaSenha)) {
            tilNovaSenha.error = "A senha não atende aos requisitos"
            mostrarMensagem("Verifique os requisitos da senha.", Tipo.ERRO)
            return
        }

        if (novaSenha != confirmar) {
            tilConfirmar.error = "As senhas não coincidem"
            mostrarMensagem("As senhas digitadas não são iguais.", Tipo.ERRO)
            return
        }

        if (novaSenha == senhaAtual) {
            tilNovaSenha.error = "Use uma senha diferente da atual"
            mostrarMensagem("A nova senha deve ser diferente da atual.", Tipo.ERRO)
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            mostrarMensagem("Usuário não identificado. Faça login novamente.", Tipo.ERRO)
            return
        }

        definirCarregando(true)

        // 1) Reautentica com a senha atual
        val credencial = EmailAuthProvider.getCredential(email, senhaAtual)
        user.reauthenticate(credencial).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                definirCarregando(false)
                if (task.exception is FirebaseNetworkException) {
                    mostrarMensagem("Erro de conexão. Verifique sua internet.", Tipo.ERRO)
                } else {
                    tilSenhaAtual.error = "Senha atual incorreta"
                    mostrarMensagem("A senha atual está incorreta.", Tipo.ERRO)
                }
                return@addOnCompleteListener
            }

            // 2) Atualiza a senha
            perfilService.atualizarSenha(novaSenha) { sucesso, erro ->
                definirCarregando(false)
                if (sucesso) {
                    mostrarMensagem("Senha atualizada com sucesso!", Tipo.SUCESSO)
                    btnSalvar.isEnabled = false
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
                } else {
                    when (erro) {
                        is FirebaseAuthWeakPasswordException -> {
                            tilNovaSenha.error = "Senha muito fraca"
                            mostrarMensagem("Escolha uma senha mais forte.", Tipo.ERRO)
                        }
                        is FirebaseNetworkException ->
                            mostrarMensagem("Erro de conexão. Verifique sua internet.", Tipo.ERRO)
                        else ->
                            mostrarMensagem(
                                "Erro ao atualizar senha: ${erro?.localizedMessage ?: "tente novamente."}",
                                Tipo.ERRO
                            )
                    }
                }
            }
        }
    }

    private fun definirCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        edtSenhaAtual.isEnabled = !carregando
        edtNovaSenha.isEnabled = !carregando
        edtConfirmar.isEnabled = !carregando
        if (carregando) {
            btnSalvar.isEnabled = false
        } else {
            atualizarRequisitos()
        }
    }

    private fun limparErros() {
        tilSenhaAtual.error = null
        tilNovaSenha.error = null
        tilConfirmar.error = null
        txtMensagem.visibility = View.GONE
    }

    private fun esconderTeclado() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun mostrarMensagem(texto: String, tipo: Tipo) {
        val (fundo, corTexto) = when (tipo) {
            Tipo.ERRO -> Pair("#FEE2E2", "#B91C1C")
            Tipo.AVISO -> Pair("#FEF9C3", "#A16207")
            Tipo.SUCESSO -> Pair("#DCFCE7", "#166534")
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

        scroll.post { scroll.smoothScrollTo(0, txtMensagem.top) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                SoundManager.dispararAudioCue(findViewById(android.R.id.home) ?: window.decorView)
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}