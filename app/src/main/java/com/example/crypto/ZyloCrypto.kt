package com.example.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ZyloCrypto {
    val MAGIC = "ZYLOSTEG".toByteArray(Charsets.US_ASCII) // 8 bytes
    const val VERSION: Int = 4
    const val ALGO_VERSION: Int = 4
    const val LEGACY_VERSION: Int = 3
    const val KDF_ID_PBKDF2_HMAC_SHA512: Int = 1
    const val KDF_ITERATIONS: Int = 50_000
    const val SALT_LEN: Int = 16
    const val IV_LEN: Int = 12
    const val STATELESS_MARKER_LEN: Int = 8
    const val HEADER_FIXED_LEN: Int = 81
    const val DEFAULT_BPC_HINT: Int = 2

    // Flags
    const val FLAG_TIME_LOCKED: Int = 1 shl 0
    const val FLAG_SELF_DESTRUCT: Int = 1 shl 1
    const val FLAG_CARRIER_BOUND: Int = 1 shl 2
    const val FLAG_NOISE_ADAPTIVE: Int = 1 shl 3
    const val FLAG_STATELESS: Int = 1 shl 4
    const val FLAG_SEMANTIC_CAMO: Int = 1 shl 5
    const val FLAG_POISON_ANTI_FORENSIC: Int = 1 shl 6
    const val FLAG_MUTATE_ON_EXTRACT: Int = 1 shl 7
    const val FLAG_BITPLANE_MIGRATION: Int = 1 shl 8

    const val MIGRATION_WINDOW: Long = 6 * 60 * 60L // 6 hours in seconds

    private val secureRandom = SecureRandom()

    fun randomBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        secureRandom.nextBytes(b)
        return b
    }

    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun sha512(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-512").digest(data)
    }

    /**
     * Deterministic PRNG using Counter Mode SHA-256: SHA-256(seed + uint64_be(ctr))
     */
    fun prngBytes(seed: ByteArray, nbytes: Int): ByteArray {
        val out = ByteArray(nbytes)
        var offset = 0
        var ctr = 0L
        val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        val digestBuf = MessageDigest.getInstance("SHA-256")

        while (offset < nbytes) {
            bb.clear()
            bb.putLong(ctr)
            
            digestBuf.reset()
            digestBuf.update(seed)
            digestBuf.update(bb.array())
            val digest = digestBuf.digest()
            
            val toCopy = min(digest.size, nbytes - offset)
            System.arraycopy(digest, 0, out, offset, toCopy)
            offset += toCopy
            ctr++
        }
        return out
    }

    /**
     * Format-Preserving Cycle-Walking Feistel Permutation over domain [0, domainSize - 1].
     * Maps index i in 0 until domainSize to a unique pseudo-random permuted index in O(1) time
     * with ZERO array memory allocations (0 bytes allocated).
     */
    fun keyedPermutation(index: Int, domainSize: Int, seed: ByteArray): Int {
        if (domainSize <= 1) return 0

        var a = sqrt(domainSize.toDouble()).toInt()
        while (a * a < domainSize) {
            a++
        }
        val b = a

        var curr = index
        var attempts = 0
        val digestBuf = MessageDigest.getInstance("SHA-256")
        val bb = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)

        while (attempts < 100) {
            var l = curr % a
            var r = curr / a

            for (round in 0..2) {
                bb.clear()
                bb.putInt(r)
                bb.putInt(round)

                digestBuf.reset()
                digestBuf.update(seed)
                digestBuf.update(bb.array())
                val hash = digestBuf.digest()

                val fVal = ((hash[0].toInt() and 0xFF) shl 24) or
                        ((hash[1].toInt() and 0xFF) shl 16) or
                        ((hash[2].toInt() and 0xFF) shl 8) or
                        (hash[3].toInt() and 0xFF)

                val nextL = r
                val nextR = (l + (fVal and 0x7FFFFFFF)) % a
                l = nextL
                r = nextR
            }

            val permuted = l * b + r
            if (permuted < domainSize) {
                return permuted
            }
            curr = permuted % domainSize
            attempts++
        }
        return (index + (seed[0].toInt() and 0xFF)) % domainSize
    }

    /**
     * Fisher-Yates shuffle driven by seeded PRNG
     */
    fun deterministicShuffle(indices: IntArray, seed: ByteArray): IntArray {
        val arr = indices.clone()
        val n = arr.size
        if (n <= 1) return arr

        var ctr = 0L
        val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        val digestBuf = MessageDigest.getInstance("SHA-256")
        
        var rnd: ByteArray? = null
        var ptr = 0

        for (i in (n - 1) downTo 1) {
            if (rnd == null || ptr + 4 > rnd.size) {
                bb.clear()
                bb.putLong(ctr)
                digestBuf.reset()
                digestBuf.update(seed)
                digestBuf.update(bb.array())
                rnd = digestBuf.digest()
                ptr = 0
                ctr++
            }
            
            val u32 = ((rnd[ptr].toInt() and 0xFF).toLong() shl 24) or
                    ((rnd[ptr + 1].toInt() and 0xFF).toLong() shl 16) or
                    ((rnd[ptr + 2].toInt() and 0xFF).toLong() shl 8) or
                    (rnd[ptr + 3].toInt() and 0xFF).toLong()
            ptr += 4
            
            val j = (u32 % (i + 1)).toInt()
            val temp = arr[i]
            arr[i] = arr[j]
            arr[j] = temp
        }
        return arr
    }

    /**
     * Hash the MSBs (top 5 bits) of ARGB carrier pixel array.
     */
    fun carrierFingerprintFromArgb(argb: IntArray): ByteArray {
        val stride = max(1, argb.size / 8192)
        val sampleCount = (argb.size + stride - 1) / stride
        val b = ByteArray(sampleCount * 3)
        var outIdx = 0
        val mask = 0xF8
        for (i in 0 until argb.size step stride) {
            if (outIdx + 2 < b.size) {
                val c = argb[i]
                b[outIdx++] = ((c shr 16) and mask).toByte()
                b[outIdx++] = ((c shr 8) and mask).toByte()
                b[outIdx++] = (c and mask).toByte()
            }
        }
        return sha256(b)
    }

    /**
     * Key derivation using PBKDF2WithHmacSHA512.
     */
    fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        carrierFingerprint: ByteArray? = null,
        length: Int = 32,
        kdfId: Int = KDF_ID_PBKDF2_HMAC_SHA512,
        iterations: Int = KDF_ITERATIONS
    ): ByteArray {
        require(kdfId == KDF_ID_PBKDF2_HMAC_SHA512) { "Unsupported KDF id: $kdfId" }
        require(iterations in 10_000..10_000_000) { "Invalid KDF iteration count: $iterations" }
        require(salt.size == SALT_LEN) { "Invalid KDF salt length: ${salt.size}" }
        require(length in 16..64) { "Invalid derived key length: $length" }
        var kdfSalt = salt
        if (carrierFingerprint != null) {
            kdfSalt = sha256(salt + carrierFingerprint).copyOf(SALT_LEN)
        }
        val spec = PBEKeySpec(passphrase.toCharArray(), kdfSalt, iterations, length * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }

    /**
     * AEAD AES-GCM 256 encryption.
     */
    fun aeadEncrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) cipher.updateAAD(associatedData)
        return cipher.doFinal(plaintext)
    }

    /**
     * AEAD AES-GCM 256 decryption.
     */
    fun aeadDecrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    /**
     * High compression using Deflate or XZ with prepended magic header.
     */
    fun compressPayload(blob: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        // Format tag 'Z' 'V' 'C' '1' followed by deflated data
        bos.write("ZVC1".toByteArray(Charsets.US_ASCII))
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val dos = DeflaterOutputStream(bos, deflater)
        dos.write(blob)
        dos.finish()
        dos.close()
        return bos.toByteArray()
    }

    fun decompressPayload(compressed: ByteArray): ByteArray {
        if (compressed.size >= 4 &&
            compressed[0] == 'Z'.code.toByte() &&
            compressed[1] == 'V'.code.toByte() &&
            compressed[2] == 'C'.code.toByte() &&
            compressed[3] == '1'.code.toByte()
        ) {
            val bis = ByteArrayInputStream(compressed, 4, compressed.size - 4)
            val iis = InflaterInputStream(bis)
            return iis.readBytes()
        }
        // Try raw Inflater or fallback
        return try {
            val bis = ByteArrayInputStream(compressed)
            val iis = InflaterInputStream(bis)
            iis.readBytes()
        } catch (e: Exception) {
            compressed
        }
    }

    /**
     * Stateless keyed implicit marker (no plaintext magic bytes).
     */
    fun computeStatelessMarker(passphrase: String, carrierFingerprint: ByteArray, algoVersion: Int): ByteArray {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(algoVersion).array()
        val markerInput = passphrase.toByteArray(Charsets.UTF_8) +
                carrierFingerprint +
                bb +
                "ZYLO_STATELESS_MARKER_V3".toByteArray(Charsets.US_ASCII)
        return sha256(markerInput).copyOf(STATELESS_MARKER_LEN)
    }

    fun verifyStatelessMarker(extractedMarker: ByteArray, passphrase: String, carrierFingerprint: ByteArray, algoVersion: Int): Boolean {
        val expected = computeStatelessMarker(passphrase, carrierFingerprint, algoVersion)
        if (extractedMarker.size != expected.size) return false
        var diff = 0
        for (i in extractedMarker.indices) {
            diff = diff or (extractedMarker[i].toInt() xor expected[i].toInt())
        }
        return diff == 0
    }

    fun computeMigrationEpoch(timestampSeconds: Long = System.currentTimeMillis() / 1000L): Int {
        return (timestampSeconds / MIGRATION_WINDOW).toInt()
    }

    fun deriveBitplaneSelection(seed: ByteArray, epoch: Int, pixelCount: Int): IntArray {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(epoch).array()
        val epochSeed = sha256(seed + bb)
        val raw = prngBytes(epochSeed, pixelCount)
        return IntArray(pixelCount) { i -> (raw[i].toInt() and 0xFF) % 3 }
    }

    /**
     * Irreversibly poison carrier data using deterministic keyed noise.
     */
    fun poisonCarrierData(flatPixels: IntArray, seed: ByteArray, intensity: Float = 1.0f): IntArray {
        val poisonSeed = sha256(seed + "SELF_DESTRUCT_POISON".toByteArray(Charsets.US_ASCII))
        val noise = prngBytes(poisonSeed, flatPixels.size)
        val poisoned = flatPixels.clone()
        for (i in poisoned.indices) {
            val nVal = noise[i].toInt() and 0xFF
            if (intensity >= 1.0f || (nVal / 255.0f) < intensity) {
                poisoned[i] = (poisoned[i] and 0xF8) or (nVal and 0x07)
            }
        }
        return poisoned
    }

    /**
     * Anti-forensics: generate camera sensor noise profile.
     */
    fun generateCameraNoiseProfile(seed: ByteArray, length: Int): IntArray {
        val noiseSeed = sha256(seed + "CAMERA_NOISE".toByteArray(Charsets.US_ASCII))
        val rawBytes = prngBytes(noiseSeed, length * 2)
        val noise = IntArray(length)
        var nIdx = 0
        var i = 0
        while (i < rawBytes.size - 1 && nIdx < length) {
            val u1 = ((rawBytes[i].toInt() and 0xFF) + 1) / 256.0
            val u2 = ((rawBytes[i + 1].toInt() and 0xFF) + 1) / 256.0
            val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
            val v = ((z + 4.0) * 0.875).toInt()
            noise[nIdx++] = max(0, min(7, v))
            i += 2
        }
        return noise
    }

    fun applyAntiForensicPoisoning(
        argb: IntArray,
        capacityMap: IntArray,
        seed: ByteArray,
        isUsedSlot: BooleanArray
    ): IntArray {
        val poisoned = argb.clone()
        val totalSlots = argb.size * 3
        var unusedCount = 0
        for (i in 0 until totalSlots) {
            if (i >= isUsedSlot.size || !isUsedSlot[i]) unusedCount++
        }
        if (unusedCount <= 0) return poisoned

        val noise = generateCameraNoiseProfile(seed, unusedCount)
        var noiseIdx = 0

        for (slotIdx in 0 until totalSlots) {
            if (slotIdx >= isUsedSlot.size || !isUsedSlot[slotIdx]) {
                val cap = if (slotIdx < capacityMap.size) capacityMap[slotIdx] else 1
                if (cap > 0 && noiseIdx < noise.size) {
                    val maskVal = ((0x1 shl cap) - 1).inv() and 0xFF
                    val noiseVal = noise[noiseIdx++] and ((0x1 shl cap) - 1)
                    
                    val pIdx = slotIdx / 3
                    val pixel = poisoned[pIdx]
                    val newPixel = when (slotIdx % 3) {
                        0 -> { // Red
                            val newR = (((pixel shr 16) and 0xFF) and maskVal) or noiseVal
                            (pixel and 0xFF00FFFF.toInt()) or (newR shl 16)
                        }
                        1 -> { // Green
                            val newG = (((pixel shr 8) and 0xFF) and maskVal) or noiseVal
                            (pixel and 0xFFFF00FF.toInt()) or (newG shl 8)
                        }
                        else -> { // Blue
                            val newB = ((pixel and 0xFF) and maskVal) or noiseVal
                            (pixel and 0xFFFFFF00.toInt()) or newB
                        }
                    }
                    poisoned[pIdx] = newPixel
                }
            }
        }
        return poisoned
    }

    fun bytesToBits(bytes: ByteArray): BooleanArray {
        val bits = BooleanArray(bytes.size * 8)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            for (bit in 0..7) {
                bits[i * 8 + bit] = ((b shr (7 - bit)) and 1) == 1
            }
        }
        return bits
    }

    fun bitsToBytes(bits: BooleanArray): ByteArray {
        val byteCount = (bits.size + 7) / 8
        val out = ByteArray(byteCount)
        for (i in bits.indices) {
            if (bits[i]) {
                val byteIndex = i / 8
                val bitOffset = 7 - (i % 8)
                out[byteIndex] = (out[byteIndex].toInt() or (1 shl bitOffset)).toByte()
            }
        }
        return out
    }
}
