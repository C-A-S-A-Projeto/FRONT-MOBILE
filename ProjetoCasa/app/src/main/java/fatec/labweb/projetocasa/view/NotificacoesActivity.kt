package fatec.labweb.projetocasa.view

import android.os.Bundle
import com.google.android.material.button.MaterialButton
import fatec.labweb.projetocasa.R

class NotificacoesActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int {
        return R.layout.activity_notifications
    }

    override fun getCurrentMenuItemId(): Int {
        return -1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<MaterialButton>(R.id.btnFecharNotificacoes)?.setOnClickListener {
            finish()
        }
    }
}
