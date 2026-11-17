package fatec.labweb.projetocasa.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.service.Esp32ImageProcessor
import fatec.labweb.projetocasa.service.Esp32TriggerService
import fatec.labweb.projetocasa.util.AudioPredefinidoHelper
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CategoriaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val imageProcessor: Esp32ImageProcessor = Esp32ImageProcessor(),
    private val triggerService: Esp32TriggerService = Esp32TriggerService()
) {
    data class ConflitoBotaoGlobal(
        val categoriaId: String,
        val categoriaNome: String,
        val botaoExistente: CategoriaItem,
        val desenhoConflitante: DesenhoBotao? = null
    )

    companion object {
        const val MIN_CATEGORIAS = 3
        const val MAX_CATEGORIAS = 3
        const val MIN_BOTOES_POR_CATEGORIA = 5
        const val MAX_BOTOES_POR_CATEGORIA = 5

        const val MSG_MAX_CATEGORIAS = "Você não pode adicionar mais do que 3 categorias."
        const val MSG_MIN_CATEGORIAS = "Você não pode ter menos do que 3 categorias. É obrigatório manter exatamente 3 categorias."
        const val MSG_MAX_ACOES_CATEGORIA = "Você não pode adicionar mais do que 5 ações nesta categoria."
        const val MSG_MIN_ACOES_CATEGORIA = "Cada categoria precisa ter exatamente 5 ações. Não é permitido salvar com menos de 5 ações."

        fun validarAdicaoCategoria(totalCategoriasAtuais: Int): Result<Unit> {
            return if (totalCategoriasAtuais >= MAX_CATEGORIAS) {
                Result.failure(IllegalStateException(MSG_MAX_CATEGORIAS))
            } else {
                Result.success(Unit)
            }
        }

        fun validarRemocaoCategoria(totalCategoriasAtuais: Int): Result<Unit> {
            return if (totalCategoriasAtuais <= MIN_CATEGORIAS) {
                Result.failure(IllegalStateException(MSG_MIN_CATEGORIAS))
            } else {
                Result.success(Unit)
            }
        }

        fun validarAdicaoAcao(totalAcoesAtuais: Int): Result<Unit> {
            return if (totalAcoesAtuais >= MAX_BOTOES_POR_CATEGORIA) {
                Result.failure(IllegalStateException(MSG_MAX_ACOES_CATEGORIA))
            } else {
                Result.success(Unit)
            }
        }

        fun validarRemocaoAcao(totalAcoesAtuais: Int): Result<Unit> {
            return if (totalAcoesAtuais <= MIN_BOTOES_POR_CATEGORIA) {
                Result.failure(IllegalStateException(MSG_MIN_ACOES_CATEGORIA))
            } else {
                Result.success(Unit)
            }
        }

        fun validarCardinalidadeCompleta(categorias: List<Categoria>): Result<Unit> {
            if (categorias.size < MIN_CATEGORIAS) {
                return Result.failure(IllegalStateException(MSG_MIN_CATEGORIAS))
            }
            if (categorias.size > MAX_CATEGORIAS) {
                return Result.failure(IllegalStateException(MSG_MAX_CATEGORIAS))
            }
            for (cat in categorias) {
                if (cat.botoes.size < MIN_BOTOES_POR_CATEGORIA) {
                    return Result.failure(IllegalStateException(MSG_MIN_ACOES_CATEGORIA))
                }
                if (cat.botoes.size > MAX_BOTOES_POR_CATEGORIA) {
                    return Result.failure(IllegalStateException(MSG_MAX_ACOES_CATEGORIA))
                }
            }
            return Result.success(Unit)
        }

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
                        CategoriaItem("b13", "Cozinha", "Cozinha", null, null, null, null, 2, GeneroAudio.FEMALE),
                        CategoriaItem("b14", "Jardim", "Jardim", null, null, null, null, 3, GeneroAudio.FEMALE),
                        CategoriaItem("b15", "Quintal", "Quintal", null, null, null, null, 4, GeneroAudio.MALE)
                    )
                )
            )
        }

        /**
         * Verifica se um CategoriaItem corresponde a um DesenhoBotao predefinido
         * com base em ID, slug, nome/rótulo normalizado ou URL oficial.
         */
        fun itemCorrespondeAoDesenho(item: CategoriaItem, desenho: DesenhoBotao): Boolean {
            val rotuloNorm = AudioPredefinidoHelper.normalizarTexto(item.rotulo)
            val rotuloPrincipalNorm = AudioPredefinidoHelper.normalizarTexto(item.rotulo.split("/").first())
            val significadoNorm = AudioPredefinidoHelper.normalizarTexto(item.significado)
            val idNorm = AudioPredefinidoHelper.normalizarTexto(item.id)
            val imagemNorm = AudioPredefinidoHelper.normalizarTexto(item.imagem ?: "")
            val imagemIdNorm = AudioPredefinidoHelper.normalizarTexto(item.imagem_id ?: "")
            val imageUrl = (item.imageUrl ?: item.imageUri ?: "").trim()

            val dTituloNorm = AudioPredefinidoHelper.normalizarTexto(desenho.titulo)
            val dPrincipalNorm = AudioPredefinidoHelper.normalizarTexto(desenho.titulo.split("/").first())
            val dPartesTitulo = desenho.titulo.split("/")
                .map { AudioPredefinidoHelper.normalizarTexto(it) }
                .filter { it.isNotBlank() && it != "dormir" }
            val dIdNorm = AudioPredefinidoHelper.normalizarTexto(desenho.id)
            val dNameNorm = AudioPredefinidoHelper.normalizarTexto(desenho.name)
            val dSlugNorm = dNameNorm.replace("_", " ")

            val termosDesenho = (listOf(dTituloNorm, dPrincipalNorm, dIdNorm, dNameNorm, dSlugNorm) + dPartesTitulo)
                .filter { it.isNotBlank() }
                .toSet()

            if (rotuloNorm.isNotBlank() && termosDesenho.contains(rotuloNorm)) return true
            if (rotuloPrincipalNorm.isNotBlank() && termosDesenho.contains(rotuloPrincipalNorm)) return true
            if (significadoNorm.isNotBlank() && termosDesenho.contains(significadoNorm)) return true
            if (idNorm.isNotBlank() && (idNorm == dIdNorm || idNorm == dNameNorm)) return true
            if (imagemNorm.isNotBlank() && (imagemNorm == dIdNorm || imagemNorm == dNameNorm)) return true
            if (imagemIdNorm.isNotBlank() && (imagemIdNorm == dIdNorm || imagemIdNorm == dNameNorm)) return true
            if (desenho.driveUrl.isNotBlank() && (imageUrl == desenho.driveUrl || item.imagem == desenho.driveUrl || item.imagem_id == desenho.driveUrl)) {
                return true
            }

            return false
        }

        /**
         * Identifica o DesenhoBotao predefinido correspondente a um CategoriaItem, se houver.
         */
        fun identificarDesenhoPredefinido(item: CategoriaItem): DesenhoBotao? {
            return DesenhoBotao.values().firstOrNull { itemCorrespondeAoDesenho(item, it) }
        }

        /**
         * Verifica se dois botões/itens conflitam por ID, slug, rótulo normalizado ou opção predefinida.
         */
        fun botoesConflitam(itemA: CategoriaItem, itemB: CategoriaItem): Boolean {
            val idA = AudioPredefinidoHelper.normalizarTexto(itemA.id)
            val idB = AudioPredefinidoHelper.normalizarTexto(itemB.id)
            if (idA.isNotBlank() && idB.isNotBlank() && idA == idB) {
                return true
            }

            val rotuloA = AudioPredefinidoHelper.normalizarTexto(itemA.rotulo)
            val rotuloB = AudioPredefinidoHelper.normalizarTexto(itemB.rotulo)
            if (rotuloA.isNotBlank() && rotuloB.isNotBlank() && rotuloA == rotuloB) {
                return true
            }

            val principalA = AudioPredefinidoHelper.normalizarTexto(itemA.rotulo.split("/").first())
            val principalB = AudioPredefinidoHelper.normalizarTexto(itemB.rotulo.split("/").first())
            if (principalA.isNotBlank() && principalB.isNotBlank() && principalA == principalB) {
                return true
            }

            if (rotuloA.isNotBlank() && rotuloA == idB) return true
            if (rotuloB.isNotBlank() && rotuloB == idA) return true

            val desenhoA = identificarDesenhoPredefinido(itemA)
            val desenhoB = identificarDesenhoPredefinido(itemB)
            if (desenhoA != null && desenhoB != null && desenhoA == desenhoB) {
                return true
            }

            return false
        }

        fun ehMesmaCategoria(cat: Categoria, identificadorAlvo: String): Boolean {
            if (identificadorAlvo.isBlank()) return false
            val alvoNorm = AudioPredefinidoHelper.normalizarTexto(identificadorAlvo)
            val idNorm = AudioPredefinidoHelper.normalizarTexto(cat.id)
            val slugNorm = AudioPredefinidoHelper.normalizarTexto(cat.slug)
            return idNorm == alvoNorm ||
                (slugNorm.isNotBlank() && slugNorm == alvoNorm) ||
                idNorm == "cat_$alvoNorm" ||
                (alvoNorm.startsWith("cat_") && slugNorm == alvoNorm.removePrefix("cat_"))
        }

        /**
         * Valida unicidade global (cross-category) de um botão candidato em relação a todas as categorias existentes.
         * Quando em modo de edição, ignora apenas o próprio botão (`ignorarBotaoId`) na categoria em edição (`categoriaIdAtual`).
         */
        fun verificarConflitoGlobalBotao(
            categorias: List<Categoria>,
            candidato: CategoriaItem,
            categoriaIdAtual: String = "",
            ignorarBotaoId: String = ""
        ): ConflitoBotaoGlobal? {
            for (cat in categorias) {
                val mesmaCat = ehMesmaCategoria(cat, categoriaIdAtual)
                for (botaoExistente in cat.botoes) {
                    if (ignorarBotaoId.isNotBlank() && botaoExistente.id.equals(ignorarBotaoId, ignoreCase = true)) {
                        if (mesmaCat || categoriaIdAtual.isBlank()) {
                            continue
                        }
                    }

                    if (botoesConflitam(candidato, botaoExistente)) {
                        return ConflitoBotaoGlobal(
                            categoriaId = cat.id,
                            categoriaNome = cat.nome.ifBlank { cat.slug.ifBlank { "Outra Categoria" } },
                            botaoExistente = botaoExistente,
                            desenhoConflitante = identificarDesenhoPredefinido(botaoExistente)
                        )
                    }
                }
            }
            return null
        }

        /**
         * Valida todos os botões de uma categoria (nova ou editada) contra duplicidade interna e contra todas as demais categorias.
         */
        fun validarUnicidadeBotoesCategoria(
            todasCategorias: List<Categoria>,
            categoriaAlvo: Categoria
        ): ConflitoBotaoGlobal? {
            val botoesAlvo = categoriaAlvo.botoes
            for (i in botoesAlvo.indices) {
                val botaoAtual = botoesAlvo[i]
                // 1. Verifica duplicidade dentro da própria categoria
                for (j in 0 until i) {
                    if (botoesConflitam(botaoAtual, botoesAlvo[j])) {
                        return ConflitoBotaoGlobal(
                            categoriaId = categoriaAlvo.id,
                            categoriaNome = categoriaAlvo.nome.ifBlank { "Categoria Atual" },
                            botaoExistente = botoesAlvo[j],
                            desenhoConflitante = identificarDesenhoPredefinido(botoesAlvo[j])
                        )
                    }
                }
                // 2. Verifica duplicidade contra todas as outras categorias
                for (outraCat in todasCategorias) {
                    if (ehMesmaCategoria(outraCat, categoriaAlvo.id) ||
                        (categoriaAlvo.slug.isNotBlank() && ehMesmaCategoria(outraCat, categoriaAlvo.slug))
                    ) {
                        continue
                    }
                    for (botaoOutra in outraCat.botoes) {
                        if (botoesConflitam(botaoAtual, botaoOutra)) {
                            return ConflitoBotaoGlobal(
                                categoriaId = outraCat.id,
                                categoriaNome = outraCat.nome.ifBlank { outraCat.slug.ifBlank { "Outra Categoria" } },
                                botaoExistente = botaoOutra,
                                desenhoConflitante = identificarDesenhoPredefinido(botaoOutra)
                            )
                        }
                    }
                }
            }
            return null
        }

        /**
         * Retorna o mapa de todos os DesenhoBotao já alocados em qualquer categoria e o nome da respectiva categoria.
         */
        fun obterMapaDesenhosEmUsoGlobal(
            categorias: List<Categoria>,
            categoriaIdAtual: String = "",
            ignorarBotaoId: String = ""
        ): Map<DesenhoBotao, String> {
            val emUso = linkedMapOf<DesenhoBotao, String>()
            for (cat in categorias) {
                val mesmaCat = ehMesmaCategoria(cat, categoriaIdAtual)
                val nomeCat = cat.nome.ifBlank { cat.slug.ifBlank { "Categoria" } }
                for (botao in cat.botoes) {
                    if (ignorarBotaoId.isNotBlank() && botao.id.equals(ignorarBotaoId, ignoreCase = true)) {
                        if (mesmaCat || categoriaIdAtual.isBlank()) {
                            continue
                        }
                    }
                    for (desenho in DesenhoBotao.values()) {
                        if (!emUso.containsKey(desenho) && itemCorrespondeAoDesenho(botao, desenho)) {
                            emUso[desenho] = nomeCat
                        }
                    }
                }
            }
            return emUso
        }

        /**
         * Filtra os DesenhoBotao predefinidos retornando apenas os que não estão alocados em nenhuma categoria.
         */
        fun filtrarDesenhosDisponiveisGlobalmente(
            categorias: List<Categoria>,
            categoriaIdAtual: String = "",
            ignorarBotaoId: String = ""
        ): List<DesenhoBotao> {
            val emUso = obterMapaDesenhosEmUsoGlobal(categorias, categoriaIdAtual, ignorarBotaoId)
            return DesenhoBotao.values().filter { !emUso.containsKey(it) }
        }
    }

    private var cacheCategorias: MutableList<Categoria> = mutableListOf()

    private fun colecaoCategorias(userId: String) =
        firestore.collection("usuarios").document(userId).collection("categorias")

    suspend fun bumpDispositivos(userId: String) {
        try {
            val snaps = firestore.collection("dispositivos")
                .whereEqualTo("owner_uid", userId)
                .get()
                .await()
            for (doc in snaps.documents) {
                doc.reference.update("config_versao", FieldValue.increment(1)).await()
            }
        } catch (ignored: Exception) {}
    }

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
                bumpDispositivos(userId)
                triggerService.dispararFlagParaTodosDispositivos(userId)
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
                return Result.failure(IllegalStateException(MSG_MAX_CATEGORIAS))
            }
            if (categoria.botoes.size > MAX_BOTOES_POR_CATEGORIA) {
                return Result.failure(IllegalStateException(MSG_MAX_ACOES_CATEGORIA))
            }
            if (categoria.botoes.size < MIN_BOTOES_POR_CATEGORIA) {
                return Result.failure(IllegalStateException(MSG_MIN_ACOES_CATEGORIA))
            }
            val catId = categoria.id.ifBlank { UUID.randomUUID().toString().replace("-", "").substring(0, 12) }
            val catFinal = categoria.copy(id = catId)

            val conflito = validarUnicidadeBotoesCategoria(cacheCategorias, catFinal)
            if (conflito != null) {
                val msg = if (ehMesmaCategoria(catFinal, conflito.categoriaId)) {
                    "O botão \"${conflito.botaoExistente.rotulo}\" está duplicado nesta categoria! Não é permitido botões repetidos."
                } else {
                    "O botão \"${conflito.botaoExistente.rotulo}\" já está vinculado à categoria \"${conflito.categoriaNome}\"! Não é permitido repetir botões entre categorias."
                }
                return Result.failure(IllegalStateException(msg))
            }

            val idx = cacheCategorias.indexOfFirst { it.id == catId }
            if (idx >= 0) cacheCategorias[idx] = catFinal else cacheCategorias.add(catFinal)

            try {
                colecaoCategorias(userId).document(catId).set(catFinal).await()
            } catch (eFs: Exception) {
                android.util.Log.w("CategoriaRepository", "Salvar categoria tolerante a regras v2: ${eFs.message}")
            }

            try {
                bumpDispositivos(userId)
                triggerService.dispararFlagParaTodosDispositivos(userId)
            } catch (eTrig: Exception) {
                android.util.Log.w("CategoriaRepository", "Trigger de hardware tolerante: ${eTrig.message}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removerCategoria(
        userId: String,
        categoriaId: String
    ): Result<Unit> {
        val validacao = validarRemocaoCategoria(cacheCategorias.size)
        if (validacao.isFailure) {
            return validacao
        }
        cacheCategorias.removeAll { it.id == categoriaId }
        cacheCategorias.forEachIndexed { index, c -> c.ordem = index }
        return try {
            try {
                colecaoCategorias(userId).document(categoriaId).delete().await()
                bumpDispositivos(userId)
                triggerService.dispararFlagParaTodosDispositivos(userId)
            } catch (eFs: Exception) {
                android.util.Log.w("CategoriaRepository", "Remoção de categoria tolerante: ${eFs.message}")
            }
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
            return Result.failure(IllegalStateException(MSG_MAX_ACOES_CATEGORIA))
        }

        val conflitoGlobal = verificarConflitoGlobalBotao(
            categorias = cacheCategorias,
            candidato = novoItem,
            categoriaIdAtual = categoriaId,
            ignorarBotaoId = ""
        )
        if (conflitoGlobal != null) {
            val msg = if (ehMesmaCategoria(cat, conflitoGlobal.categoriaId)) {
                "O botão \"${novoItem.rotulo}\" já foi adicionado a esta categoria! Não é permitido botões repetidos."
            } else {
                "O botão \"${novoItem.rotulo}\" já está vinculado à categoria \"${conflitoGlobal.categoriaNome}\"! Não é permitido repetir botões entre categorias."
            }
            return Result.failure(IllegalStateException(msg))
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
            try {
                colecaoCategorias(userId).document(categoriaId).set(cat).await()
                bumpDispositivos(userId)
                triggerService.dispararFlagParaTodosDispositivos(userId)
            } catch (eFs: Exception) {
                android.util.Log.w("CategoriaRepository", "Persistência remota tolerante a regras v2: ${eFs.message}")
            }
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

        val validacao = validarRemocaoAcao(cat.botoes.size)
        if (validacao.isFailure) {
            return validacao
        }

        cat.botoes.removeAll { it.id == botaoId }
        // Reordena
        cat.botoes.forEachIndexed { index, b -> b.ordem = index }

        return try {
            try {
                colecaoCategorias(userId).document(categoriaId).set(cat).await()
                bumpDispositivos(userId)
                triggerService.dispararFlagParaTodosDispositivos(userId)
            } catch (eFs: Exception) {
                android.util.Log.w("CategoriaRepository", "Remoção remota tolerante a regras v2: ${eFs.message}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
