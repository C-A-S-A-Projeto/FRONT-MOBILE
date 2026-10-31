package fatec.labweb.projetocasa.view

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.service.PerfilService

class ExcluirContaActivity : AppCompatActivity() {

    private lateinit var perfilService: PerfilService
    private lateinit var scroll: ScrollView
    private lateinit var tilSenha: TextInputLayout
    private lateinit var edtSenha: TextInputEditText
    private lateinit var chkEntendo: CheckBox
    private lateinit var txtMensagem: TextView
    private lateinit var progresso: LinearProgressIndicator
    private lateinit var btnConfirmarExclusao: MaterialButton

    private enum class Tipo { ERRO, AVISO, SUCESSO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excluirconta)

        perfilService = PerfilService()

        if (perfilService.obterUsuarioAtual() == null) {
            irParaBemVindo()
            return
        }

        scroll = findViewById(R.id.scroll)
        tilSenha = findViewById(R.id.tilSenha)
        edtSenha = findViewById(R.id.edtSenha)
        chkEntendo = findViewById(R.id.chkEntendo)
        txtMensagem = findViewById(R.id.txtMensagem)
        progresso = findViewById(R.id.progresso)
        btnConfirmarExclusao = findViewById(R.id.btnConfirmarExclusao)

        findViewById<ImageButton>(R.id.btnVoltarExcluir).setOnClickListener { finish() }

        edtSenha.doAfterTextChanged {
            tilSenha.error = null
            atualizarBotao()
        }

        chkEntendo.setOnCheckedChangeListener { _, _ ->
            atualizarBotao()
        }

        btnConfirmarExclusao.setOnClickListener {
            confirmarExclusaoDialog()
        }
    }

    private fun atualizarBotao() {
        val senhaPreenchida = !edtSenha.text.isNullOrBlank()
        btnConfirmarExclusao.isEnabled = senhaPreenchida && chkEntendo.isChecked
    }

    private fun confirmarExclusaoDialog() {
        val senha = edtSenha.text.toString()
        if (senha.isEmpty()) {
            tilSenha.error = "Digite sua senha"
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar exclusão")
            .setMessage("Tem certeza de que deseja excluir permanentemente sua conta?")
            .setPositiveButton("Excluir") { _, _ ->
                executarExclusao(senha)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executarExclusao(senha: String) {
        limparErros()
        esconderTeclado()
        definirCarregando(true)

        perfilService.reautenticarEExcluirConta(senha) { sucesso, erro ->
            if (sucesso) {
                mostrarMensagem("Sua conta foi excluída com sucesso.", Tipo.SUCESSO)
                Handler(Looper.getMainLooper()).postDelayed({
                    irParaBemVindo()
                }, 1500)
            } else {
                definirCarregando(false)
                when (erro) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        tilSenha.error = "Senha incorreta"
                        mostrarMensagem("A senha informada está incorreta.", Tipo.ERRO)
                    }
                    is FirebaseNetworkException ->
                        mostrarMensagem("Erro de conexão. Verifique sua internet.", Tipo.ERRO)
                    else ->
                        mostrarMensagem(
                            "Erro ao excluir conta: ${erro?.localizedMessage ?: "tente novamente."}",
                            Tipo.ERRO
                        )
                }
            }
        }
    }

    private fun irParaBemVindo() {
        val intent = Intent(this, BemVindoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun definirCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        edtSenha.isEnabled = !carregando
        chkEntendo.isEnabled = !carregando
        if (carregando) {
            btnConfirmarExclusao.isEnabled = false
        } else {
            atualizarBotao()
        }
    }

    private fun limparErros() {
        tilSenha.error = null
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
}
