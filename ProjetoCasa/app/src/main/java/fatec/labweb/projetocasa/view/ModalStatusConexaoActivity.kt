package fatec.labweb.projetocasa.view

import android.os.Bundle
import fatec.labweb.projetocasa.R

class ModalStatusConexaoActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int {
        return R.layout.activity_modal_status_conecxao
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuInicio
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
