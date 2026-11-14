package fatec.labweb.projetocasa

import fatec.labweb.projetocasa.model.Acionamento
import fatec.labweb.projetocasa.model.DadosDispositivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
