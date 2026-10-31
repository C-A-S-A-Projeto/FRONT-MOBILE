package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R

class HomeActivity : BaseDrawerActivity() {

    private lateinit var auth: FirebaseAuth

    // 1. Informa qual é o layout XML específico desta tela (activity_home)
    override fun getContentLayoutId(): Int {
        return R.layout.activity_home
    }

    // 2. Informa qual item do menu lateral deve ficar destacado (ex: nav_inicio)
    override fun getCurrentMenuItemId(): Int {
        return R.id.nav_inicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nota: O setContentView e o inflamento do layout base já são tratados automaticamente pela BaseDrawerActivity

        auth = FirebaseAuth.getInstance()

        val usuario = auth.currentUser
        if (usuario == null) {
            irParaInicio()
            return
        }

        val email = usuario.email ?: "-"
        val nome = usuario.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@")
        val primeiroNome = nome.trim().substringBefore(" ")

        findViewById<TextView>(R.id.txtOla).text = "Olá, $primeiroNome!"
        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtEmail).text = email

        // Ao clicar em sair, chama o modal de confirmação
        findViewById<MaterialButton>(R.id.btnSair).setOnClickListener {
            mostrarModalDesconectar()
        }

        findViewById<MaterialButton>(R.id.btnRedefinirSenha).setOnClickListener {
            startActivity(Intent(this, RedefinirSenhaActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnExcluirConta).setOnClickListener {
            startActivity(Intent(this, ExcluirContaActivity::class.java))
        }
    }

    private fun mostrarModalDesconectar() {
        AlertDialog.Builder(this)
            .setTitle("Desconectar")
            .setMessage("Tem certeza de que deseja sair da sua conta?")
            .setPositiveButton("Sim") { _, _ ->
                val intent = Intent(this, DesconectarContaActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun irParaInicio() {
        val intent = Intent(this, BemVindoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}