package com.example.crypto

import java.security.SecureRandom

/**
 * Galois Field 256 arithmetic for Shamir Secret Sharing (polynomial modulus 0x11D).
 * Matches ZYLO VEIL v3 Python implementation.
 */
class GF256 {
    val exp = IntArray(512)
    val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        exp[255] = exp[0]
        for (i in 255 until 512) {
            exp[i] = exp[i - 255]
        }
    }

    fun add(a: Int, b: Int): Int = a xor b
    fun sub(a: Int, b: Int): Int = a xor b

    fun mul(a: Int, b: Int): Int {
        val ua = a and 0xFF
        val ub = b and 0xFF
        if (ua == 0 || ub == 0) return 0
        return exp[log[ua] + log[ub]]
    }

    fun div(a: Int, b: Int): Int {
        val ua = a and 0xFF
        val ub = b and 0xFF
        if (ub == 0) throw ArithmeticException("Division by zero in GF(256)")
        if (ua == 0) return 0
        return exp[(log[ua] - log[ub] + 255) % 255]
    }

    companion object {
        val INSTANCE = GF256()

        /**
         * Split secret into N shares where K are needed for reconstruction.
         * Returns list of Pair(share_index, share_bytes) where index is 1..N.
         */
        fun shamirSplit(secret: ByteArray, k: Int, n: Int): List<Pair<Int, ByteArray>> {
            require(k in 1..n) { "K must be between 1 and N" }
            require(n in 1..255) { "N must be between 1 and 255" }

            val gf = INSTANCE
            val random = SecureRandom()
            val shares = Array(n) { ByteArray(secret.size) }

            for (byteIdx in secret.indices) {
                val secretByte = secret[byteIdx].toInt() and 0xFF
                val coeffs = IntArray(k)
                coeffs[0] = secretByte
                for (c in 1 until k) {
                    coeffs[c] = random.nextInt(256)
                }

                for (i in 0 until n) {
                    val x = i + 1
                    var y = 0
                    for (c in (k - 1) downTo 0) {
                        y = gf.add(gf.mul(y, x), coeffs[c])
                    }
                    shares[i][byteIdx] = y.toByte()
                }
            }

            return List(n) { i -> Pair(i + 1, shares[i]) }
        }

        /**
         * Recover secret from at least K shares using Lagrange interpolation at x = 0.
         */
        fun shamirRecover(shares: List<Pair<Int, ByteArray>>, k: Int): ByteArray {
            require(shares.size >= k) { "Need at least $k shares, got ${shares.size}" }
            val gf = INSTANCE
            val activeShares = shares.take(k)
            val xs = activeShares.map { it.first }
            val ysList = activeShares.map { it.second }
            val length = ysList[0].size

            for (ys in ysList) {
                if (ys.size != length) {
                    throw IllegalArgumentException("Share length mismatch")
                }
            }

            val secret = ByteArray(length)
            for (i in 0 until length) {
                var result = 0
                for (j in 0 until k) {
                    val xj = xs[j]
                    val yj = ysList[j][i].toInt() and 0xFF
                    var prod = 1
                    for (m in 0 until k) {
                        if (j == m) continue
                        val xm = xs[m]
                        val nom = xm
                        val denom = gf.sub(xm, xj)
                        if (denom == 0) {
                            throw IllegalArgumentException("Duplicate share index: $xm")
                        }
                        val term = gf.div(nom, denom)
                        prod = gf.mul(prod, term)
                    }
                    result = gf.add(result, gf.mul(yj, prod))
                }
                secret[i] = result.toByte()
            }
            return secret
        }
    }
}
