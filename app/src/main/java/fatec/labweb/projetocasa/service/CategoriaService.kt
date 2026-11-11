package fatec.labweb.projetocasa.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
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
        private const val TAG = "CategoriaService"
        // Coleções legadas (mantidas para fallback/retrocompatibilidade)
        const val COLECAO_CATEGORIAS = "categoria"
        const val COLECAO_BOTOES = "categoria_botao"
        // Alinhado com a Web API CASA-API (MAX_CATEGORIAS = 3)
        const val MAX_CATEGORIAS = 3
        const val MAX_BOTOES_POR_CATEGORIA = 5
    }

    /**
     * Coleção canônica conforme Web API e ESP32: /usuarios/{userId}/categorias/{categoriaId}
     */
    private fun colecaoCategoriasUser(userId: String) =
        firestore.collection("usuarios").document(userId).collection("categorias")

    /**
     * Incrementa config_versao em /dispositivos onde owner_uid == userId
     * idêntico à função bump_dispositivos() da Web API.
     */
    suspend fun bumpDispositivos(userId: String) {
        try {
            val snaps = firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()
            for (doc in snaps.documents) {
                doc.reference.update("config_versao", FieldValue.increment(1)).await()
            }
            Log.d(TAG, "bumpDispositivos executado com sucesso para user: $userId (dispositivos: ${snaps.size()})")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar bumpDispositivos para user: $userId", e)
        }
    }

    // ==========================================
    // OPERAÇÕES DE CATEGORIA (CRUD)
    // ==========================================

    suspend fun listarCategorias(userId: String): List<Categoria> {
        return try {
            // 1. Busca prioritária na coleção canônica: /usuarios/{userId}/categorias
            val snapCanonico = colecaoCategoriasUser(userId).get().await()
            if (!snapCanonico.isEmpty) {
                return snapCanonico.documents.mapNotNull { doc ->
                    doc.toObject(Categoria::class.java)
                }.sortedBy { it.ordem }
            }

            // 2. Fallback: Coleção legada na raiz 'categoria'
            val snapLegado = firestore.collection(COLECAO_CATEGORIAS)
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()

            if (!snapLegado.isEmpty) {
                val lista = snapLegado.documents.mapNotNull { doc ->
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

                // Carrega botões legados da coleção 'categoria_botao'
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

                    // Migra automaticamente para a coleção canônica
                    colecaoCategoriasUser(userId).document(cat.id).set(cat).await()
                }

                lista
            } else {
                // 3. Nenhuma categoria encontrada: Inicializa categorias padrão compatíveis com a Web API
                val padrao = CategoriaRepository.getCategoriasPadrao()
                for (cat in padrao) {
                    colecaoCategoriasUser(userId).document(cat.id).set(cat).await()
                    // Espelha legado
                    salvarCategoriaLegada(userId, cat)
                }
                bumpDispositivos(userId)
                triggerService.notificarAtualizacao()
                padrao
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar categorias para userId: $userId", e)
            CategoriaRepository.getCategoriasPadrao()
        }
    }

    suspend fun salvarCategoria(userId: String, categoria: Categoria): Result<Categoria> {
        return try {
            val catId = categoria.id.ifBlank { "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) }
            val slug = categoria.slug.ifBlank { categoria.nome.trim().lowercase().replace(" ", "_") }

            // Validação de limite MAX_CATEGORIAS (3)
            val atuais = colecaoCategoriasUser(userId).get().await()
            if (atuais.size() >= MAX_CATEGORIAS && atuais.documents.none { it.id == catId }) {
                return Result.failure(IllegalStateException("Limite de $MAX_CATEGORIAS categorias atingido."))
            }

            val catFinal = categoria.copy(id = catId, slug = slug)

            // Salva na coleção canônica (/usuarios/{userId}/categorias/{catId})
            colecaoCategoriasUser(userId).document(catId).set(catFinal).await()

            // Espelha na coleção legada para retrocompatibilidade
            salvarCategoriaLegada(userId, catFinal)

            // Notifica via hardware (bump_dispositivos) e realtime fallback
            bumpDispositivos(userId)
            triggerService.notificarAtualizacao()

            Result.success(catFinal)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar categoria", e)
            Result.failure(e)
        }
    }

    suspend fun excluirCategoria(userId: String, categoriaId: String): Result<Unit> {
        return try {
            // Remove da coleção canônica
            colecaoCategoriasUser(userId).document(categoriaId).delete().await()

            // Remove da coleção legada 'categoria'
            try {
                firestore.collection(COLECAO_CATEGORIAS).document(categoriaId).delete().await()
            } catch (ignored: Exception) {}

            // Remove botões associados em 'categoria_botao' legada
            try {
                val botoes = firestore.collection(COLECAO_BOTOES)
                    .whereEqualTo("categoria_id", categoriaId)
                    .whereEqualTo("owner_uid", userId)
                    .get()
                    .await()
                for (doc in botoes.documents) {
                    doc.reference.delete().await()
                }
            } catch (ignored: Exception) {}

            // Notifica hardware
            bumpDispositivos(userId)
            triggerService.notificarAtualizacao()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir categoria", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // OPERAÇÕES DE ITENS / BOTÕES (CRUD)
    // ==========================================

    suspend fun listarBotoes(userId: String, categoriaId: String): List<CategoriaItem> {
        return try {
            // Busca na coleção canônica da categoria
            val docCat = colecaoCategoriasUser(userId).document(categoriaId).get().await()
            if (docCat.exists()) {
                val cat = docCat.toObject(Categoria::class.java)
                if (cat != null && cat.botoes.isNotEmpty()) {
                    return cat.botoes.sortedBy { it.ordem }
                }
            }

            // Fallback para coleção legada 'categoria_botao'
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
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar botoes para cat: $categoriaId", e)
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

            // Atualiza na coleção canônica /usuarios/{userId}/categorias/{categoriaId}
            val docCatRef = colecaoCategoriasUser(userId).document(categoriaId)
            val docCatSnap = docCatRef.get().await()
            val cat = docCatSnap.toObject(Categoria::class.java)
            if (cat != null) {
                val idx = cat.botoes.indexOfFirst { it.id == botaoId }
                if (idx >= 0) {
                    cat.botoes[idx] = itemFinal
                } else {
                    if (cat.botoes.size >= MAX_BOTOES_POR_CATEGORIA) {
                        return Result.failure(IllegalStateException("Limite de $MAX_BOTOES_POR_CATEGORIA botões atingido nesta categoria."))
                    }
                    cat.botoes.add(itemFinal)
                }
                docCatRef.set(cat).await()
            }

            // Espelha na coleção legada 'categoria_botao'
            try {
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
                firestore.collection(COLECAO_BOTOES).document(botaoId).set(dadosBotao).await()
            } catch (ignored: Exception) {}

            // Notifica hardware
            bumpDispositivos(userId)
            triggerService.notificarAtualizacao()

            Result.success(itemFinal)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar botao", e)
            Result.failure(e)
        }
    }

    suspend fun excluirBotao(
        userId: String,
        categoriaId: String,
        botaoId: String
    ): Result<Unit> {
        return try {
            // Remove da coleção canônica
            val docCatRef = colecaoCategoriasUser(userId).document(categoriaId)
            val docCatSnap = docCatRef.get().await()
            val cat = docCatSnap.toObject(Categoria::class.java)
            if (cat != null) {
                cat.botoes.removeAll { it.id == botaoId }
                cat.botoes.forEachIndexed { index, b -> b.ordem = index }
                docCatRef.set(cat).await()
            }

            // Remove da coleção legada 'categoria_botao'
            try {
                firestore.collection(COLECAO_BOTOES).document(botaoId).delete().await()
            } catch (ignored: Exception) {}

            // Notifica hardware
            bumpDispositivos(userId)
            triggerService.notificarAtualizacao()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir botao", e)
            Result.failure(e)
        }
    }

    private suspend fun salvarCategoriaLegada(userId: String, categoria: Categoria) {
        try {
            val dadosCategoria = hashMapOf<String, Any>(
                "id" to categoria.id,
                "nome" to categoria.nome,
                "slug" to categoria.slug,
                "cor" to categoria.cor,
                "ordem" to categoria.ordem,
                "owner_uid" to userId
            )
            categoria.imageUrl?.takeIf { it.isNotBlank() }?.let {
                dadosCategoria["imageUrl"] = it
                dadosCategoria["imageUri"] = it
            }
            categoria.audioUrl?.takeIf { it.isNotBlank() }?.let {
                dadosCategoria["audioUrl"] = it
                dadosCategoria["audioPath"] = it
            }
            firestore.collection(COLECAO_CATEGORIAS).document(categoria.id).set(dadosCategoria).await()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar categoria na coleção legada: ${e.message}")
        }
    }
}
