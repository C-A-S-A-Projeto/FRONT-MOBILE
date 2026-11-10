package fatec.labweb.projetocasa.service

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CategoriaService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val imageProcessor: Esp32ImageProcessor = Esp32ImageProcessor(),
    private val triggerService: Esp32TriggerService = Esp32TriggerService()
) {

    companion object {
        const val COLECAO_CATEGORIAS = "categoria"
        const val COLECAO_BOTOES = "categoria_botao"
        const val MAX_CATEGORIAS = 10
        const val MAX_BOTOES_POR_CATEGORIA = 5
    }

    private fun colecaoCategoriasUser(userId: String) =
        firestore.collection("usuarios").document(userId).collection("categorias")

    // ==========================================
    // OPERAÇÕES DE CATEGORIA (CRUD)
    // ==========================================

    suspend fun listarCategorias(userId: String): List<Categoria> {
        return try {
            // Tenta buscar da coleção 'categoria' vinculada ao usuário
            val snap = firestore.collection(COLECAO_CATEGORIAS)
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            if (!snap.isEmpty) {
                val lista = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val nome = doc.getString("nome") ?: ""
                    val slug = doc.getString("slug") ?: nome.lowercase()
                    val cor = doc.getString("cor") ?: "#4A90E2"
                    val ordem = doc.getLong("ordem")?.toInt() ?: 0
                    val imageUrl = doc.getString("imageUrl") ?: doc.getString("imageUri")
                    val audioUrl = doc.getString("audioUrl") ?: doc.getString("audioPath")
                    Categoria(
                        id = id,
                        nome = nome,
                        slug = slug,
                        cor = cor,
                        ordem = ordem,
                        imageUrl = imageUrl,
                        imageUri = imageUrl,
                        audioUrl = audioUrl,
                        audioPath = audioUrl
                    )
                }.sortedBy { it.ordem }.toMutableList()

                // Carrega os botões associados a cada categoria
                for (cat in lista) {
                    val botoesSnap = firestore.collection(COLECAO_BOTOES)
                        .whereEqualTo("categoria_id", cat.id)
                        .whereEqualTo("owner_uid", userId)
                        .get()
                        .await()

                    val botoes = botoesSnap.documents.mapNotNull { bDoc ->
                        val bId = bDoc.getString("id") ?: bDoc.id
                        val rotulo = bDoc.getString("rotulo") ?: ""
                        val significado = bDoc.getString("significado") ?: rotulo
                        val imagem = bDoc.getString("imagem")
                        val imagemId = bDoc.getString("imagem_id")
                        val audio = bDoc.getString("audio")
                        val audioId = bDoc.getString("audio_id")
                        val bOrdem = bDoc.getLong("ordem")?.toInt() ?: 0
                        val genStr = bDoc.getString("defaultGender") ?: "FEMALE"
                        val genero = try { GeneroAudio.valueOf(genStr) } catch (e: Exception) { GeneroAudio.FEMALE }
                        val bImageUrl = bDoc.getString("imageUrl") ?: bDoc.getString("imageUri") ?: imagem ?: imagemId
                        val bAudioUrl = bDoc.getString("audioUrl") ?: bDoc.getString("audioPath") ?: audio ?: audioId

                        CategoriaItem(
                            id = bId,
                            rotulo = rotulo,
                            significado = significado,
                            imagem = imagem ?: bImageUrl,
                            imagem_id = imagemId ?: bImageUrl,
                            audio = audio ?: bAudioUrl,
                            audio_id = audioId ?: bAudioUrl,
                            ordem = bOrdem,
                            defaultGender = genero,
                            imageUrl = bImageUrl,
                            audioUrl = bAudioUrl
                        )
                    }.sortedBy { it.ordem }

                    cat.botoes.clear()
                    cat.botoes.addAll(botoes)
                }

                lista
            } else {
                // Fallback para colecao legada 'usuarios/{uid}/categorias' ou categorias padrão
                val snapLegado = colecaoCategoriasUser(userId).get().await()
                if (!snapLegado.isEmpty) {
                    snapLegado.documents.mapNotNull { it.toObject(Categoria::class.java) }.sortedBy { it.ordem }
                } else {
                    // Inicializa categorias padrão e sincroniza no Firestore
                    val padrao = CategoriaRepository.getCategoriasPadrao()
                    for (cat in padrao) {
                        salvarCategoria(userId, cat)
                        for (botao in cat.botoes) {
                            salvarBotao(userId, cat.id, botao)
                        }
                    }
                    padrao
                }
            }
        } catch (e: Exception) {
            CategoriaRepository.getCategoriasPadrao()
        }
    }

    suspend fun salvarCategoria(userId: String, categoria: Categoria): Result<Categoria> {
        return try {
            val catId = categoria.id.ifBlank { "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) }
            val slug = categoria.slug.ifBlank { categoria.nome.trim().lowercase().replace(" ", "_") }
            val catFinal = categoria.copy(id = catId, slug = slug)

            val dadosCategoria = hashMapOf<String, Any>(
                "id" to catId,
                "nome" to catFinal.nome,
                "slug" to slug,
                "cor" to catFinal.cor,
                "ordem" to catFinal.ordem,
                "owner_uid" to userId
            )
            catFinal.imageUrl?.takeIf { it.isNotBlank() }?.let {
                dadosCategoria["imageUrl"] = it
                dadosCategoria["imageUri"] = it
            }
            catFinal.audioUrl?.takeIf { it.isNotBlank() }?.let {
                dadosCategoria["audioUrl"] = it
                dadosCategoria["audioPath"] = it
            }

            // Sincroniza em ambas as coleções para consistência
            firestore.collection(COLECAO_CATEGORIAS).document(catId).set(dadosCategoria).await()
            colecaoCategoriasUser(userId).document(catId).set(catFinal).await()

            triggerService.notificarAtualizacao()
            Result.success(catFinal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun excluirCategoria(userId: String, categoriaId: String): Result<Unit> {
        return try {
            // Remove botões associados em 'categoria_botao'
            val botoes = firestore.collection(COLECAO_BOTOES)
                .whereEqualTo("categoria_id", categoriaId)
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            for (doc in botoes.documents) {
                doc.reference.delete().await()
            }

            // Remove da coleção 'categoria'
            firestore.collection(COLECAO_CATEGORIAS).document(categoriaId).delete().await()

            // Remove da coleção de usuário
            colecaoCategoriasUser(userId).document(categoriaId).delete().await()

            triggerService.notificarAtualizacao()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // OPERAÇÕES DE ITENS / BOTÕES (CRUD)
    // ==========================================

    suspend fun listarBotoes(userId: String, categoriaId: String): List<CategoriaItem> {
        return try {
            val snap = firestore.collection(COLECAO_BOTOES)
                .whereEqualTo("categoria_id", categoriaId)
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            if (!snap.isEmpty) {
                snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val rotulo = doc.getString("rotulo") ?: ""
                    val significado = doc.getString("significado") ?: rotulo
                    val imagem = doc.getString("imagem")
                    val imagemId = doc.getString("imagem_id")
                    val audio = doc.getString("audio")
                    val audioId = doc.getString("audio_id")
                    val ordem = doc.getLong("ordem")?.toInt() ?: 0
                    val genStr = doc.getString("defaultGender") ?: "FEMALE"
                    val genero = try { GeneroAudio.valueOf(genStr) } catch (e: Exception) { GeneroAudio.FEMALE }
                    val bImageUrl = doc.getString("imageUrl") ?: doc.getString("imageUri") ?: imagem ?: imagemId
                    val bAudioUrl = doc.getString("audioUrl") ?: doc.getString("audioPath") ?: audio ?: audioId

                    CategoriaItem(
                        id = id,
                        rotulo = rotulo,
                        significado = significado,
                        imagem = imagem ?: bImageUrl,
                        imagem_id = imagemId ?: bImageUrl,
                        audio = audio ?: bAudioUrl,
                        audio_id = audioId ?: bAudioUrl,
                        ordem = ordem,
                        defaultGender = genero,
                        imageUrl = bImageUrl,
                        audioUrl = bAudioUrl
                    )
                }.sortedBy { it.ordem }
            } else {
                // Tenta carregar da categoria embutida
                val docCat = colecaoCategoriasUser(userId).document(categoriaId).get().await()
                val cat = docCat.toObject(Categoria::class.java)
                cat?.botoes ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun salvarBotao(
        userId: String,
        categoriaId: String,
        item: CategoriaItem,
        context: Context? = null,
        imagemUriLocal: Uri? = null,
        audioUriLocal: Uri? = null
    ): Result<CategoriaItem> {
        return try {
            var imagemFinal = item.imagem
            var imagemIdFinal = item.imagem_id

            if (context != null && imagemUriLocal != null) {
                val resUpload = imageProcessor.processarESalvarImagem(
                    context = context,
                    userId = userId,
                    imagemUri = imagemUriLocal,
                    nomeOriginal = item.rotulo.ifBlank { "imagem" } + ".jpg"
                )
                if (resUpload.isSuccess) {
                    val midia = resUpload.getOrThrow()
                    imagemIdFinal = midia.midiaId
                    imagemFinal = midia.nomeArquivo
                }
            }

            val botaoId = item.id.ifBlank { "b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) }
            val itemFinal = item.copy(
                id = botaoId,
                rotulo = item.rotulo.ifBlank { "Botão" },
                significado = item.significado.ifBlank { item.rotulo.ifBlank { "Botão" } },
                imagem = imagemFinal ?: item.imageUrl,
                imagem_id = imagemIdFinal ?: item.imageUrl,
                imageUrl = item.imageUrl ?: imagemFinal,
                audio = item.audio ?: item.audioUrl,
                audio_id = item.audio_id ?: item.audioUrl,
                audioUrl = item.audioUrl ?: item.audio
            )

            val dadosBotao = hashMapOf<String, Any>(
                "id" to botaoId,
                "categoria_id" to categoriaId,
                "rotulo" to itemFinal.rotulo,
                "significado" to itemFinal.significado,
                "imagem" to (itemFinal.imagem ?: ""),
                "imagem_id" to (itemFinal.imagem_id ?: ""),
                "imageUrl" to (itemFinal.imageUrl ?: itemFinal.imagem ?: ""),
                "imageUri" to (itemFinal.imageUrl ?: itemFinal.imagem ?: ""),
                "audio" to (itemFinal.audio ?: ""),
                "audio_id" to (itemFinal.audio_id ?: ""),
                "audioUrl" to (itemFinal.audioUrl ?: itemFinal.audio ?: ""),
                "audioPath" to (itemFinal.audioUrl ?: itemFinal.audio ?: ""),
                "ordem" to itemFinal.ordem,
                "defaultGender" to itemFinal.defaultGender.name,
                "owner_uid" to userId
            )

            // Salva na coleção 'categoria_botao'
            firestore.collection(COLECAO_BOTOES).document(botaoId).set(dadosBotao).await()

            // Atualiza também na lista da categoria de usuários para compatibilidade
            val docCatRef = colecaoCategoriasUser(userId).document(categoriaId)
            val docCatSnap = docCatRef.get().await()
            val cat = docCatSnap.toObject(Categoria::class.java)
            if (cat != null) {
                val idx = cat.botoes.indexOfFirst { it.id == botaoId }
                if (idx >= 0) {
                    cat.botoes[idx] = itemFinal
                } else {
                    cat.botoes.add(itemFinal)
                }
                docCatRef.set(cat).await()
            }

            triggerService.notificarAtualizacao()
            Result.success(itemFinal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun excluirBotao(
        userId: String,
        categoriaId: String,
        botaoId: String
    ): Result<Unit> {
        return try {
            // Remove da coleção 'categoria_botao'
            firestore.collection(COLECAO_BOTOES).document(botaoId).delete().await()

            // Remove da coleção de usuários
            val docCatRef = colecaoCategoriasUser(userId).document(categoriaId)
            val docCatSnap = docCatRef.get().await()
            val cat = docCatSnap.toObject(Categoria::class.java)
            if (cat != null) {
                cat.botoes.removeAll { it.id == botaoId }
                cat.botoes.forEachIndexed { index, b -> b.ordem = index }
                docCatRef.set(cat).await()
            }

            triggerService.notificarAtualizacao()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
