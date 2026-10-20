package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.api.CasaApiClient

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CasaApiClient.init(applicationContext)

        CasaApiClient.verificarSaude { online, erro ->
            if (online) {
                Log.d("CASA_API", "CASA-API conectada em ${CasaApiClient.baseUrl}")
            } else {
                Log.w("CASA_API", "CASA-API indisponível: $erro")
            }
        }

        findViewById<MaterialButton>(R.id.btnLogin)?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnCadastrar)?.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("MODO_CADASTRO", true)
            }
            startActivity(intent)
        }
    }
}
