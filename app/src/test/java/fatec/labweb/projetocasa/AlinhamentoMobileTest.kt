package fatec.labweb.projetocasa

import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.model.GeneroAudio
import fatec.labweb.projetocasa.repository.CategoriaRepository
import fatec.labweb.projetocasa.service.DirectEdgeTtsService
import fatec.labweb.projetocasa.util.AudioDspHelper
import fatec.labweb.projetocasa.util.AudioPredefinidoHelper
import fatec.labweb.projetocasa.view.AvatarCropView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AlinhamentoMobileTest {

    @Test
    fun dadosDispositivo_bateriaEfetiva_priorizaBateriaPct() {
        val dispComPct = DadosDispositivo(
            mac = "24:62:AB:E4:C1:0C",
            nivelBateria = 80,
            bateria_pct = 95
        )
        assertEquals(95, dispComPct.bateriaEfetiva)

        val dispSemPct = DadosDispositivo(
            mac = "24:62:AB:E4:C1:0C",
            nivelBateria = 75,
            bateria_pct = null
        )
        assertEquals(75, dispSemPct.bateriaEfetiva)
    }

    @Test
    fun dadosDispositivo_contratoPadrao_alinhadoComApiWebEFirmware() {
        val disp = DadosDispositivo(
            mac = "24:62:AB:E4:C1:0C",
            nome = "ESP32 Teste"
        )
        assertEquals("casa", disp.modo)
        assertEquals(1, disp.config_versao)
        assertEquals(100, disp.nivelBateria)
        assertEquals("ESP32 Teste", disp.nome)
        assertEquals("24:62:AB:E4:C1:0C", disp.mac)
    }

    @Test
    fun acionamento_camposContratoWeb_validados() {
        val acionamento = Acionamento(
            botao_id = "btn_1",
            botao_nome = "Água",
            categoria_nome = "Casa",
            dispositivo = "24:62:AB:E4:C1:0C",
            origem = "dispositivo",
            acionado_em = "2026-11-13T22:30:00Z",
            tempo_aproximado = false,
            id = "doc12345"
        )
        assertEquals("btn_1", acionamento.botao_id)
        assertEquals("Água", acionamento.botao_nome)
        assertEquals("Casa", acionamento.categoria_nome)
        assertEquals("24:62:AB:E4:C1:0C", acionamento.dispositivo)
        assertEquals("dispositivo", acionamento.origem)
        assertEquals("doc12345", acionamento.id)
        assertFalse(acionamento.tempo_aproximado)
    }

    @Test
    fun validacaoCodigoPareamentoOled_seisDigitosNumericos() {
        val regexCodigo = Regex("^[0-9]{6}$")

        // Códigos válidos
        assertTrue("123456".matches(regexCodigo))
        assertTrue("000000".matches(regexCodigo))
        assertTrue("987654".matches(regexCodigo))

        // Códigos inválidos
        assertFalse("12345".matches(regexCodigo))
        assertFalse("1234567".matches(regexCodigo))
        assertFalse("12a456".matches(regexCodigo))
        assertFalse("abcdef".matches(regexCodigo))
        assertFalse("".matches(regexCodigo))
        assertFalse(" 123456 ".matches(regexCodigo))
    }

    @Test
    fun formatacaoDataIso8601Utc_compativelComFirestoreEFastAPI() {
        val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val dataString = "2026-11-13T22:30:00Z"
        val dataLimpa = dataString.replace("Z", "")
        val dataParse = formatoIso.parse(dataLimpa)

        assertNotNull(dataParse)
    }

    @Test
    fun volumeDispositivo_limitadoEntreZeroECem() {
        val volumeNegativo = -10
        val volumeExcessivo = 150
        val volumeNormal = 75

        assertEquals(0, volumeNegativo.coerceIn(0, 100))
        assertEquals(100, volumeExcessivo.coerceIn(0, 100))
        assertEquals(75, volumeNormal.coerceIn(0, 100))
    }

    @Test
    fun unicidadeGlobalBotoes_bloqueiaBotaoRepetidoEmOutraCategoria() {
        val categorias = CategoriaRepository.getCategoriasPadrao()

        // "Feliz" já existe na categoria "Emoções" (cat_emocoes).
        // Tentar adicionar "Feliz" na categoria "Desejos" (cat_desejos) deve gerar conflito global.
        val candidatoFelizOutraCat = CategoriaItem(
            id = "btn_novo_1",
            rotulo = "Feliz",
            significado = "Feliz"
        )
        val conflito = CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = categorias,
            candidato = candidatoFelizOutraCat,
            categoriaIdAtual = "cat_desejos"
        )
        assertNotNull(conflito)
        assertEquals("cat_emocoes", conflito?.categoriaId)
        assertEquals("Emoções", conflito?.categoriaNome)
    }

    @Test
    fun unicidadeGlobalBotoes_bloqueiaPorIdSlugOuRotuloNormalizado() {
        val categorias = CategoriaRepository.getCategoriasPadrao()

        // Variação de caixa/acentuação ("ÁGUA" -> "agua" já em "Desejos") sendo inserida em "Casa"
        val candidatoAgua = CategoriaItem(id = "btn_x", rotulo = "  agua ")
        val conflitoAgua = CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = categorias,
            candidato = candidatoAgua,
            categoriaIdAtual = "cat_casa"
        )
        assertNotNull(conflitoAgua)
        assertEquals("cat_desejos", conflitoAgua?.categoriaId)

        // Conflito por imagem_id / slug do DesenhoBotao ("cat_btn_emocao_feliz") mesmo com título composto
        val candidatoPorSlug = CategoriaItem(
            id = "btn_y",
            rotulo = "Feliz / Alegre",
            imagem_id = DesenhoBotao.FELIZ.id
        )
        val conflitoSlug = CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = categorias,
            candidato = candidatoPorSlug,
            categoriaIdAtual = "cat_casa"
        )
        assertNotNull(conflitoSlug)
        assertEquals("cat_emocoes", conflitoSlug?.categoriaId)
    }

    @Test
    fun unicidadeGlobalBotoes_filtraDesenhosDisponiveisEntreTodasCategorias() {
        val categorias = CategoriaRepository.getCategoriasPadrao()

        // Na seleção para "cat_desejos", botões predefinidos já usados em "Emoções" (FELIZ, TRISTE)
        // e em "Casa" (QUARTO, SALA, COZINHA) não devem estar disponíveis
        val disponiveis = CategoriaRepository.filtrarDesenhosDisponiveisGlobalmente(
            categorias = categorias,
            categoriaIdAtual = "cat_desejos"
        )

        assertFalse(disponiveis.contains(DesenhoBotao.FELIZ))
        assertFalse(disponiveis.contains(DesenhoBotao.TRISTE))
        assertFalse(disponiveis.contains(DesenhoBotao.BANHO))
        assertFalse(disponiveis.contains(DesenhoBotao.QUARTO))
        assertFalse(disponiveis.contains(DesenhoBotao.SALA))
        assertFalse(disponiveis.contains(DesenhoBotao.COZINHA))

        // Opções ainda não usadas em nenhuma categoria continuam disponíveis
        assertTrue(disponiveis.contains(DesenhoBotao.MUSICA))
        assertTrue(disponiveis.contains(DesenhoBotao.LER))
        assertTrue(disponiveis.contains(DesenhoBotao.LAVANDERIA))
    }

    @Test
    fun unicidadeGlobalBotoes_permiteEdicaoDoProprioBotaoEBloqueiaTrocaPorExistenteEmOutraCategoria() {
        val categorias = CategoriaRepository.getCategoriasPadrao()

        // Editando o próprio botão "b1" ("Feliz") dentro de "cat_emocoes": deve ser permitido manter "Feliz"
        val botaoFelizEditado = CategoriaItem(
            id = "b1",
            rotulo = "Feliz",
            significado = "Feliz"
        )
        val conflitoProprio = CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = categorias,
            candidato = botaoFelizEditado,
            categoriaIdAtual = "cat_emocoes",
            ignorarBotaoId = "b1"
        )
        assertNull(conflitoProprio)

        // Na edição de "b1" em "cat_emocoes", DesenhoBotao.FELIZ deve continuar disponível para ele mesmo
        val disponiveisParaB1 = CategoriaRepository.filtrarDesenhosDisponiveisGlobalmente(
            categorias = categorias,
            categoriaIdAtual = "cat_emocoes",
            ignorarBotaoId = "b1"
        )
        assertTrue(disponiveisParaB1.contains(DesenhoBotao.FELIZ))
        // Mas DesenhoBotao.QUARTO (em "cat_casa") continua bloqueado
        assertFalse(disponiveisParaB1.contains(DesenhoBotao.QUARTO))

        // Se tentar editar "b1" em "cat_emocoes" mudando o rótulo para "Quarto" (que está em "cat_casa"), deve bloquear
        val tentativaTrocaConflitante = CategoriaItem(
            id = "b1",
            rotulo = "Quarto",
            significado = "Quarto"
        )
        val conflitoTroca = CategoriaRepository.verificarConflitoGlobalBotao(
            categorias = categorias,
            candidato = tentativaTrocaConflitante,
            categoriaIdAtual = "cat_emocoes",
            ignorarBotaoId = "b1"
        )
        assertNotNull(conflitoTroca)
        assertEquals("cat_casa", conflitoTroca?.categoriaId)
    }

    @Test
    fun exclusaoLivreEBannerDinamico_exibeFaltantesEDesapareceAoCompletar() {
        // Quando categoria tem 5 ações e usuário exclui 1 (fica com 4): banner informa exatamente que falta 1 ação
        assertEquals(
            "Esta categoria precisa de 5 ações. Adicione mais 1 ação(ões) para completar.",
            CategoriaRepository.gerarMensagemPendenciaAcoes(4)
        )
        assertEquals(
            "Esta categoria precisa de 5 ações. Adicione mais 2 ação(ões) para completar.",
            CategoriaRepository.gerarMensagemPendenciaAcoes(3)
        )
        // Ao adicionar a ação substituta (completando 5 ações), o banner desaparece (retorna null)
        assertNull(CategoriaRepository.gerarMensagemPendenciaAcoes(5))

        // Quando há menos de 3 categorias: banner informa quantas categorias faltam
        assertEquals(
            "É necessário manter 3 categorias. Adicione mais 1 categoria(s) para completar.",
            CategoriaRepository.gerarMensagemPendenciaCategorias(2)
        )
        // Ao completar 3 categorias, o banner desaparece (retorna null)
        assertNull(CategoriaRepository.gerarMensagemPendenciaCategorias(3))
    }

    @Test
    fun travaLimiteMaximo_bloqueiaApenasQuartaCategoriaESextaAcao() {
        assertTrue(CategoriaRepository.validarAdicaoCategoria(2).isSuccess)
        val erroCat = CategoriaRepository.validarAdicaoCategoria(3)
        assertTrue(erroCat.isFailure)
        assertEquals("Você não pode adicionar mais do que 3 categorias.", erroCat.exceptionOrNull()?.message)

        assertTrue(CategoriaRepository.validarAdicaoAcao(4).isSuccess)
        val erroAcao = CategoriaRepository.validarAdicaoAcao(5)
        assertTrue(erroAcao.isFailure)
        assertEquals("Você não pode adicionar mais do que 5 ações nesta categoria.", erroAcao.exceptionOrNull()?.message)
    }

    @Test
    fun validacaoSalvamentoFinal_bloqueiaIncompletoEAceitaExatosTresPorCinco() {
        val categoriasCompletas = CategoriaRepository.getCategoriasPadrao()
        assertEquals(3, categoriasCompletas.size)
        assertTrue(categoriasCompletas.all { it.botoes.size == 5 })
        assertNull(CategoriaRepository.gerarMensagemPendenciaGlobal(categoriasCompletas))
        assertTrue(CategoriaRepository.validarSalvamentoFinal(categoriasCompletas).isSuccess)

        // Simula exclusão livre de 1 ação na primeira categoria para substituição (4 ações restantes)
        val comUmaAcaoExcluida = categoriasCompletas.mapIndexed { index, cat ->
            if (index == 0) cat.copy(botoes = cat.botoes.dropLast(1).toMutableList()) else cat
        }
        assertNotNull(CategoriaRepository.gerarMensagemPendenciaGlobal(comUmaAcaoExcluida))
        assertTrue(CategoriaRepository.validarSalvamentoFinal(comUmaAcaoExcluida).isFailure)

        // Simula exclusão de 1 categoria (2 categorias restantes)
        val comDuasCategorias = categoriasCompletas.dropLast(1)
        assertEquals(
            "É necessário manter 3 categorias. Adicione mais 1 categoria(s) para completar.",
            CategoriaRepository.gerarMensagemPendenciaGlobal(comDuasCategorias)
        )
        assertTrue(CategoriaRepository.validarSalvamentoFinal(comDuasCategorias).isFailure)
    }

    @Test
    fun selecaoVozMasculinaEFeminina_diferenciaPitchEVozesCorretamente() {
        assertEquals("pt-BR-AntonioNeural", DirectEdgeTtsService.VOICE_MALE)
        assertEquals("pt-BR-FranciscaNeural", DirectEdgeTtsService.VOICE_FEMALE)

        assertTrue(AudioPredefinidoHelper.identificarGeneroDaVozTts("pt-br-x-ptd-local", GeneroAudio.MALE))
        assertTrue(AudioPredefinidoHelper.identificarGeneroDaVozTts("pt-BR-Daniel-male", GeneroAudio.MALE))
        assertFalse(AudioPredefinidoHelper.identificarGeneroDaVozTts("pt-br-x-ptc-local", GeneroAudio.MALE))
        assertTrue(AudioPredefinidoHelper.identificarGeneroDaVozTts("pt-br-x-ptc-local", GeneroAudio.FEMALE))

        val pitchMasc = AudioPredefinidoHelper.obterPitchParaGenero(GeneroAudio.MALE)
        val pitchFem = AudioPredefinidoHelper.obterPitchParaGenero(GeneroAudio.FEMALE)
        assertTrue(pitchMasc in 0.78f..0.85f)
        assertTrue(pitchFem in 1.05f..1.15f)
        assertTrue(pitchMasc < pitchFem)
    }

    @Test
    fun amplificacaoDspAudio_aumentaVolumeSemClipping() {
        val samples = shortArrayOf(2000, -2000, 5000, -5000, 15000)
        val originalPrimeiro = samples[0].toInt()
        val maxOut = AudioDspHelper.processarBufferPcmComGanhoECompressor(samples, samples.size, 2.2f)

        assertTrue(samples[0] > originalPrimeiro)
        assertTrue(maxOut > 0f)
        assertTrue(samples.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }

    @Test
    fun avatarCropViewport_dimensaoFixa240x240() {
        assertEquals(240, AvatarCropView.OUTPUT_SIZE_PX)
    }

    @Test
    fun acoesPredefinidas_estritamenteSomenteLeituraEImutaveis() {
        val categoriasPadrao = CategoriaRepository.getCategoriasPadrao()
        val acaoPredefinida = categoriasPadrao.first().botoes.first() // "Feliz" (b1)

        assertTrue(acaoPredefinida.isPredefined)
        assertFalse(acaoPredefinida.isCustom)
        assertTrue(CategoriaRepository.isAcaoPredefinida(acaoPredefinida))

        // Tentativas de mutação de título, asset visual ou áudio em ação pré-definida devem falhar
        assertTrue(CategoriaRepository.editActionTitle(acaoPredefinida, "Outro Título").isFailure)
        assertTrue(CategoriaRepository.updateActionAsset(acaoPredefinida, "/data/user/0/custom.jpg").isFailure)
        assertTrue(CategoriaRepository.updateActionAudio(acaoPredefinida, "/data/user/0/custom.wav").isFailure)
        assertTrue(
            CategoriaRepository.updateAction(
                acaoPredefinida,
                novoTitulo = "Alterado",
                novaImagemUri = "/data/user/0/custom.jpg",
                novoAudioUri = "/data/user/0/custom.wav"
            ).isFailure
        )

        // Blindagem restaura metadados canônicos caso haja tentativa de corrupção em memória
        val corrompida = acaoPredefinida.copy(
            rotulo = "Título Indevido",
            localPath = "/cache/invalido.jpg",
            imagem_id = DesenhoBotao.FELIZ.id
        )
        val blindada = CategoriaRepository.blindarAcaoPredefinida(corrompida)
        assertEquals("Feliz", blindada.rotulo)
        assertNull(blindada.localPath)
        assertTrue(blindada.isPredefined)
        assertFalse(blindada.isCustom)
    }

    @Test
    fun acoesPersonalizadas_permitemCrudCompletoEPersistenciaDeAtributos() {
        val acaoCustom = CategoriaItem(
            id = "btn_custom_101",
            rotulo = "Meu Brinquedo",
            significado = "Meu Brinquedo",
            isPredefined = false,
            isCustom = true
        )

        assertFalse(CategoriaRepository.isAcaoPredefinida(acaoCustom))

        val resultadoMutacao = CategoriaRepository.updateAction(
            action = acaoCustom,
            novoTitulo = "Brinquedo Favorito",
            novaImagemUri = "/files/botoes_imagens/asset_btn_custom_101_1700000000.jpg",
            novoAudioUri = "/files/botoes_audios/asset_btn_custom_101_1700000000.wav"
        )

        assertTrue(resultadoMutacao.isSuccess)
        val atualizada = resultadoMutacao.getOrThrow()
        assertEquals("Brinquedo Favorito", atualizada.rotulo)
        assertEquals("/files/botoes_imagens/asset_btn_custom_101_1700000000.jpg", atualizada.localPath)
        assertEquals("/files/botoes_imagens/asset_btn_custom_101_1700000000.jpg", atualizada.imageUri)
        assertEquals("/files/botoes_audios/asset_btn_custom_101_1700000000.wav", atualizada.audioPath)
        assertFalse(atualizada.isPredefined)
        assertTrue(atualizada.isCustom)
    }

    @Test
    fun primeiroBotaoPersonalizado_indexZero_semprePermiteEdicaoComIconeLapis() {
        val listaPersonalizados = listOf(
            CategoriaItem(id = "1", rotulo = "Primeiro Personalizado", significado = "Primeiro Personalizado", ordem = 0, isPredefined = false, isCustom = true),
            CategoriaItem(id = "default", rotulo = "Botão Default Custom", significado = "Botão Default Custom", ordem = 0, isPredefined = false, isCustom = true),
            CategoriaItem(id = "btn_0", rotulo = "Ação Zero", significado = "Ação Zero", ordem = 0, isPredefined = false, isCustom = true),
            CategoriaItem(id = "b1", rotulo = "Título Customizado no Slot 1", significado = "Título Customizado no Slot 1", ordem = 0, isPredefined = false, isCustom = true)
        )

        for ((index, item) in listaPersonalizados.withIndex()) {
            assertFalse("Item customizado no index=$index (id=${item.id}) não pode ser bloqueado como pré-definido", CategoriaRepository.isAcaoPredefinida(item))
            assertTrue(CategoriaRepository.editActionTitle(item, "Novo Título $index").isSuccess)
        }
    }
}
