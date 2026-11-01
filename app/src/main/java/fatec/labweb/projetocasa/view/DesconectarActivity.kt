package fatec.labweb.projetocasa.view

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R

class DesconectarContaActivity : AppCompatActivity() {

    companion object {
        private const val TEMPO_TOTAL_MS = 5_000L
    }

    private var timer: CountDownTimer? = null
    private var jaNavegou = false

    private lateinit var txtContador: TextView
    private lateinit var txtSegundos: TextView
    private lateinit var barraTempo: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desconectarconta)

        txtContador = findViewById(R.id.txtContador)
        txtSegundos = findViewById(R.id.txtSegundos)
        barraTempo = findViewById(R.id.barraTempo)

        // Garante que o usuário está desconectado
        FirebaseAuth.getInstance().signOut()

        findViewById<MaterialButton>(R.id.btnIrAgora).setOnClickListener { irParaBemVindo() }

        // Nesta tela o botão voltar do sistema não deve retornar à Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                irParaBemVindo()
            }
        })

        iniciarContagem()
    }

    private fun iniciarContagem() {
        timer = object : CountDownTimer(TEMPO_TOTAL_MS, 50) {
            override fun onTick(restanteMs: Long) {
                // Arredonda para cima: mostra 5, 4, 3, 2, 1
                val segundos = ((restanteMs + 999) / 1000).toInt()
                txtContador.text = segundos.toString()
                txtSegundos.text = if (segundos == 1) "segundo" else "segundos"
                barraTempo.progress = restanteMs.toInt()
            }

            override fun onFinish() {
                barraTempo.progress = 0
                irParaBemVindo()
            }
        }.start()
    }

    private fun irParaBemVindo() {
        if (jaNavegou) return
        jaNavegou = true
        timer?.cancel()

        val intent = Intent(this, BemVindoActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}