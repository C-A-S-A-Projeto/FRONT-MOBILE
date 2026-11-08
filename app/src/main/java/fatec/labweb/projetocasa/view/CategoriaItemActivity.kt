package fatec.labweb.projetocasa.view

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.google.firebase.auth.FirebaseAuth
import fatec.labweb.projetocasa.R
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel

class CategoriaItemActivity : BaseDrawerActivity() {

    private val viewModel: CategoriaViewModel by viewModels()

    override fun getContentLayoutId(): Int = R.layout.activity_categoria_item
    override fun getCurrentMenuItemId(): Int = R.id.menuCategorias

    private var uriImagemSelecionada: Uri? = null
    private var uriAudioGravado: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonimo"
        viewModel.carregarDados(uid)

        viewModel.categorias.observe(this) { categorias ->
            // Categorias carregadas
        }

        viewModel.errorMessage.observe(this) { erro ->
            if (!erro.isNullOrBlank()) {
                Toast.makeText(this, erro, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun salvarBotao(categoriaId: String, rotulo: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val novoItem = CategoriaItem(
            rotulo = rotulo,
            significado = rotulo,
            defaultGender = GeneroAudio.FEMALE
        )
        viewModel.adicionarBotaoComMidia(
            context = this,
            categoriaId = categoriaId,
            novoItem = novoItem,
            imagemUri = uriImagemSelecionada,
            audioUri = uriAudioGravado,
            userId = uid
        )
    }
}
