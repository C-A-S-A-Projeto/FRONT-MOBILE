package fatec.labweb.projetocasa.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.service.CategoriaService
import kotlinx.coroutines.launch

class CategoriaViewModel : ViewModel() {

    private val service = CategoriaService()

    private val _categorias = MutableLiveData<List<Categoria>>()
    val categorias: LiveData<List<Categoria>> get() = _categorias

    private val _botoes = MutableLiveData<List<CategoriaItem>>()
    val botoes: LiveData<List<CategoriaItem>> get() = _botoes

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private val _sucessoOperacao = MutableLiveData<String?>()
    val sucessoOperacao: LiveData<String?> get() = _sucessoOperacao

    // ================= CATEGORIAS =================

    fun carregarDados(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val lista = service.listarCategorias(userId)
                _categorias.value = lista
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar categorias: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun salvarCategoria(userId: String, categoria: Categoria) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = service.salvarCategoria(userId, categoria)
            if (res.isSuccess) {
                _sucessoOperacao.value = "Categoria salva com sucesso!"
                val listaAtualizada = service.listarCategorias(userId)
                _categorias.value = listaAtualizada
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Erro ao salvar categoria"
            }
            _isLoading.value = false
        }
    }

    fun excluirCategoria(userId: String, categoriaId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = service.excluirCategoria(userId, categoriaId)
            if (res.isSuccess) {
                _sucessoOperacao.value = "Categoria excluída com sucesso!"
                val listaAtualizada = service.listarCategorias(userId)
                _categorias.value = listaAtualizada
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Erro ao excluir categoria"
            }
            _isLoading.value = false
        }
    }

    // ================= BOTÕES / ITENS =================

    fun carregarBotoes(userId: String, categoriaId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val lista = service.listarBotoes(userId, categoriaId)
                _botoes.value = lista
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar botões: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun salvarBotaoComMidia(
        context: Context?,
        categoriaId: String,
        item: CategoriaItem,
        imagemUri: Uri?,
        audioUri: Uri?,
        userId: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = service.salvarBotao(
                userId = userId,
                categoriaId = categoriaId,
                item = item,
                context = context,
                imagemUriLocal = imagemUri,
                audioUriLocal = audioUri
            )
            if (res.isSuccess) {
                _sucessoOperacao.value = "Botão salvo com sucesso!"
                val listaAtualizada = service.listarBotoes(userId, categoriaId)
                _botoes.value = listaAtualizada
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Erro ao salvar botão"
            }
            _isLoading.value = false
        }
    }

    fun removerBotao(userId: String, categoriaId: String, botaoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = service.excluirBotao(userId, categoriaId, botaoId)
            if (res.isSuccess) {
                _sucessoOperacao.value = "Botão excluído com sucesso!"
                val listaAtualizada = service.listarBotoes(userId, categoriaId)
                _botoes.value = listaAtualizada
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Erro ao excluir botão"
            }
            _isLoading.value = false
        }
    }

    // Manter assinatura legada
    fun adicionarBotaoComMidia(
        context: Context,
        categoriaId: String,
        novoItem: CategoriaItem,
        imagemUri: Uri?,
        audioUri: Uri?,
        userId: String
    ) {
        salvarBotaoComMidia(context, categoriaId, novoItem, imagemUri, audioUri, userId)
    }
}
