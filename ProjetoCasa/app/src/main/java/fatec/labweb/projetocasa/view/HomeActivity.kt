package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R

class HomeActivity : BaseDrawerActivity() {

    private lateinit var auth: FirebaseAuth

    override fun getContentLayoutId(): Int {
        return R.layout.activity_home
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.nav_inicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        findViewById<TextView>(R.id.txtOla)?.text = "Olá, $primeiroNome!"

        configurarBotoes()
    }

    private fun configurarBotoes() {
        findViewById<MaterialButton>(R.id.btnGerenciarDispositivos)?.setOnClickListener {
            startActivity(Intent(this, GerenciarDispositivosActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnMaisDetalhes)?.setOnClickListener {
            startActivity(Intent(this, ModalStatusConexaoActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnVerHistorico)?.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnAlterarBotaoSugerido)?.setOnClickListener {
            startActivity(Intent(this, CategoriaGeralActivity::class.java))
        }
    }

    private fun irParaInicio() {
        val intent = Intent(this, BemVindoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
