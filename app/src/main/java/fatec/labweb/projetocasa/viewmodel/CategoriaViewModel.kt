package fatec.labweb.projetocasa.viewmodel

import android.content.Context
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

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun carregarDados(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val lista = repository.carregarOuInicializarCategorias(userId)
            _categorias.value = lista
            _isLoading.value = false
        }
    }

    fun adicionarBotaoComMidia(
        context: Context,
        categoriaId: String,
        novoItem: CategoriaItem,
        imagemUri: Uri?,
        audioUri: Uri?,
        userId: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.adicionarItemNaCategoriaComMidia(
                context = context,
                userId = userId,
                categoriaId = categoriaId,
                novoItem = novoItem,
                imagemUriLocal = imagemUri,
                audioUriLocal = audioUri
            )
            if (res.isSuccess) {
                _categorias.value = repository.getCategorias()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message
            }
            _isLoading.value = false
        }
    }

    fun removerBotao(userId: String, categoriaId: String, botaoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.removerBotao(userId, categoriaId, botaoId)
            if (res.isSuccess) {
                _categorias.value = repository.getCategorias()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message
            }
            _isLoading.value = false
        }
    }
}
