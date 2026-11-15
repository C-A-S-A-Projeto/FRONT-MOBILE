package fatec.labweb.projetocasa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiCryptoHelperTest {

    @Test
    fun gerarChaveAes256_geraChaveValida32Bytes() {
        val chaveB64 = WifiCryptoHelper.gerarChaveAes256()
        val bytes = WifiCryptoHelper.validarChaveB64(chaveB64)
        assertEquals(32, bytes.size)
    }

    @Test
    fun cifrarEDecifrarSenha_mantemValorOriginal() {
        val chaveB64 = WifiCryptoHelper.gerarChaveAes256()
        val senhaOriginal = "SenhaUltraSecreta@123"

        val cifrada = WifiCryptoHelper.cifrarSenhaWifi(senhaOriginal, chaveB64)
        assertTrue(cifrada.isNotBlank())
        assertNotEquals(senhaOriginal, cifrada)

        val decifrada = WifiCryptoHelper.decifrarSenhaWifi(cifrada, chaveB64)
        assertEquals(senhaOriginal, decifrada)
    }

    @Test
    fun redeAberta_senhaVaziaRetornaStringVazia() {
        val chaveB64 = WifiCryptoHelper.gerarChaveAes256()
        val cifrada = WifiCryptoHelper.cifrarSenhaWifi("", chaveB64)
        assertEquals("", cifrada)

        val decifrada = WifiCryptoHelper.decifrarSenhaWifi("", chaveB64)
        assertEquals("", decifrada)
    }

    @Test(expected = Exception::class)
    fun decifrarComChaveErrada_lancaExcecao() {
        val chave1 = WifiCryptoHelper.gerarChaveAes256()
        val chave2 = WifiCryptoHelper.gerarChaveAes256()

        val cifrada = WifiCryptoHelper.cifrarSenhaWifi("MinhaSenhaSegura", chave1)
        WifiCryptoHelper.decifrarSenhaWifi(cifrada, chave2)
    }
}
