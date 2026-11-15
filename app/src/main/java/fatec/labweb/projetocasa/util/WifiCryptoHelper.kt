package fatec.labweb.projetocasa.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilitário de criptografia AES-256-GCM para credenciais Wi-Fi do ESP32 (Firmware v11).
 * Formato do texto cifrado: Base64( IV[12 bytes] + Ciphertext[N bytes] + TAG[16 bytes] )
 */
object WifiCryptoHelper {

    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    private fun b64Encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun b64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    /**
     * Gera uma nova chave criptográfica AES-256 de 32 bytes codificada em Base64.
     */
    fun gerarChaveAes256(): String {
        val chaveBytes = ByteArray(AES_KEY_LENGTH_BYTES)
        secureRandom.nextBytes(chaveBytes)
        return b64Encode(chaveBytes)
    }

    /**
     * Valida se a string Base64 contém exatamente 32 bytes (256 bits).
     */
    fun validarChaveB64(chaveB64: String): ByteArray {
        val bytes = try {
            b64Decode(chaveB64.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Chave não é um Base64 válido.", e)
        }
        if (bytes.size != AES_KEY_LENGTH_BYTES) {
            throw IllegalArgumentException("Chave AES deve ter exatamente 32 bytes (recebido: ${bytes.size}).")
        }
        return bytes
    }

    /**
     * Cifra a senha utilizando AES-256-GCM com IV aleatório de 12 bytes.
     * Retorna Base64 de (IV + Ciphertext + TAG).
     * Se a senha for vazia, retorna "" (representando rede aberta).
     */
    fun cifrarSenhaWifi(senha: String, chaveB64: String): String {
        if (senha.isEmpty()) return ""

        val chaveBytes = validarChaveB64(chaveB64)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val keySpec = SecretKeySpec(chaveBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val textoClaro = senha.toByteArray(Charsets.UTF_8)
        val ctTag = cipher.doFinal(textoClaro)

        val resultado = ByteArray(iv.size + ctTag.size)
        System.arraycopy(iv, 0, resultado, 0, iv.size)
        System.arraycopy(ctTag, 0, resultado, iv.size, ctTag.size)

        return b64Encode(resultado)
    }

    /**
     * Decifra a string Base64 (IV[12] + Ciphertext + TAG[16]) usando AES-256-GCM.
     */
    fun decifrarSenhaWifi(cifradaB64: String, chaveB64: String): String {
        if (cifradaB64.isEmpty()) return ""

        val chaveBytes = validarChaveB64(chaveB64)
        val raw = try {
            b64Decode(cifradaB64.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Texto cifrado não é Base64 válido.", e)
        }

        if (raw.size < (GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8))) {
            throw IllegalArgumentException("Tamanho do texto cifrado menor que o mínimo necessário (IV + TAG).")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(raw, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val ctTagLen = raw.size - GCM_IV_LENGTH_BYTES
        val ctTag = ByteArray(ctTagLen)
        System.arraycopy(raw, GCM_IV_LENGTH_BYTES, ctTag, 0, ctTagLen)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val keySpec = SecretKeySpec(chaveBytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decifrado = cipher.doFinal(ctTag)
        return String(decifrado, Charsets.UTF_8)
    }
}
