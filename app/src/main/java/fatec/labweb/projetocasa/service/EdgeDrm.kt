package fatec.labweb.projetocasa.service

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementação pura em Kotlin do algoritmo Sec-MS-GEC v2 do Microsoft Edge TTS.
 * Permite que o app Android se conecte diretamente aos servidores da Microsoft
 * para sintetizar vozes neurais humanizadas (Francisca e Antônio) sem depender de nenhum servidor intermediário.
 */
object EdgeDrm {

    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
    private const val WIN_EPOCH = 11644473600L
    private const val S_TO_NS = 1_000_000_000.0

    private val SHA256_INITIAL = intArrayOf(
        0x6A09E667.toInt(), 0xBB67AE85.toInt(), 0x3C6EF372.toInt(), 0xA54FF53A.toInt(),
        0x510E527F.toInt(), 0x9B05688C.toInt(), 0x1F83D9AB.toInt(), 0x5BE0CD19.toInt()
    )

    private val SHA256_STATE = intArrayOf(
        0xFD9FBEA2.toInt(), 0x91243CE7.toInt(), 0xBE499094.toInt(), 0xCD2E1E0A.toInt(),
        0x6C006F49.toInt(), 0x04BD35AB.toInt(), 0xFEB0A7E4.toInt(), 0x03EFFBD2.toInt()
    )

    private val SHA256_SCHEDULE = intArrayOf(
        0xEB24BF9E.toInt(), 0x1558691F.toInt(), 0xA0C09EE2.toInt(), 0x0CFA32BA.toInt(),
        0xA622EE8C.toInt(), 0xD1DD9491.toInt(), 0xC0DD25A0.toInt(), 0xF9E70AB6.toInt(),
        0xF0C31573.toInt(), 0x7058CE4E.toInt(), 0x7188E606.toInt(), 0x00E5AB33.toInt(),
        0xD258B060.toInt(), 0x72766564.toInt()
    )

    private val SHA256_ROUND_CONSTANTS = intArrayOf(
        0x428A2F98.toInt(), 0x71374491.toInt(), 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(),
        0x3956C25B.toInt(), 0x59F111F1.toInt(), 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(),
        0xD807AA98.toInt(), 0x12835B01.toInt(), 0x243185BE.toInt(), 0x550C7DC3.toInt(),
        0x72BE5D74.toInt(), 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(),
        0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6.toInt(), 0x240CA1CC.toInt(),
        0x2DE92C6F.toInt(), 0x4A7484AA.toInt(), 0x5CB0A9DC.toInt(), 0x76F988DA.toInt(),
        0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(),
        0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351.toInt(), 0x14292967.toInt(),
        0x27B70A85.toInt(), 0x2E1B2138.toInt(), 0x4D2C6DFC.toInt(), 0x53380D13.toInt(),
        0x650A7354.toInt(), 0x766A0ABB.toInt(), 0x81C2C92E.toInt(), 0x92722C85.toInt(),
        0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(),
        0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070.toInt(),
        0x19A4C116.toInt(), 0x1E376C08.toInt(), 0x2748774C.toInt(), 0x34B0BCB5.toInt(),
        0x391C0CB3.toInt(), 0x4ED8AA4A.toInt(), 0x5B9CCA4F.toInt(), 0x682E6FF3.toInt(),
        0x748F82EE.toInt(), 0x78A5636F.toInt(), 0x84C87814.toInt(), 0x8CC70208.toInt(),
        0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt()
    )

    private val SHA256_BLOCK_CONSTANTS = IntArray(64).apply {
        System.arraycopy(SHA256_ROUND_CONSTANTS, 0, this, 0, 14)
        this[14] = 0xC9DC06A7.toInt()
        System.arraycopy(SHA256_ROUND_CONSTANTS, 15, this, 15, 49)
    }

    private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80
    private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0
    private const val SHA256_ADJUSTMENT = 0xC9DC06A7.toInt()

    private val CS64_COEFFICIENTS = arrayOf(
        0xE82E91B1.toInt() to 0xB32700D0.toInt(),
        0x44DE24C6.toInt() to 0x88DFF51D.toInt(),
        0x3BEB65BC.toInt() to 0x29514038.toInt(),
        0xC89C5933.toInt() to 0xB328CBDF.toInt(),
        0x8D1EC211.toInt() to 0xD405B4D5.toInt(),
        0x314EF5D5.toInt() to 0x26E55F3C.toInt(),
        0xE92491B1.toInt() to 0x607801C6.toInt(),
        0xF0A5B9D5.toInt() to 0x82B2C1FB.toInt(),
        0x06064CF3.toInt() to 0xFA51A3DA.toInt(),
        0xDA79FA60.toInt() to 0x207CC464.toInt(),
        0xBED20560.toInt() to 0x3C3C74F3.toInt(),
        0x89F9C9F8.toInt() to 0xD2D14B61.toInt(),
        0x35FA38C6.toInt() to 0xBB04A2C1.toInt(),
        0xCB49E3C6.toInt() to 0xC5626167.toInt()
    )

    private const val CS64_FACTOR = 0x82893933.toInt()
    private const val CS64_ADJUSTMENT0 = 0xF2F90B1A.toInt()
    private const val CS64_ADJUSTMENT1 = 0x0B1A0000

    private fun rol16(v: Int): Int = (v shl 16) or (v ushr 16)

    private fun sigma0(x: Int): Int =
        (x ushr 7) xor (x shl 25) xor (x ushr 18) xor (x shl 14) xor (x ushr 3)

    private fun sigma1(x: Int): Int =
        (x ushr 17) xor (x shl 15) xor (x ushr 19) xor (x shl 13) xor (x ushr 10)

    private fun sha256Rounds(state: IntArray, words: IntArray, firstRound: Int = 0): IntArray {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        val constants = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS

        val aligned = minOf(64, ((firstRound + 7) / 8) * 8)
        for (i in firstRound until aligned) {
            val rotE = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + rotE + ch + constants[i] + words[i]
            val rotA = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = rotA + maj
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }

        for (i in aligned until 64) {
            val rotE = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + rotE + ch + constants[i] + words[i]
            val rotA = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = rotA + maj
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }

        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun expandSha256Schedule(words: IntArray, firstWord: Int = 16) {
        for (i in firstWord until 64) {
            val x = words[i - 2]
            val y = words[i - 15]
            val s1 = (x ushr 17 or (x shl 15)) xor (x ushr 19 or (x shl 13)) xor (x ushr 10)
            val s0 = (y ushr 7 or (y shl 25)) xor (y ushr 18 or (y shl 14)) xor (y ushr 3)
            words[i] = s1 + words[i - 7] + s0 + words[i - 16]
        }
    }

    private fun sha256Init(data7: ByteArray): IntArray {
        val words = IntArray(64)
        // words[0..13] = 0
        val b0 = data7[0].toInt() and 0xFF
        val b1 = data7[1].toInt() and 0xFF
        val b2 = data7[2].toInt() and 0xFF
        words[14] = (b0 shl 16) or (b1 shl 8) or b2

        val b3 = data7[3].toInt() and 0xFF
        val b4 = data7[4].toInt() and 0xFF
        val b5 = data7[5].toInt() and 0xFF
        val b6 = data7[6].toInt() and 0xFF
        words[15] = (b3 shl 24) or (b4 shl 16) or (b5 shl 8) or b6

        for (i in 16 until 30) {
            var value = SHA256_SCHEDULE[i - 16]
            if (i - 2 >= 14) {
                value += if (i == 16) (sigma1(words[i - 2]) xor SHA256_SIGMA1_ADJUSTMENT) else sigma1(words[i - 2])
            }
            if (i - 7 >= 14) {
                value += words[i - 7]
            }
            if (i - 15 >= 14) {
                value += if (i == 29) (sigma0(words[i - 15]) xor SHA256_SIGMA0_ADJUSTMENT) else sigma0(words[i - 15])
            }
            words[i] = value
        }

        words[30] = sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] + SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]
        expandSha256Schedule(words, 31)

        val working = sha256Rounds(SHA256_STATE, words, 14)
        return IntArray(8) { idx -> SHA256_INITIAL[idx] + working[idx] }
    }

    private fun sha256Digest(data: ByteArray): ByteArray {
        require(data.size >= 7) { "Token salt must contain at least seven bytes" }
        val length = 57 + data.size
        val padZeroCount = (55 - length).let { ((it % 64) + 64) % 64 }
        val bb = ByteBuffer.allocate(data.size - 7 + 1 + padZeroCount + 8)
        bb.put(data, 7, data.size - 7)
        bb.put(0x80.toByte())
        for (i in 0 until padZeroCount) bb.put(0.toByte())
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putLong((length.toLong()) * 8L)
        val padded = bb.array()

        var state = sha256Init(data.copyOfRange(0, 7))
        for (offset in padded.indices step 64) {
            val words = IntArray(64)
            val chunkBuf = ByteBuffer.wrap(padded, offset, 64).order(ByteOrder.BIG_ENDIAN)
            for (w in 0 until 16) {
                words[w] = chunkBuf.int
            }
            expandSha256Schedule(words)
            val working = sha256Rounds(state, words)
            state = IntArray(8) { idx -> state[idx] + working[idx] }
        }

        val outBuf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        for (s in state) outBuf.putInt(s)
        return outBuf.array()
    }

    private fun cs64Digest(data: ByteArray, digest: ByteArray): Pair<Int, Int> {
        val digestBuf = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
        var keyA = digestBuf.int or 1
        var keyB = digestBuf.int or 1

        val factorA = keyA * CS64_FACTOR
        val factorB = keyB * CS64_FACTOR

        val terms = mutableListOf<Int>()
        val offsets = mutableListOf<Int>()

        for (i in CS64_COEFFICIENTS.indices) {
            val (coeff, off) = CS64_COEFFICIENTS[i]
            val f = if (i % 2 == 0) factorA else factorB
            terms.add(coeff * f)
            offsets.add(off)
        }

        val prepData = ByteArray(data.size + 1).apply {
            this[0] = 0
            System.arraycopy(data, 0, this, 1, data.size)
        }

        val padLen = (4 - (prepData.size % 4)) % 4
        val padded = ByteArray(prepData.size + padLen).apply {
            System.arraycopy(prepData, 0, this, 0, prepData.size)
        }

        val wordsList = mutableListOf<Int>()
        val pBuf = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
        while (pBuf.hasRemaining()) {
            wordsList.add(pBuf.int)
        }
        if (wordsList.size % 2 != 0) {
            wordsList.add(0)
        }

        for (i in wordsList.indices) {
            val w = wordsList[i]
            var term = w * (if (i % 2 == 0) keyA else keyB)
            var offset = rol16(w) * (if (i % 2 == 0) 0xEF0569FB.toInt() else 0xC31713DB.toInt())
            if (i == 0) {
                term += CS64_ADJUSTMENT0 * factorA
                offset += CS64_ADJUSTMENT1
            }
            terms.add(term)
            offsets.add(offset)
        }

        var swapAcc = 0
        var swapLast = 0
        var state = 0
        var acc = 0

        for (i in 0 until terms.size step 2) {
            val x = terms[i]
            val y = terms[i + 1]

            var t = rol16(offsets[i] + x)
            var q = t * 0x76F50F34.toInt()
            swapAcc += rol16(q) * 0xC3EFEA97.toInt() + q

            t = rol16(offsets[i + 1] + y)
            q = t * 0x1881CE3C
            swapLast = rol16(q) * 0x35BD1EC9 + q
            swapAcc += swapLast

            state = state * keyA + x
            state = rol16(state) * 0xCF98B111.toInt()
            state = rol16(state) * 0x87085B9F.toInt()
            state = rol16(state) * 0x12CEB96D
            state = rol16(state) * 0x257E1D83
            acc += state

            t = state * keyB + y
            t = rol16(t) * 0xA27416F5.toInt()
            t = rol16(t) * 0xD38396FF.toInt()
            state = rol16(rol16(t) * 0x7C932B89.toInt()) * 0xBFA49F69.toInt()
            acc += state
        }

        return Pair(swapLast xor state, swapAcc xor acc)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format(java.util.Locale.US, "%02X", b))
        }
        return sb.toString()
    }

    fun generateSecMsGecV2(unixTimestamp: Double? = null): String {
        val ts = unixTimestamp ?: (System.currentTimeMillis() / 1000.0)
        var ticks = ts + WIN_EPOCH
        ticks -= ticks % 300
        ticks *= S_TO_NS / 100.0
        val strToHash = String.format(java.util.Locale.US, "%.0f%s", ticks, TRUSTED_CLIENT_TOKEN).toByteArray(Charsets.US_ASCII)

        val digest = sha256Digest(strToHash)
        val (csA, csB) = cs64Digest(strToHash, digest)

        val csBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        csBuf.putInt(csA)
        csBuf.putInt(csB)
        val cs64Hex = bytesToHex(csBuf.array())
        val sha256Hex = bytesToHex(digest)

        return "$cs64Hex-$sha256Hex"
    }
}
