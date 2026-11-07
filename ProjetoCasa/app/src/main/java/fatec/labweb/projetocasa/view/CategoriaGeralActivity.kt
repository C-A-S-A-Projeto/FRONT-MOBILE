package fatec.labweb.projetocasa.view

import android.os.Bundle
import fatec.labweb.projetocasa.R

class CategoriaGeralActivity : BaseDrawerActivity() {

    override fun getContentLayoutId(): Int {
        return R.layout.activity_categoria_botao
    }

    override fun getCurrentMenuItemId(): Int {
        return R.id.menuCategorias
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
