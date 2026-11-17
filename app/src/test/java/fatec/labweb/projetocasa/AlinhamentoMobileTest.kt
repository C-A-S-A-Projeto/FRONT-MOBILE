package fatec.labweb.projetocasa

import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.model.Categoria
import fatec.labweb.projetocasa.model.CategoriaItem
import fatec.labweb.projetocasa.model.DadosDispositivo
import fatec.labweb.projetocasa.model.DesenhoBotao
import fatec.labweb.projetocasa.repository.CategoriaRepository
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
    fun cardinalidadeExata_validaLimitesDeTresCategoriasECincoAcoesComMensagensExatas() {
        val padrao = CategoriaRepository.getCategoriasPadrao()
        assertEquals(3, padrao.size)
        padrao.forEach { cat ->
            assertEquals(5, cat.botoes.size)
        }
        assertTrue(CategoriaRepository.validarCardinalidadeCompleta(padrao).isSuccess)

        // 1. Limite Máximo de Categorias (> 3)
        val resAdd4Categoria = CategoriaRepository.validarAdicaoCategoria(3)
        assertTrue(resAdd4Categoria.isFailure)
        assertEquals(
            "Você não pode adicionar mais do que 3 categorias.",
            resAdd4Categoria.exceptionOrNull()?.message
        )

        // 2. Limite Mínimo de Categorias (< 3)
        val resRemocaoCom3 = CategoriaRepository.validarRemocaoCategoria(3)
        assertTrue(resRemocaoCom3.isFailure)
        assertEquals(
            "Você não pode ter menos do que 3 categorias. É obrigatório manter exatamente 3 categorias.",
            resRemocaoCom3.exceptionOrNull()?.message
        )

        val menosDe3Categorias = padrao.take(2)
        val resSubmitMenosDe3 = CategoriaRepository.validarCardinalidadeCompleta(menosDe3Categorias)
        assertTrue(resSubmitMenosDe3.isFailure)
        assertEquals(
            "Você não pode ter menos do que 3 categorias. É obrigatório manter exatamente 3 categorias.",
            resSubmitMenosDe3.exceptionOrNull()?.message
        )

        // 3. Limite Máximo de Ações por Categoria (> 5)
        val resAdd6Acao = CategoriaRepository.validarAdicaoAcao(5)
        assertTrue(resAdd6Acao.isFailure)
        assertEquals(
            "Você não pode adicionar mais do que 5 ações nesta categoria.",
            resAdd6Acao.exceptionOrNull()?.message
        )

        // 4. Limite Mínimo de Ações por Categoria (< 5)
        val resRemoverCom5 = CategoriaRepository.validarRemocaoAcao(5)
        assertTrue(resRemoverCom5.isFailure)
        assertEquals(
            "Cada categoria precisa ter exatamente 5 ações. Não é permitido salvar com menos de 5 ações.",
            resRemoverCom5.exceptionOrNull()?.message
        )

        val categoriaIncompleta = padrao.mapIndexed { idx, c ->
            if (idx == 0) c.copy(botoes = c.botoes.take(4).toMutableList()) else c
        }
        val resSubmitAcoesIncompletas = CategoriaRepository.validarCardinalidadeCompleta(categoriaIncompleta)
        assertTrue(resSubmitAcoesIncompletas.isFailure)
        assertEquals(
            "Cada categoria precisa ter exatamente 5 ações. Não é permitido salvar com menos de 5 ações.",
            resSubmitAcoesIncompletas.exceptionOrNull()?.message
        )
    }
}
