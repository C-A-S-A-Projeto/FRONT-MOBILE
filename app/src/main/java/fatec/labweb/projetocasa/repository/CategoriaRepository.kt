package fatec.labweb.projetocasa.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CategoriaRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storageRef = FirebaseStorage.getInstance().reference
    private val categoriasCollection = firestore.collection("usuarios_categorias")

    companion object {
        private val listaCategorias = mutableListOf(
            Categoria(
                id = "cat_emocoes",
                name = "Emoções",
                imageUri = null,
                audioPath = null,
                defaultGender = GeneroAudio.FEMALE,
                buttons = mutableListOf(
                    CategoriaItem("b1", "Feliz", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b2", "Triste", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b3", "Bravo", null, null, GeneroAudio.MALE),
                    CategoriaItem("b4", "Assustado", null, null, GeneroAudio.MALE),
                    CategoriaItem("b5", "Cansado", null, null, GeneroAudio.FEMALE)
                )
            ),
            Categoria(
                id = "cat_desejos",
                name = "Desejos",
                imageUri = null,
                audioPath = null,
                defaultGender = GeneroAudio.FEMALE,
                buttons = mutableListOf(
                    CategoriaItem("b6", "Água", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b7", "Comida", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b8", "Banheiro", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b9", "Dormir", null, null, GeneroAudio.MALE),
                    CategoriaItem("b10", "Brincar", null, null, GeneroAudio.FEMALE)
                )
            ),
            Categoria(
                id = "cat_casa",
                name = "Casa",
                imageUri = null,
                audioPath = null,
                defaultGender = GeneroAudio.FEMALE,
                buttons = mutableListOf(
                    CategoriaItem("b11", "Quarto", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b12", "Sala", null, null, GeneroAudio.FEMALE),
                    CategoriaItem("b13", "Cozinha", null, null, GeneroAudio.FEMALE)
                )
            )
        )
    }

    // --- FUNÇÕES EM FALTA QUE O VIEWMODEL ESTÁ A CHAMAR ---

    suspend fun carregarOuInicializarCategorias(userId: String): List<Categoria> {
        return try {
            val docRef = categoriasCollection.document(userId)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                listaCategorias
            } else {
                val dadosIniciais = mapOf("categorias" to listaCategorias)
                docRef.set(dadosIniciais).await()
                listaCategorias
            }
        } catch (e: Exception) {
            listaCategorias
        }
    }

    suspend fun salvarAlteracoesUsuario(userId: String, listaAtualizada: List<Categoria>) {
        try {
            val dados = mapOf("categorias" to listaAtualizada)
            categoriasCollection.document(userId).set(dados).await()
        } catch (e: Exception) {
            // Tratar erro se necessário
        }
    }

    suspend fun uploadArquivoParaStorage(fileUri: Uri, pasta: String): Result<String> {
        return try {
            val fileExtension = fileUri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
            val fileName = "${UUID.randomUUID()}.$fileExtension"
            val ref = storageRef.child("projeto_casa/$pasta/$fileName")
            ref.putFile(fileUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCategorias(): List<Categoria> {
        return listaCategorias
    }

    suspend fun adicionarItemNaCategoriaComUpload(
        categoriaId: String,
        novoItem: CategoriaItem,
        imagemUriLocal: Uri?,
        audioUriLocal: Uri?
    ): Boolean {
        val categoria = listaCategorias.find { it.id == categoriaId } ?: return false
        if (categoria.buttons.size >= 5) return false

        var imageUrlFinal = novoItem.imageUri
        if (imagemUriLocal != null) {
            val resImg = uploadArquivoParaStorage(imagemUriLocal, "itens/imagens")
            resImg.onSuccess { imageUrlFinal = it }
        }

        var audioUrlFinal = novoItem.audioPath
        if (audioUriLocal != null) {
            val resAudio = uploadArquivoParaStorage(audioUriLocal, "itens/audios")
            resAudio.onSuccess { audioUrlFinal = it }
        }

        val itemComUrls = novoItem.copy(
            imageUri = imageUrlFinal,
            audioPath = audioUrlFinal
        )

        categoria.buttons.add(itemComUrls)
        return true
    }
}