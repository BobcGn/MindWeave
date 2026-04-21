package org.example.mindweave.util

private val SHA256_INITIAL_HASHES = intArrayOf(
    0x6A09E667,
    0xBB67AE85.toInt(),
    0x3C6EF372,
    0xA54FF53A.toInt(),
    0x510E527F,
    0x9B05688C.toInt(),
    0x1F83D9AB,
    0x5BE0CD19,
)

private val SHA256_ROUND_CONSTANTS = intArrayOf(
    0x428A2F98,
    0x71374491,
    0xB5C0FBCF.toInt(),
    0xE9B5DBA5.toInt(),
    0x3956C25B,
    0x59F111F1,
    0x923F82A4.toInt(),
    0xAB1C5ED5.toInt(),
    0xD807AA98.toInt(),
    0x12835B01,
    0x243185BE,
    0x550C7DC3,
    0x72BE5D74,
    0x80DEB1FE.toInt(),
    0x9BDC06A7.toInt(),
    0xC19BF174.toInt(),
    0xE49B69C1.toInt(),
    0xEFBE4786.toInt(),
    0x0FC19DC6,
    0x240CA1CC,
    0x2DE92C6F,
    0x4A7484AA,
    0x5CB0A9DC,
    0x76F988DA.toInt(),
    0x983E5152.toInt(),
    0xA831C66D.toInt(),
    0xB00327C8.toInt(),
    0xBF597FC7.toInt(),
    0xC6E00BF3.toInt(),
    0xD5A79147.toInt(),
    0x06CA6351,
    0x14292967,
    0x27B70A85,
    0x2E1B2138,
    0x4D2C6DFC,
    0x53380D13,
    0x650A7354,
    0x766A0ABB,
    0x81C2C92E.toInt(),
    0x92722C85.toInt(),
    0xA2BFE8A1.toInt(),
    0xA81A664B.toInt(),
    0xC24B8B70.toInt(),
    0xC76C51A3.toInt(),
    0xD192E819.toInt(),
    0xD6990624.toInt(),
    0xF40E3585.toInt(),
    0x106AA070,
    0x19A4C116,
    0x1E376C08,
    0x2748774C,
    0x34B0BCB5.toInt(),
    0x391C0CB3.toInt(),
    0x4ED8AA4A,
    0x5B9CCA4F,
    0x682E6FF3,
    0x748F82EE.toInt(),
    0x78A5636F,
    0x84C87814.toInt(),
    0x8CC70208.toInt(),
    0x90BEFFFA.toInt(),
    0xA4506CEB.toInt(),
    0xBEF9A3F7.toInt(),
    0xC67178F2.toInt(),
)

fun hashPassword(value: String): String {
    val message = value.encodeToByteArray()
    val padded = padSha256(message)
    val hash = SHA256_INITIAL_HASHES.copyOf()
    val schedule = IntArray(64)

    var offset = 0
    while (offset < padded.size) {
        var index = 0
        while (index < 16) {
            val base = offset + index * 4
            schedule[index] = (
                ((padded[base].toInt() and 0xFF) shl 24) or
                    ((padded[base + 1].toInt() and 0xFF) shl 16) or
                    ((padded[base + 2].toInt() and 0xFF) shl 8) or
                    (padded[base + 3].toInt() and 0xFF)
                )
            index++
        }
        while (index < 64) {
            val s0 = schedule[index - 15].rotateRight(7) xor
                schedule[index - 15].rotateRight(18) xor
                (schedule[index - 15] ushr 3)
            val s1 = schedule[index - 2].rotateRight(17) xor
                schedule[index - 2].rotateRight(19) xor
                (schedule[index - 2] ushr 10)
            schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
            index++
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]

        index = 0
        while (index < 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + SHA256_ROUND_CONSTANTS[index] + schedule[index]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
            index++
        }

        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h

        offset += 64
    }

    return buildString(hash.size * 8) {
        hash.forEach { value32 ->
            append(value32.toUInt().toString(16).padStart(8, '0'))
        }
    }
}

private fun padSha256(message: ByteArray): ByteArray {
    val messageLength = message.size
    val bitLength = messageLength.toLong() * 8L
    val paddingLength = ((56 - ((messageLength + 1) % 64)) + 64) % 64
    val padded = ByteArray(messageLength + 1 + paddingLength + 8)
    message.copyInto(padded, endIndex = messageLength)
    padded[messageLength] = 0x80.toByte()

    for (i in 0 until 8) {
        padded[padded.lastIndex - i] = ((bitLength ushr (i * 8)) and 0xFF).toByte()
    }
    return padded
}
