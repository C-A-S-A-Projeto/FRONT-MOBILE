package fatec.labweb.projetocasa.util

import fatec.labweb.projetocasa.service.EdgeDrm
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDrmTest {

    @Test
    fun testGenerateSecMsGecV2MatchesOfficialPythonImplementation() {
        val expected = "4357F74BF065683A-0B133EFB4CF557DA6C29390ED604E3354E4099F60B206CB8734E912B84AA8284"
        val actual = EdgeDrm.generateSecMsGecV2(1731800000.0)
        assertEquals(expected, actual)
    }
}
