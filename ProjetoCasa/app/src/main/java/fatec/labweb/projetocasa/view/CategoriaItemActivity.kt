package fatec.labweb.projetocasa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.viewmodel.CategoriaViewModel

class CategoriaItemActivity : AppCompatActivity() {

    private val viewModel: CategoriaViewModel by viewModels()
    private val userIdUsuarioAtual = "usuario_teste_123"

    private var uriImagemSelecionada: Uri? = null
    private var uriAudioGravado: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categoria_item)

        // Carrega os dados iniciais
        viewModel.carregarDados(userIdUsuarioAtual)
    }

    // Exemplo de função para selecionar imagem (pode ser chamada num botão)
    private fun selecionarOuTirarFoto() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        // startActivityForResult ou ActivityResultLauncher entraria aqui
    }

    // Exemplo de função para guardar as alterações do item
    private fun guardarAlteracoesDoItem(categoriaId: String, itemId: String, novoTitulo: String) {
        val itemAtualizado = CategoriaItem(
            id = itemId,
            title = novoTitulo,
            imageUri = null,
            audioPath = null,
            defaultGender = GeneroAudio.FEMALE
        )

        // Se quiseres chamar o método de atualizar no ViewModel (podes criá-lo se necessário)
        Toast.makeText(this, "A guardar alterações...", Toast.LENGTH_SHORT).show()
    }
}