package fatec.labweb.projetocasa.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import fatec.labweb.projetocasa.BemVindoActivity
import fatec.labweb.projetocasa.R

class MainActivity : AppCompatActivity() {

    private lateinit var cardLogo: MaterialCardView
    private lateinit var viewPulso: View
    private lateinit var txtNomeApp: TextView
    private lateinit var txtSlogan: TextView
    private lateinit var txtStatus: TextView
    private lateinit var barra: LinearProgressIndicator

    private var animacaoProgresso: ValueAnimator? = null
    private var animacaoPulso: ObjectAnimator? = null
    private var cancelado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardLogo = findViewById(R.id.cardLogo)
        viewPulso = findViewById(R.id.viewPulso)
        txtNomeApp = findViewById(R.id.txtNomeApp)
        txtSlogan = findViewById(R.id.txtSlogan)
        txtStatus = findViewById(R.id.txtStatus)
        barra = findViewById(R.id.barraProgresso)

        animarEntrada()
    }

    // 1) Logo entra com zoom, 2) textos sobem, 3) barra aparece e enche
    private fun animarEntrada() {
        cardLogo.scaleX = 0.5f
        cardLogo.scaleY = 0.5f
        txtNomeApp.translationY = 40f
        txtSlogan.translationY = 40f

        val logo = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cardLogo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cardLogo, View.SCALE_X, 0.5f, 1f),
                ObjectAnimator.ofFloat(cardLogo, View.SCALE_Y, 0.5f, 1f)
            )
            duration = 700
            interpolator = OvershootInterpolator(1.2f)
        }

        val textos = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(txtNomeApp, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(txtNomeApp, View.TRANSLATION_Y, 40f, 0f),
                ObjectAnimator.ofFloat(txtSlogan, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(txtSlogan, View.TRANSLATION_Y, 40f, 0f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
        }

        val barraAparece = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(barra, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(txtStatus, View.ALPHA, 0f, 1f)
            )
            duration = 300
        }

        AnimatorSet().apply {
            playSequentially(logo, textos, barraAparece)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelado) return
                    iniciarPulso()
                    iniciarProgresso()
                }
            })
            start()
        }
    }

    // Círculo azul claro que cresce e some, repetindo
    private fun iniciarPulso() {
        viewPulso.scaleX = 0.8f
        viewPulso.scaleY = 0.8f

        animacaoPulso = ObjectAnimator.ofPropertyValuesHolder(
            viewPulso,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.2f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.2f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f)
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun iniciarProgresso() {
        animacaoProgresso = ValueAnimator.ofInt(0, 100).apply {
            duration = 2200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val valor = it.animatedValue as Int
                barra.progress = valor
                txtStatus.text = when {
                    valor < 35 -> "Iniciando..."
                    valor < 70 -> "Carregando seus dados..."
                    valor < 100 -> "Quase pronto..."
                    else -> "Tudo certo!"
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelado) irParaBemVindo()
                }
            })
            start()
        }
    }

    // Loading termina e encaminha para a tela de boas-vindas
    private fun irParaBemVindo() {
        startActivity(Intent(this, BemVindoActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // Evita abrir a próxima tela se o app for fechado durante o loading
    override fun onDestroy() {
        super.onDestroy()
        cancelado = true
        animacaoProgresso?.cancel()
        animacaoPulso?.cancel()
    }
}