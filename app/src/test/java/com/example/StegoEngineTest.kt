package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.example.crypto.GF256
import com.example.crypto.StegoEngine
import com.example.crypto.ZyloCrypto
import com.example.hardware.HardwareAccelerator
import com.example.model.EmbedOptions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class StegoEngineTest {

    @Test
    fun testGF256Arithmetic() {
        val gf = GF256.INSTANCE
        // Multiplication and division
        val a = 0x53
        val b = 0xCA
        val product = gf.mul(a, b)
        val quotient = gf.div(product, b)
        assertEquals(a, quotient)

        // Identity
        assertEquals(a, gf.mul(a, 1))
        assertEquals(0, gf.mul(a, 0))
    }

    @Test
    fun testShamirSecretSharingKofN() {
        val originalSecret = "TOP_SECRET_CRYPTOGRAPHIC_SEED_2026_ZYLO_VEIL".toByteArray(Charsets.UTF_8)

        // Test 2-of-3 Shamir
        val shares = GF256.shamirSplit(originalSecret, k = 2, n = 3)
        assertEquals(3, shares.size)

        // Reconstruct with any 2 shares
        val share1and2 = listOf(shares[0], shares[1])
        val reconstructed1 = GF256.shamirRecover(share1and2, k = 2)
        assertArrayEquals(originalSecret, reconstructed1)

        val share2and3 = listOf(shares[1], shares[2])
        val reconstructed2 = GF256.shamirRecover(share2and3, k = 2)
        assertArrayEquals(originalSecret, reconstructed2)

        val share1and3 = listOf(shares[0], shares[2])
        val reconstructed3 = GF256.shamirRecover(share1and3, k = 2)
        assertArrayEquals(originalSecret, reconstructed3)
    }

    @Test
    fun testShamirSecretSharingK1of1() {
        val secret = "SINGLE_CARRIER_CONFIDENTIAL".toByteArray(Charsets.UTF_8)
        val shares = GF256.shamirSplit(secret, k = 1, n = 1)
        val recovered = GF256.shamirRecover(shares, k = 1)
        assertArrayEquals(secret, recovered)
    }

    @Test
    fun testAeadEncryptionAndDecryption() {
        val key = ZyloCrypto.randomBytes(32)
        val iv = ZyloCrypto.randomBytes(ZyloCrypto.IV_LEN)
        val aad = "ZYLO_HEADER_V4".toByteArray(Charsets.US_ASCII)
        val plaintext = "ZYLO_VEIL_AES_256_GCM_HARDWARE_PROTECTED".toByteArray(Charsets.UTF_8)

        val ciphertext = ZyloCrypto.aeadEncrypt(key, iv, plaintext, aad)
        val decrypted = ZyloCrypto.aeadDecrypt(key, iv, ciphertext, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun testAeadRejectsModifiedAssociatedData() {
        val key = ZyloCrypto.randomBytes(32)
        val iv = ZyloCrypto.randomBytes(ZyloCrypto.IV_LEN)
        val ciphertext = ZyloCrypto.aeadEncrypt(key, iv, byteArrayOf(1, 2, 3), byteArrayOf(4))

        ZyloCrypto.aeadDecrypt(key, iv, ciphertext, byteArrayOf(5))
    }

    @Test
    fun testCompressionAndDecompression() {
        val data = "A very repetitive payload that compresses exceptionally well in ZYLO VEIL ".repeat(20).toByteArray(Charsets.UTF_8)
        val compressed = ZyloCrypto.compressPayload(data)
        assertTrue(compressed.size < data.size)

        val decompressed = ZyloCrypto.decompressPayload(compressed)
        assertArrayEquals(data, decompressed)
    }

    @Test
    fun testHardwareProfileDetection() {
        val profile = HardwareAccelerator.profile
        assertTrue(profile.cpuCores >= 1)
        assertTrue(profile.memoryLimitMb > 0)
        assertTrue(profile.processorFamily.displayName.isNotEmpty())
    }

    @Test
    fun testHeaderPackAndUnpack() {
        val salt = ZyloCrypto.randomBytes(16)
        val iv = ZyloCrypto.randomBytes(12)
        val packed = StegoEngine.packHeaderV3(
            flags = 12,
            bpc = 2,
            salt = salt,
            iv = iv,
            shareDataLen = 1024L,
            startTs = 1000L,
            endTs = 2000L,
            maxAtt = 3,
            attUsed = 0,
            shareIdx = 1,
            kNeeded = 1,
            nTotal = 1,
            shareId = 987654321L,
            migrationEpoch = 12345L
        )
        assertEquals(ZyloCrypto.HEADER_FIXED_LEN, packed.size)

        val unpacked = StegoEngine.unpackHeaderV3(packed)
        assertEquals(12, unpacked.flags)
        assertEquals(2, unpacked.bpc)
        assertArrayEquals(salt, unpacked.salt)
        assertArrayEquals(iv, unpacked.iv)
        assertEquals(1024L, unpacked.shareDataLen)
        assertEquals(1, unpacked.shareIdx)
        assertEquals(1, unpacked.kNeeded)
        assertEquals(1, unpacked.nTotal)
        assertEquals(987654321L, unpacked.shareId)
        assertEquals(12345L, unpacked.migrationEpoch)
        assertEquals(ZyloCrypto.KDF_ID_PBKDF2_HMAC_SHA512, unpacked.kdfId)
        assertEquals(ZyloCrypto.KDF_ITERATIONS, unpacked.kdfIterations)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHeaderRejectsInvalidThreshold() {
        StegoEngine.packHeaderV3(
            flags = 0,
            bpc = 2,
            salt = ZyloCrypto.randomBytes(ZyloCrypto.SALT_LEN),
            iv = ZyloCrypto.randomBytes(ZyloCrypto.IV_LEN),
            shareDataLen = 32,
            startTs = 0,
            endTs = 0,
            maxAtt = 0,
            attUsed = 0,
            shareIdx = 1,
            kNeeded = 3,
            nTotal = 2,
            shareId = 1,
            migrationEpoch = 0
        )
    }

    @Test
    fun testVerifyCarrierSetReportsMissingAuthenticatedCarriers() {
        val report = StegoEngine.verifyCarrierSet(emptyList(), "passphrase")

        assertTrue(!report.passed)
        assertTrue(report.checks.any { it.name == "Share-set membership" })
    }
}
