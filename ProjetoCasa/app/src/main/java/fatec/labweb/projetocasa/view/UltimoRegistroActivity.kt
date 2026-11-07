package fatec.labweb.projetocasa.view

import android.os.Bundle
import fatec.labweb.projetocasa.R

class UltimoRegistroActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int {
        return R.layout.activity_modal_ultimo_registro
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuHistorico
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
