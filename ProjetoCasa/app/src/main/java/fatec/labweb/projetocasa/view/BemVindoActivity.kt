package fatec.labweb.projetocasa

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.view.CadastroActivity
import fatec.labweb.projetocasa.view.HomeActivity
import fatec.labweb.projetocasa.view.LoginActivity

class BemVindoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 1. Redireciona para a Home imediatamente se o usuário já estiver logado
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        // 2. Infla o layout somente se o usuário precisar ver a tela de boas-vindas
        // Nota: Certifique-se de que o arquivo em res/layout se chama 'activity_bemvindo.xml'
        setContentView(R.layout.activity_bemvindo)

        // 3. Configura os cliques dos botões usando findViewById
        // (Usado View genérico ou Button para evitar erros de cast se o elemento for um MaterialButton)
        findViewById<View>(R.id.btnLogin)?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<View>(R.id.btnCadastrar)?.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }
    }
}