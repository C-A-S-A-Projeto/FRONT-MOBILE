package fatec.labweb.projetocasa.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.Esp32ImageProcessor
import fatec.labweb.projetocasa.service.Esp32TriggerService
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CategoriaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val imageProcessor: Esp32ImageProcessor = Esp32ImageProcessor(),
    private val triggerService: Esp32TriggerService = Esp32TriggerService()
) {
    companion object {
        const val MAX_CATEGORIAS = 3
        const val MAX_BOTOES_POR_CATEGORIA = 5

        fun getCategoriasPadrao(): List<Categoria> {
            return listOf(
                Categoria(
                    id = "cat_emocoes",
                    nome = "Emoções",
                    slug = "emocoes",
                    cor = "#4A90E2",
                    ordem = 0,
                    botoes = mutableListOf(
                        CategoriaItem("b1", "Feliz", "Feliz", null, null, null, null, 0, GeneroAudio.FEMALE),
                        CategoriaItem("b2", "Triste", "Triste", null, null, null, null, 1, GeneroAudio.FEMALE),
                        CategoriaItem("b3", "Bravo", "Bravo", null, null, null, null, 2, GeneroAudio.MALE),
                        CategoriaItem("b4", "Assustado", "Assustado", null, null, null, null, 3, GeneroAudio.MALE),
                        CategoriaItem("b5", "Cansado", "Cansado", null, null, null, null, 4, GeneroAudio.FEMALE)
                    )
                ),
                Categoria(
                    id = "cat_desejos",
                    nome = "Desejos",
                    slug = "desejos",
                    cor = "#50E3C2",
                    ordem = 1,
                    botoes = mutableListOf(
                        CategoriaItem("b6", "Água", "Água", null, null, null, null, 0, GeneroAudio.FEMALE),
                        CategoriaItem("b7", "Comida", "Comida", null, null, null, null, 1, GeneroAudio.FEMALE),
                        CategoriaItem("b8", "Banheiro", "Banheiro", null, null, null, null, 2, GeneroAudio.FEMALE),
                        CategoriaItem("b9", "Dormir", "Dormir", null, null, null, null, 3, GeneroAudio.MALE),
                        CategoriaItem("b10", "Brincar", "Brincar", null, null, null, null, 4, GeneroAudio.FEMALE)
                    )
                ),
                Categoria(
                    id = "cat_casa",
                    nome = "Casa",
                    slug = "casa",
                    cor = "#F5A623",
                    ordem = 2,
                    botoes = mutableListOf(
                        CategoriaItem("b11", "Quarto", "Quarto", null, null, null, null, 0, GeneroAudio.FEMALE),
                        CategoriaItem("b12", "Sala", "Sala", null, null, null, null, 1, GeneroAudio.FEMALE),
                        CategoriaItem("b13", "Cozinha", "Cozinha", null, null, null, null, 2, GeneroAudio.FEMALE)
                    )
                )
            )
        }
    }

    private var cacheCategorias: MutableList<Categoria> = mutableListOf()

    private fun colecaoCategorias(userId: String) =
        firestore.collection("usuarios").document(userId).collection("categorias")

    suspend fun carregarOuInicializarCategorias(userId: String): List<Categoria> {
        return try {
            val snapshot = colecaoCategorias(userId).get().await()

            if (!snapshot.isEmpty) {
                val lista = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Categoria::class.java)
                }.sortedBy { it.ordem }.toMutableList()

                cacheCategorias = lista
                lista
            } else {
                // Inicializa categorias padrão compatíveis com a CASA-API
                val padrao = getCategoriasPadrao()
                for (cat in padrao) {
                    colecaoCategorias(userId).document(cat.id).set(cat).await()
                }
                cacheCategorias = padrao.toMutableList()
                triggerService.notificarAtualizacao()
                padrao
            }
        } catch (e: Exception) {
            if (cacheCategorias.isEmpty()) {
                cacheCategorias = getCategoriasPadrao().toMutableList()
            }
            cacheCategorias
        }
    }

    fun getCategorias(): List<Categoria> = cacheCategorias

    suspend fun salvarCategoria(userId: String, categoria: Categoria): Result<Unit> {
        return try {
            if (cacheCategorias.size >= MAX_CATEGORIAS && cacheCategorias.none { it.id == categoria.id }) {
                return Result.failure(IllegalStateException("Limite de $MAX_CATEGORIAS categorias atingido."))
            }
            val catId = categoria.id.ifBlank { UUID.randomUUID().toString().replace("-", "").substring(0, 12) }
            val catFinal = categoria.copy(id = catId)

            colecaoCategorias(userId).document(catId).set(catFinal).await()

            val idx = cacheCategorias.indexOfFirst { it.id == catId }
            if (idx >= 0) cacheCategorias[idx] = catFinal else cacheCategorias.add(catFinal)

            triggerService.notificarAtualizacao()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adicionarItemNaCategoriaComMidia(
        context: Context,
        userId: String,
        categoriaId: String,
        novoItem: CategoriaItem,
        imagemUriLocal: Uri?,
        audioUriLocal: Uri?
    ): Result<CategoriaItem> {
        val cat = cacheCategorias.find { it.id == categoriaId }
            ?: return Result.failure(IllegalArgumentException("Categoria $categoriaId não encontrada."))

        if (cat.botoes.size >= MAX_BOTOES_POR_CATEGORIA) {
            return Result.failure(IllegalStateException("Limite de $MAX_BOTOES_POR_CATEGORIA botões atingido."))
        }

        var imagemIdFinal = novoItem.imagem_id
        var imagemArquivoFinal = novoItem.imagem

        // Processa imagem se fornecida
        if (imagemUriLocal != null) {
            val resUpload = imageProcessor.processarESalvarImagem(
                context = context,
                userId = userId,
                imagemUri = imagemUriLocal,
                nomeOriginal = novoItem.rotulo.ifBlank { "imagem" } + ".jpg"
            )
            if (resUpload.isSuccess) {
                val midia = resUpload.getOrThrow()
                imagemIdFinal = midia.midiaId
                imagemArquivoFinal = midia.nomeArquivo
            } else {
                return Result.failure(resUpload.exceptionOrNull() ?: Exception("Erro ao processar imagem."))
            }
        }

        val botaoId = novoItem.id.ifBlank { UUID.randomUUID().toString().replace("-", "").substring(0, 12) }
        val itemCompleto = novoItem.copy(
            id = botaoId,
            rotulo = novoItem.rotulo.ifBlank { "Botão" },
            significado = novoItem.significado.ifBlank { novoItem.rotulo.ifBlank { "Botão" } },
            imagem_id = imagemIdFinal,
            imagem = imagemArquivoFinal,
            ordem = cat.botoes.size
        )

        cat.botoes.add(itemCompleto)

        return try {
            colecaoCategorias(userId).document(categoriaId).set(cat).await()
            // Notifica Realtime Database para atualizar o hardware ESP32
            triggerService.notificarAtualizacao()
            Result.success(itemCompleto)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removerBotao(
        userId: String,
        categoriaId: String,
        botaoId: String
    ): Result<Unit> {
        val cat = cacheCategorias.find { it.id == categoriaId }
            ?: return Result.failure(IllegalArgumentException("Categoria não encontrada."))

        cat.botoes.removeAll { it.id == botaoId }
        // Reordena
        cat.botoes.forEachIndexed { index, b -> b.ordem = index }

        return try {
            colecaoCategorias(userId).document(categoriaId).set(cat).await()
            triggerService.notificarAtualizacao()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
