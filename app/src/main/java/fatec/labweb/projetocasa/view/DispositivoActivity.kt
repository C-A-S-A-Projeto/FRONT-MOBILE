package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * DispositivoActivity: Redireciona para CategoriaGeralActivity.
 * No ecossistema Web C.A.S.A., a rota '/dispositivo' é a tela de configuração da prancha
 * (categorias e botões do hardware), enquanto o gerenciamento/pareamento do ESP32 fica na Dashboard (Início).
 */
class DispositivoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, CategoriaGeralActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
