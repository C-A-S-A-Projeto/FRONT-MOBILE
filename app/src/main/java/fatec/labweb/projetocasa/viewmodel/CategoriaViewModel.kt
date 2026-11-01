package fatec.labweb.projetocasa.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.repository.CategoriaRepository
import kotlinx.coroutines.launch

class CategoriaViewModel : ViewModel() {

    private val repository = CategoriaRepository()

    private val _categorias = MutableLiveData<List<Categoria>>()
    val categorias: LiveData<List<Categoria>> get() = _categorias

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    fun carregarDados(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // Como é uma função suspend, o coroutine lida com ela aqui dentro
            val lista = repository.carregarOuInicializarCategorias(userId)
            _categorias.value = lista
            _isLoading.value = false
        }
    }

    fun adicionarBotaoComMidia(
        categoriaId: String,
        novoItem: CategoriaItem,
        imagemUri: Uri?,
        audioUri: Uri?,
        userId: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            // Certifica-te de que chamas as funções corretas do repositório
            repository.adicionarItemNaCategoriaComUpload(categoriaId, novoItem, imagemUri, audioUri)
            repository.salvarAlteracoesUsuario(userId, repository.getCategorias())

            _categorias.value = repository.getCategorias()
            _isLoading.value = false
        }
    }
}