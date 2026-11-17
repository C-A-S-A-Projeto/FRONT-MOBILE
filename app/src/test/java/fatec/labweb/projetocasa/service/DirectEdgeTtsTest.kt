package fatec.labweb.projetocasa.service

import fatec.labweb.projetocasa.model.GeneroAudio
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DirectEdgeTtsTest {

    @Test
    fun testSynthesizeFemaleAndMale() {
        val tempFemale = File.createTempFile("edge_test_f_", ".mp3")
        val tempMale = File.createTempFile("edge_test_m_", ".mp3")

        try {
            val femaleOk = DirectEdgeTtsService.sintetizarParaArquivo(
                texto = "Banheiro",
                genero = GeneroAudio.FEMALE,
                arquivoDestino = tempFemale,
                timeoutSegundos = 15
            )
            println("Female test result: ok=$femaleOk size=${tempFemale.length()}")
            assertTrue("Female audio should be generated and have bytes", femaleOk && tempFemale.length() > 500)

            val maleOk = DirectEdgeTtsService.sintetizarParaArquivo(
                texto = "Banheiro",
                genero = GeneroAudio.MALE,
                arquivoDestino = tempMale,
                timeoutSegundos = 15
            )
            println("Male test result: ok=$maleOk size=${tempMale.length()}")
            assertTrue("Male audio should be generated and have bytes", maleOk && tempMale.length() > 500)
        } finally {
            tempFemale.delete()
            tempMale.delete()
        }
    }
}
