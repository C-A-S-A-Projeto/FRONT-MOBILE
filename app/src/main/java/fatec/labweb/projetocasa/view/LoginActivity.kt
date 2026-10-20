package fatec.labweb.projetocasa.view

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.api.CasaApiClient
import fatec.labweb.projetocasa.dao.UsuarioDao
import fatec.labweb.projetocasa.model.Usuario

class LoginActivity : AppCompatActivity() {

    private val usuarioDao = UsuarioDao()
    private var modoCadastro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        CasaApiClient.init(applicationContext)
        modoCadastro = intent.getBooleanExtra("MODO_CADASTRO", false)

        val btnVoltar = findViewById<ImageButton>(R.id.btnVoltar)
        val edtEmail = findViewById<TextInputEditText>(R.id.edtEmailLogin)
        val edtSenha = findViewById<TextInputEditText>(R.id.edtSenhaLogin)
        val txtEsqueciSenha = findViewById<TextView>(R.id.txtEsqueciSenha)
        val btnEntrar = findViewById<MaterialButton>(R.id.btnEntrar)
        val txtIrParaCadastro = findViewById<TextView>(R.id.txtIrParaCadastro)

        btnVoltar.setOnClickListener { finish() }

        txtEsqueciSenha.setOnClickListener {
            val email = edtEmail.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                Toast.makeText(this, "Informe seu e-mail no campo acima.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            usuarioDao.enviarRecuperacaoSenha(email) { sucesso, erro ->
                if (sucesso) {
                    Toast.makeText(this, "Instruções enviadas para $email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, erro ?: "Erro ao enviar recuperação", Toast.LENGTH_LONG).show()
                }
            }
        }

        val iniciarFluxoCadastro = {
            val email = edtEmail.text?.toString()?.trim().orEmpty()
            val senha = edtSenha.text?.toString().orEmpty()
            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha e-mail e senha para cadastrar.", Toast.LENGTH_SHORT).show()
            } else {
                val nome = email.substringBefore("@").ifBlank { "Usuário" }
                btnEntrar.isEnabled = false
                usuarioDao.iniciarCadastroApi(nome, email, senha) { ok, erroApi ->
                    btnEntrar.isEnabled = true
                    if (ok) {
                        mostrarDialogCodigoConfirmacao(email, senha)
                    } else if (erroApi != null && erroApi.contains("Sem conexão")) {
                        // Fallback para cadastro direto no Firebase caso a API local esteja desligada
                        btnEntrar.isEnabled = false
                        usuarioDao.cadastrar(Usuario(nome = nome, email = email), senha) { okDireto, errDireto ->
                            btnEntrar.isEnabled = true
                            if (okDireto) {
                                Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this, errDireto ?: "Falha ao cadastrar", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, erroApi ?: "Falha ao iniciar cadastro", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        txtIrParaCadastro.setOnClickListener { iniciarFluxoCadastro() }

        btnEntrar.setOnClickListener {
            val email = edtEmail.text?.toString()?.trim().orEmpty()
            val senha = edtSenha.text?.toString().orEmpty()
            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (modoCadastro) {
                iniciarFluxoCadastro()
            } else {
                btnEntrar.isEnabled = false
                usuarioDao.logar(email, senha) { sucesso, erro ->
                    btnEntrar.isEnabled = true
                    if (sucesso) {
                        Toast.makeText(this, "Login realizado com sucesso!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, erro ?: "Falha no login", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun mostrarDialogCodigoConfirmacao(email: String, senha: String) {
        val inputCodigo = EditText(this).apply {
            hint = "Código de 6 dígitos"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Confirme seu e-mail")
            .setMessage("Digite o código de 6 dígitos enviado para $email (ou exibido no terminal da CASA-API):")
            .setView(inputCodigo)
            .setCancelable(false)
            .setPositiveButton("Confirmar") { _, _ ->
                val codigo = inputCodigo.text?.toString()?.trim().orEmpty()
                if (codigo.length != 6) {
                    Toast.makeText(this, "O código deve ter 6 dígitos.", Toast.LENGTH_SHORT).show()
                    mostrarDialogCodigoConfirmacao(email, senha)
                    return@setPositiveButton
                }
                usuarioDao.confirmarCadastroApi(email, codigo, senha) { ok, erro ->
                    if (ok) {
                        Toast.makeText(this, "Conta confirmada e autenticada!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, erro ?: "Código inválido", Toast.LENGTH_LONG).show()
                        mostrarDialogCodigoConfirmacao(email, senha)
                    }
                }
            }
            .setNeutralButton("Reenviar") { _, _ ->
                usuarioDao.reenviarCodigoApi(email) { ok, erro ->
                    Toast.makeText(
                        this,
                        if (ok) "Novo código enviado!" else (erro ?: "Aguarde para reenviar."),
                        Toast.LENGTH_SHORT
                    ).show()
                    mostrarDialogCodigoConfirmacao(email, senha)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
