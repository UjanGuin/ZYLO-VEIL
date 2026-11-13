package com.example.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AnalysisReport
import com.example.model.CarrierImage
import com.example.model.EmbedOptions
import com.example.model.EmbedResult
import com.example.model.ExtractResult
import com.example.model.StegoProgress
import com.example.model.ZyloHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object StegoEngine {

    /**
     * Auto converts any uploaded image (JPG, JPEG, WEBP, PNG) to a clean, lossless PNG
     * in ARGB_8888 format to protect against lossy LSB destruction.
     */
    suspend fun prepareCarrierImage(
        context: Context,
        uri: Uri,
        forcePngConversion: Boolean = true
    ): CarrierImage = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/png"
        val isJpg = mimeType.contains("jpeg", ignoreCase = true) ||
                mimeType.contains("jpg", ignoreCase = true) ||
                uri.toString().contains(".jpg", ignoreCase = true) ||
                uri.toString().contains(".jpeg", ignoreCase = true)

        val isWebp = mimeType.contains("webp", ignoreCase = true)

        var fileName = "carrier_${System.currentTimeMillis()}"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex) ?: fileName
                }
            }
        }

        // Decode source bitmap in full ARGB_8888
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open carrier image stream")
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            ?: throw IllegalArgumentException("Cannot decode carrier image")
        inputStream.close()

        val maxDim = 1600
        val origW = bitmap.width
        val origH = bitmap.height
        val scale = minOf(1.0f, maxDim.toFloat() / maxOf(origW, origH))
        val processedBitmap = if (scale < 1.0f) {
            val newW = (origW * scale).toInt().coerceAtLeast(1)
            val newH = (origH * scale).toInt().coerceAtLeast(1)
            val scaledBmp = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            bitmap.recycle()
            scaledBmp
        } else {
            bitmap
        }

        val width = processedBitmap.width
        val height = processedBitmap.height
        val totalSlots = width * height * 3
        // Approximate capacity at default 2 BPC
        val capacityBytes = (totalSlots * ZyloCrypto.DEFAULT_BPC_HINT) / 8

        val baseName = fileName.substringBeforeLast('.')
        val outputPngFile = File(context.cacheDir, "carrier_${UUID.randomUUID().toString().take(8)}_$baseName.png")

        val fos = FileOutputStream(outputPngFile)
        processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        processedBitmap.recycle()

        val origFormat = when {
            isJpg -> "JPEG"
            isWebp -> "WEBP"
            else -> "PNG"
        }

        val conversionMessage = if (isJpg) {
            "Auto-converted JPG to lossless PNG (ARGB_8888). Bit-planes preserved."
        } else if (isWebp) {
            "Auto-converted WEBP to lossless PNG. Ready for steganography."
        } else {
            "Pristine PNG carrier verified."
        }

        CarrierImage(
            id = UUID.randomUUID().toString(),
            originalName = fileName,
            file = outputPngFile,
            width = width,
            height = height,
            capacityBytes = capacityBytes,
            isConvertedFromJpg = isJpg || isWebp,
            originalFormat = origFormat,
            conversionMessage = conversionMessage
        )
    }

    /**
     * Rescales a carrier PNG image up to a target resolution/capacity.
     */
    suspend fun upscaleCarrierImage(
        context: Context,
        carrier: CarrierImage,
        targetCapacityBytes: Int
    ): CarrierImage = withContext(Dispatchers.IO) {
        val targetSlotsNeeded = (targetCapacityBytes.toLong() * 8 / ZyloCrypto.DEFAULT_BPC_HINT)
        val targetPixelsNeeded = (targetSlotsNeeded / 3).toDouble()
        val currentPixels = carrier.width.toLong() * carrier.height.toLong()

        val scaleFactor = sqrt(targetPixelsNeeded / currentPixels.toDouble()).coerceAtLeast(1.05)
        val newW = (carrier.width * scaleFactor).toInt().coerceAtLeast(carrier.width + 10)
        val newH = (carrier.height * scaleFactor).toInt().coerceAtLeast(carrier.height + 10)

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val origBmp = BitmapFactory.decodeFile(carrier.file.absolutePath, options)
            ?: throw IllegalArgumentException("Cannot decode carrier for upscale: ${carrier.originalName}")

        val scaledBmp = Bitmap.createScaledBitmap(origBmp, newW, newH, true)
        origBmp.recycle()

        val newFile = File(context.cacheDir, "upscaled_${UUID.randomUUID().toString().take(8)}_${carrier.originalName.substringBeforeLast('.')}.png")
        FileOutputStream(newFile).use { fos ->
            scaledBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        scaledBmp.recycle()

        val newTotalSlots = newW * newH * 3
        val newCap = (newTotalSlots * ZyloCrypto.DEFAULT_BPC_HINT) / 8

        carrier.copy(
            file = newFile,
            width = newW,
            height = newH,
            capacityBytes = newCap,
            conversionMessage = "Upscaled to ${newW}×${newH} px (${newCap / 1024} KB capacity)"
        )
    }

    /**
     * Reads 1-Int-per-pixel ARGB_8888 bitmap array.
     */
    fun loadArgbPixels(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val numPixels = width * height
        val argb = IntArray(numPixels)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        return argb
    }

    /**
     * Reconstructs ARGB_8888 bitmap from IntArray.
     */
    fun saveArgbPixelsToBitmap(argb: IntArray, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

    inline fun getChannelValue(argb: IntArray, slotIdx: Int): Int {
        val pixel = argb[slotIdx / 3]
        return when (slotIdx % 3) {
            0 -> (pixel shr 16) and 0xFF
            1 -> (pixel shr 8) and 0xFF
            else -> pixel and 0xFF
        }
    }

    inline fun setChannelValue(argb: IntArray, slotIdx: Int, maskVal: Int, chunkVal: Int) {
        val pIdx = slotIdx / 3
        val pixel = argb[pIdx]
        val newPixel = when (slotIdx % 3) {
            0 -> { // Red
                val newR = (((pixel shr 16) and 0xFF) and maskVal) or chunkVal
                (pixel and 0xFF00FFFF.toInt()) or (newR shl 16)
            }
            1 -> { // Green
                val newG = (((pixel shr 8) and 0xFF) and maskVal) or chunkVal
                (pixel and 0xFFFF00FF.toInt()) or (newG shl 8)
            }
            else -> { // Blue
                val newB = ((pixel and 0xFF) and maskVal) or chunkVal
                (pixel and 0xFFFFFF00.toInt()) or newB
            }
        }
        argb[pIdx] = newPixel
    }

    /**
     * Compute bits-per-channel capacity map matching ZYLO VEIL Python logic.
     */
    fun computeCapacityMap(
        argb: IntArray,
        width: Int,
        height: Int,
        bpcHint: Int,
        seed: ByteArray,
        flags: Int,
        migrationEpoch: Int = 0
    ): IntArray {
        val numPixels = width * height
        val useNoise = (flags and ZyloCrypto.FLAG_NOISE_ADAPTIVE) != 0
        val useSemantic = (flags and ZyloCrypto.FLAG_SEMANTIC_CAMO) != 0
        val useMigration = (flags and ZyloCrypto.FLAG_BITPLANE_MIGRATION) != 0

        val scores = FloatArray(numPixels)
        val mask = 0xF8

        if (useNoise || useSemantic) {
            val lum = FloatArray(numPixels)
            for (i in 0 until numPixels) {
                val c = argb[i]
                val r = (c shr 16) and mask
                val g = (c shr 8) and mask
                val b = c and mask
                lum[i] = (0.299f * r + 0.587f * g + 0.114f * b)
            }

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val i = rowOffset + x
                    val l = lum[i]
                    var diff = 0f
                    var count = 0
                    if (x < width - 1) { diff += abs(l - lum[i + 1]); count++ }
                    if (y < height - 1) { diff += abs(l - lum[i + width]); count++ }
                    if (x > 0) { diff += abs(l - lum[i - 1]); count++ }
                    if (y > 0) { diff += abs(l - lum[i - width]); count++ }
                    scores[i] = if (count > 0) diff / count else 0f
                }
            }
        }

        val biasBytes = ZyloCrypto.prngBytes(seed, numPixels)
        val migrationAdjustment = if (useMigration && migrationEpoch > 0) {
            ZyloCrypto.deriveBitplaneSelection(seed, migrationEpoch, numPixels)
        } else {
            IntArray(numPixels) { 0 }
        }

        val finalBpp = IntArray(numPixels)

        if (!useNoise && !useSemantic) {
            for (i in 0 until numPixels) {
                var base = bpcHint
                if (useMigration) {
                    base = max(1, min(3, base + migrationAdjustment[i] - 1))
                }
                finalBpp[i] = base
            }
        } else {
            var maxS = 0f
            for (s in scores) if (s > maxS) maxS = s
            if (maxS < 1e-9f) maxS = 1.0f

            val combined = FloatArray(numPixels)
            for (i in 0 until numPixels) {
                val normScore = scores[i] / maxS
                val bias = (biasBytes[i].toInt() and 0xFF) / 255.0f
                combined[i] = if (useSemantic) {
                    normScore * 0.7f + bias * 0.3f
                } else {
                    normScore * 0.4f + bias * 0.6f
                }
            }

            // Quantiles
            val sorted = combined.clone().apply { sort() }
            val high = sorted[(sorted.size * 0.85).toInt()]
            val low = sorted[(sorted.size * 0.15).toInt()]

            for (i in 0 until numPixels) {
                val v = combined[i]
                var baseBpc = bpcHint
                if (v > high) {
                    baseBpc = min(3, bpcHint + 1)
                } else if (v < low) {
                    baseBpc = max(0, bpcHint - 1)
                }
                if (useMigration) {
                    baseBpc = max(0, min(3, baseBpc + migrationAdjustment[i] - 1))
                }
                finalBpp[i] = baseBpc
            }
        }

        // Expand to 3 channels: R, G, B
        val bitsMap = IntArray(numPixels * 3)
        var ptr = 0
        for (bpp in finalBpp) {
            bitsMap[ptr++] = bpp
            bitsMap[ptr++] = bpp
            bitsMap[ptr++] = bpp
        }
        return bitsMap
    }

    /**
     * Deterministic selection of header slots with capacity >= 1 using O(1) memory permutation.
     */
    fun findValidHeaderSlots(bitsMap: IntArray, neededBits: Int, seed: ByteArray): IntArray {
        val totalSlots = bitsMap.size
        val headerSeed = ZyloCrypto.sha256(seed + "HEADER_SLOTS".toByteArray(Charsets.US_ASCII))
        val result = IntArray(neededBits)
        var count = 0
        var ptr = 0
        while (count < neededBits && ptr < totalSlots) {
            val slot = ZyloCrypto.keyedPermutation(ptr++, totalSlots, headerSeed)
            if (bitsMap[slot] >= 1) {
                result[count++] = slot
            }
        }
        if (count < neededBits) {
            throw IllegalArgumentException("Insufficient carrier capacity for header. Need $neededBits bits, have $count")
        }
        return result
    }

    /**
     * Pack header v3 (81 bytes).
     */
    fun packHeaderV3(
        flags: Int,
        bpc: Int,
        salt: ByteArray,
        iv: ByteArray,
        shareDataLen: Long,
        startTs: Long,
        endTs: Long,
        maxAtt: Int,
        attUsed: Int,
        shareIdx: Int,
        kNeeded: Int,
        nTotal: Int,
        shareId: Long,
        migrationEpoch: Long
    ): ByteArray {
        require(flags in 0..0xFFFF) { "Invalid header flags: $flags" }
        require(bpc in 1..3) { "Invalid bits-per-channel value: $bpc" }
        require(salt.size == ZyloCrypto.SALT_LEN) { "Invalid salt length: ${salt.size}" }
        require(iv.size == ZyloCrypto.IV_LEN) { "Invalid IV length: ${iv.size}" }
        require(shareDataLen >= 0) { "Invalid share data length: $shareDataLen" }
        require(maxAtt in 0..255 && attUsed in 0..255) { "Invalid attempt counter" }
        require(kNeeded in 1..255 && nTotal in 1..255 && kNeeded <= nTotal) {
            "Invalid Shamir threshold: $kNeeded of $nTotal"
        }
        require(shareIdx in 1..255) { "Invalid share index: $shareIdx" }
        require(migrationEpoch in 0..0xFFFFFFFFL) { "Invalid migration epoch: $migrationEpoch" }
        val bb = ByteBuffer.allocate(ZyloCrypto.HEADER_FIXED_LEN).order(ByteOrder.BIG_ENDIAN)
        bb.put(ZyloCrypto.MAGIC)
        bb.put(ZyloCrypto.VERSION.toByte())
        bb.putShort(flags.toShort())
        bb.put(bpc.toByte())
        bb.put(salt) // 16
        bb.put(iv)   // 12
        bb.putLong(shareDataLen)
        bb.putLong(startTs)
        bb.putLong(endTs)
        bb.put(maxAtt.toByte())
        bb.put(attUsed.toByte())
        bb.put(shareIdx.toByte())
        bb.put(kNeeded.toByte())
        bb.put(nTotal.toByte())
        bb.putInt(shareId.toInt())
        bb.putInt(migrationEpoch.toInt())
        bb.put(ZyloCrypto.KDF_ID_PBKDF2_HMAC_SHA512.toByte())
        bb.put((ZyloCrypto.KDF_ITERATIONS ushr 16).toByte())
        bb.put((ZyloCrypto.KDF_ITERATIONS ushr 8).toByte())
        bb.put(ZyloCrypto.KDF_ITERATIONS.toByte())
        return bb.array()
    }

    /**
     * Unpack header v3 (81 bytes).
     */
    fun unpackHeaderV3(b: ByteArray): ZyloHeader {
        if (b.size < ZyloCrypto.HEADER_FIXED_LEN) {
            throw IllegalArgumentException("Header too short: ${b.size} < ${ZyloCrypto.HEADER_FIXED_LEN}")
        }
        val bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(8)
        bb.get(magic)
        if (!magic.contentEquals(ZyloCrypto.MAGIC)) {
            throw IllegalArgumentException("Invalid steganographic magic identifier")
        }
        val version = bb.get().toInt() and 0xFF
        require(version == ZyloCrypto.VERSION || version == ZyloCrypto.LEGACY_VERSION) {
            "Unsupported steganographic version: $version"
        }
        val flags = bb.short.toInt() and 0xFFFF
        val bpc = bb.get().toInt() and 0xFF
        require(bpc in 1..3) { "Invalid bits-per-channel value: $bpc" }
        val salt = ByteArray(16)
        bb.get(salt)
        val iv = ByteArray(12)
        bb.get(iv)
        val shareDataLen = bb.long
        val startTs = bb.long
        val endTs = bb.long
        val maxAtt = bb.get().toInt() and 0xFF
        val attUsed = bb.get().toInt() and 0xFF
        val shareIdx = bb.get().toInt() and 0xFF
        val kNeeded = bb.get().toInt() and 0xFF
        val nTotal = bb.get().toInt() and 0xFF
        val shareId = bb.int.toLong() and 0xFFFFFFFFL
        val migrationEpoch = bb.int.toLong() and 0xFFFFFFFFL
        val encodedKdfId = bb.get().toInt() and 0xFF
        val encodedIterations = ((bb.get().toInt() and 0xFF) shl 16) or
                ((bb.get().toInt() and 0xFF) shl 8) or
                (bb.get().toInt() and 0xFF)
        val kdfId = if (encodedKdfId == 0) ZyloCrypto.KDF_ID_PBKDF2_HMAC_SHA512 else encodedKdfId
        val kdfIterations = if (encodedIterations == 0) ZyloCrypto.KDF_ITERATIONS else encodedIterations
        require(kdfId == ZyloCrypto.KDF_ID_PBKDF2_HMAC_SHA512) { "Unsupported KDF id: $kdfId" }
        require(kdfIterations in 10_000..10_000_000) {
            "Invalid KDF iteration count: $kdfIterations"
        }
        require(shareDataLen >= 16) { "Invalid encrypted share length: $shareDataLen" }
        require(maxAtt == 0 || attUsed <= maxAtt) { "Invalid attempt counter: $attUsed/$maxAtt" }
        require(kNeeded in 1..nTotal) { "Invalid Shamir threshold: $kNeeded of $nTotal" }
        require(shareIdx in 1..nTotal) { "Invalid share index: $shareIdx of $nTotal" }

        return ZyloHeader(
            magic = magic,
            version = version,
            flags = flags,
            bpc = bpc,
            salt = salt,
            iv = iv,
            shareDataLen = shareDataLen,
            startTs = startTs,
            endTs = endTs,
            maxAtt = maxAtt,
            attUsed = attUsed,
            shareIdx = shareIdx,
            kNeeded = kNeeded,
            nTotal = nTotal,
            shareId = shareId,
            migrationEpoch = migrationEpoch,
            kdfId = kdfId,
            kdfIterations = kdfIterations
        )
    }

    private fun headerBytesForAad(header: ZyloHeader): ByteArray {
        return headerBytesForStorage(header.copy(attUsed = 0))
    }

    private fun headerBytesForStorage(header: ZyloHeader): ByteArray {
        return packHeaderV3(
            flags = header.flags,
            bpc = header.bpc,
            salt = header.salt,
            iv = header.iv,
            shareDataLen = header.shareDataLen,
            startTs = header.startTs,
            endTs = header.endTs,
            maxAtt = header.maxAtt,
            attUsed = header.attUsed,
            shareIdx = header.shareIdx,
            kNeeded = header.kNeeded,
            nTotal = header.nTotal,
            shareId = header.shareId,
            migrationEpoch = header.migrationEpoch
        )
    }

    /**
     * Embed a single Shamir share into a carrier image file and save as PNG.
     */
    fun embedShare(
        carrierFile: File,
        outputFile: File,
        shareData: ByteArray,
        passphrase: String,
        flags: Int,
        bpcHint: Int,
        startTs: Long,
        endTs: Long,
        maxAtt: Int,
        shareIdx: Int,
        kNeeded: Int,
        nTotal: Int,
        shareId: Long
    ) {
        val bitmap = BitmapFactory.decodeFile(carrierFile.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }) ?: throw IllegalArgumentException("Cannot decode carrier ${carrierFile.name}")

        val width = bitmap.width
        val height = bitmap.height
        val argb = loadArgbPixels(bitmap)
        val fp = ZyloCrypto.carrierFingerprintFromArgb(argb)
        val totalSlots = width * height * 3

        val migrationEpoch = if ((flags and ZyloCrypto.FLAG_BITPLANE_MIGRATION) != 0) {
            ZyloCrypto.computeMigrationEpoch()
        } else 0

        val isUsedSlot = BooleanArray(totalSlots)

        if ((flags and ZyloCrypto.FLAG_STATELESS) != 0) {
            // Stateless Zero-Header mode
            val salt = ZyloCrypto.sha256(fp + passphrase.toByteArray(Charsets.UTF_8)).copyOf(ZyloCrypto.SALT_LEN)
            val key = ZyloCrypto.deriveKey(passphrase, salt, fp)
            val seedHeader = ZyloCrypto.sha256(key + "StatelessSeedV3".toByteArray(Charsets.US_ASCII))
            val iv = ZyloCrypto.sha256(seedHeader + "IV".toByteArray(Charsets.US_ASCII)).copyOf(ZyloCrypto.IV_LEN)
            val marker = ZyloCrypto.computeStatelessMarker(passphrase, fp, ZyloCrypto.ALGO_VERSION)
            val ciphertextLength = shareData.size + 16
            val metadata = ByteBuffer.allocate(8 + 1 + 1 + 1 + 4 + 4).order(ByteOrder.BIG_ENDIAN)
            metadata.put(marker)
            metadata.put(shareIdx.toByte())
            metadata.put(kNeeded.toByte())
            metadata.put(nTotal.toByte())
            metadata.putInt(shareId.toInt())
            metadata.putInt(ciphertextLength)
            val metadataBytes = metadata.array()
            val ciphertext = ZyloCrypto.aeadEncrypt(key, iv, shareData, metadataBytes)
            val bb = ByteBuffer.allocate(metadataBytes.size + ciphertext.size).order(ByteOrder.BIG_ENDIAN)
            bb.put(metadataBytes)
            bb.put(ciphertext)
            val fullData = bb.array()

            val mapSeed = seedHeader
            val payloadBitsMap = computeCapacityMap(argb, width, height, bpcHint, mapSeed, flags, migrationEpoch)
            val dataBits = ZyloCrypto.bytesToBits(fullData)

            val totalCapacity = payloadBitsMap.sum()
            if (dataBits.size > totalCapacity) {
                throw IllegalArgumentException("Carrier image too small for stateless share. Need ${dataBits.size} bits, have $totalCapacity bits.")
            }

            var ptr = 0
            var bitPtr = 0
            while (bitPtr < dataBits.size) {
                if (ptr >= totalSlots) throw IllegalArgumentException("Ran out of embedding slots")
                val idx = ZyloCrypto.keyedPermutation(ptr++, totalSlots, mapSeed)
                val cap = payloadBitsMap[idx]
                if (cap == 0) continue

                var chunkVal = 0
                for (b in 0 until cap) {
                    if (bitPtr + b < dataBits.size && dataBits[bitPtr + b]) {
                        chunkVal = chunkVal or (1 shl (cap - 1 - b))
                    }
                }
                val maskVal = ((0x1 shl cap) - 1).inv() and 0xFF
                setChannelValue(argb, idx, maskVal, chunkVal)
                isUsedSlot[idx] = true
                bitPtr += cap
            }
        } else {
            // Standard mode with capacity-aware header
            val salt = ZyloCrypto.randomBytes(ZyloCrypto.SALT_LEN)
            val iv = ZyloCrypto.randomBytes(ZyloCrypto.IV_LEN)
            val fpForKey = if ((flags and ZyloCrypto.FLAG_CARRIER_BOUND) != 0) fp else null
            val key = ZyloCrypto.deriveKey(passphrase, salt, fpForKey)
            val ciphertextLength = shareData.size + 16
            val header = packHeaderV3(
                flags, bpcHint, salt, iv, ciphertextLength.toLong(),
                startTs, endTs, maxAtt, 0,
                shareIdx, kNeeded, nTotal, shareId, migrationEpoch.toLong()
            )
            val ciphertext = ZyloCrypto.aeadEncrypt(key, iv, shareData, header)
            val headerBits = ZyloCrypto.bytesToBits(header)

            val mapSeed = ZyloCrypto.sha256(passphrase.toByteArray(Charsets.UTF_8) + fp + "HEADER_SEED_V3".toByteArray(Charsets.US_ASCII))
            val headerBitsMap = computeCapacityMap(argb, width, height, ZyloCrypto.DEFAULT_BPC_HINT, mapSeed, 0, 0)
            val headerSlots = findValidHeaderSlots(headerBitsMap, headerBits.size, mapSeed)

            // Embed header bits into header slots (1 bit per slot in LSB)
            for (hIdx in headerSlots.indices) {
                val slotIdx = headerSlots[hIdx]
                val bitVal = if (headerBits[hIdx]) 1 else 0
                setChannelValue(argb, slotIdx, 0xFE, bitVal)
                isUsedSlot[slotIdx] = true
            }

            // Payload embedding
            val payloadSeed = ZyloCrypto.sha256(salt + passphrase.toByteArray(Charsets.UTF_8) + "PAYLOAD_V3".toByteArray(Charsets.US_ASCII))
            val payloadBitsMap = computeCapacityMap(argb, width, height, bpcHint, payloadSeed, flags, migrationEpoch)

            val headerSlotsSet = BooleanArray(totalSlots)
            for (slot in headerSlots) {
                if (slot in 0 until totalSlots) {
                    headerSlotsSet[slot] = true
                }
            }

            val dataBits = ZyloCrypto.bytesToBits(ciphertext)
            var sPtr = 0
            var pPtr = 0
            while (pPtr < dataBits.size) {
                if (sPtr >= totalSlots) throw IllegalArgumentException("Ran out of embedding slots for payload")
                val idx = ZyloCrypto.keyedPermutation(sPtr++, totalSlots, payloadSeed)
                if (headerSlotsSet[idx]) continue
                val cap = payloadBitsMap[idx]
                if (cap == 0) continue

                var chunkVal = 0
                for (b in 0 until cap) {
                    if (pPtr + b < dataBits.size && dataBits[pPtr + b]) {
                        chunkVal = chunkVal or (1 shl (cap - 1 - b))
                    }
                }
                val maskVal = ((0x1 shl cap) - 1).inv() and 0xFF
                setChannelValue(argb, idx, maskVal, chunkVal)
                isUsedSlot[idx] = true
                pPtr += cap
            }
        }

        // Anti-forensic sensor noise
        val finalArgb = if ((flags and ZyloCrypto.FLAG_POISON_ANTI_FORENSIC) != 0) {
            val poisonSeed = ZyloCrypto.sha256(passphrase.toByteArray(Charsets.UTF_8) + fp + "ANTI_FORENSIC".toByteArray(Charsets.US_ASCII))
            val capMap = computeCapacityMap(argb, width, height, bpcHint, poisonSeed, flags, migrationEpoch)
            ZyloCrypto.applyAntiForensicPoisoning(argb, capMap, poisonSeed, isUsedSlot)
        } else {
            argb
        }

        // Save output bitmap to lossless PNG
        val outBitmap = saveArgbPixelsToBitmap(finalArgb, width, height)
        val fos = FileOutputStream(outputFile)
        outBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        bitmap.recycle()
        outBitmap.recycle()
    }

    /**
     * Extract a single share from a stego carrier image.
     */
    fun extractShare(
        stegoFile: File,
        passphrase: String,
        allowMutation: Boolean = false
    ): Pair<ZyloHeader, ByteArray> {
        val bitmap = BitmapFactory.decodeFile(stegoFile.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }) ?: throw IllegalArgumentException("Cannot decode carrier ${stegoFile.name}")

        val width = bitmap.width
        val height = bitmap.height
        val argb = loadArgbPixels(bitmap)
        val fp = ZyloCrypto.carrierFingerprintFromArgb(argb)
        val totalSlots = width * height * 3

        var header: ZyloHeader? = null
        var statelessDetected = false

        // 1. Attempt standard header extraction
        try {
            val headerSeed = ZyloCrypto.sha256(passphrase.toByteArray(Charsets.UTF_8) + fp + "HEADER_SEED_V3".toByteArray(Charsets.US_ASCII))
            val headerBitsMap = computeCapacityMap(argb, width, height, ZyloCrypto.DEFAULT_BPC_HINT, headerSeed, 0, 0)
            val headerSlots = findValidHeaderSlots(headerBitsMap, ZyloCrypto.HEADER_FIXED_LEN * 8, headerSeed)

            val bits = BooleanArray(headerSlots.size)
            for (i in headerSlots.indices) {
                bits[i] = (getChannelValue(argb, headerSlots[i]) and 1) == 1
            }
            val headerBytes = ZyloCrypto.bitsToBytes(bits)
            header = unpackHeaderV3(headerBytes)
        } catch (e: Exception) {
            statelessDetected = true
        }

        if (statelessDetected || header == null) {
            // Try Stateless Mode
            val salt = ZyloCrypto.sha256(fp + passphrase.toByteArray(Charsets.UTF_8)).copyOf(ZyloCrypto.SALT_LEN)
            val key = ZyloCrypto.deriveKey(passphrase, salt, fp)
            val seedHeader = ZyloCrypto.sha256(key + "StatelessSeedV3".toByteArray(Charsets.US_ASCII))
            val assumedFlags = ZyloCrypto.FLAG_STATELESS or ZyloCrypto.FLAG_SEMANTIC_CAMO or
                    ZyloCrypto.FLAG_CARRIER_BOUND or ZyloCrypto.FLAG_NOISE_ADAPTIVE

            val bitsMap = computeCapacityMap(argb, width, height, ZyloCrypto.DEFAULT_BPC_HINT, seedHeader, assumedFlags, 0)

            // Extract stateless header: Marker(8) + ShareIdx(1) + KNeeded(1) + NTotal(1) + ShareID(4) + CTLen(4) = 19 bytes = 152 bits
            val statelessHeaderBitsCount = 19 * 8
            val extractedBits = ArrayList<Boolean>()
            var ptr = 0

            while (extractedBits.size < statelessHeaderBitsCount && ptr < totalSlots) {
                val idx = ZyloCrypto.keyedPermutation(ptr++, totalSlots, seedHeader)
                val cap = bitsMap[idx]
                if (cap > 0) {
                    val v = getChannelValue(argb, idx) and ((1 shl cap) - 1)
                    for (b in (cap - 1) downTo 0) {
                        extractedBits.add(((v shr b) and 1) == 1)
                    }
                }
            }
            if (extractedBits.size < statelessHeaderBitsCount) {
                throw IllegalArgumentException("Could not extract stateless header bits")
            }

            val headerBytes = ZyloCrypto.bitsToBytes(extractedBits.take(statelessHeaderBitsCount).toBooleanArray())
            val marker = headerBytes.copyOfRange(0, ZyloCrypto.STATELESS_MARKER_LEN)
            val markerIsCurrent = ZyloCrypto.verifyStatelessMarker(marker, passphrase, fp, ZyloCrypto.ALGO_VERSION)
            val markerValid = markerIsCurrent ||
                    ZyloCrypto.verifyStatelessMarker(marker, passphrase, fp, ZyloCrypto.LEGACY_VERSION)
            if (!markerValid) {
                throw IllegalArgumentException("Passphrase mismatch or unencrypted carrier (Stateless marker failed)")
            }

            val bb = ByteBuffer.wrap(headerBytes, ZyloCrypto.STATELESS_MARKER_LEN, headerBytes.size - ZyloCrypto.STATELESS_MARKER_LEN)
                .order(ByteOrder.BIG_ENDIAN)
            val shareIdx = bb.get().toInt() and 0xFF
            val kNeeded = bb.get().toInt() and 0xFF
            val nTotal = bb.get().toInt() and 0xFF
            val shareId = bb.int.toLong() and 0xFFFFFFFFL
            val ctLen = bb.int
            require(kNeeded in 1..nTotal) { "Invalid Shamir threshold: $kNeeded of $nTotal" }
            require(shareIdx in 1..nTotal) { "Invalid share index: $shareIdx of $nTotal" }
            require(ctLen >= 16 && ctLen <= Int.MAX_VALUE / 8) {
                "Invalid encrypted share length: $ctLen"
            }

            val ctBitsNeeded = ctLen * 8
            while (extractedBits.size < statelessHeaderBitsCount + ctBitsNeeded && ptr < totalSlots) {
                val idx = ZyloCrypto.keyedPermutation(ptr++, totalSlots, seedHeader)
                val cap = bitsMap[idx]
                if (cap > 0) {
                    val v = getChannelValue(argb, idx) and ((1 shl cap) - 1)
                    for (b in (cap - 1) downTo 0) {
                        extractedBits.add(((v shr b) and 1) == 1)
                    }
                }
            }

            require(extractedBits.size >= statelessHeaderBitsCount + ctBitsNeeded) {
                "Could not extract complete stateless payload"
            }
            val ctBits = extractedBits.subList(statelessHeaderBitsCount, statelessHeaderBitsCount + ctBitsNeeded)
            val ciphertext = ZyloCrypto.bitsToBytes(ctBits.toBooleanArray())
            val iv = ZyloCrypto.sha256(seedHeader + "IV".toByteArray(Charsets.US_ASCII)).copyOf(ZyloCrypto.IV_LEN)

            val shareData = if (markerIsCurrent) {
                ZyloCrypto.aeadDecrypt(key, iv, ciphertext, headerBytes.copyOfRange(0, statelessHeaderBitsCount / 8))
            } else {
                ZyloCrypto.aeadDecrypt(key, iv, ciphertext)
            }
            val syntheticHeader = ZyloHeader(
                magic = ZyloCrypto.MAGIC,
                version = ZyloCrypto.VERSION,
                flags = assumedFlags,
                bpc = ZyloCrypto.DEFAULT_BPC_HINT,
                salt = salt,
                iv = iv,
                shareDataLen = ctLen.toLong(),
                startTs = 0,
                endTs = 0,
                maxAtt = 0,
                attUsed = 0,
                shareIdx = shareIdx,
                kNeeded = kNeeded,
                nTotal = nTotal,
                shareId = shareId,
                migrationEpoch = 0
            )
            bitmap.recycle()
            return Pair(syntheticHeader, shareData)
        } else {
            // Standard mode checks
            val nowSec = System.currentTimeMillis() / 1000L
            if (header.isTimeLocked) {
                if (header.startTs > 0 && nowSec < header.startTs) {
                    throw IllegalStateException("Time lock active: carrier is sealed until timestamp ${header.startTs}")
                }
                if (header.endTs > 0 && nowSec > header.endTs) {
                    throw IllegalStateException("Time lock expired: carrier access ended at timestamp ${header.endTs}")
                }
            }

            if (header.isBitMigration) {
                val currentEpoch = ZyloCrypto.computeMigrationEpoch()
                if (header.migrationEpoch != currentEpoch.toLong()) {
                    throw IllegalStateException("Bit-plane migration expired: carrier epoch was ${header.migrationEpoch}, current epoch is $currentEpoch")
                }
            }

            val fpForKey = if (header.isCarrierBound) fp else null
            val key = ZyloCrypto.deriveKey(
                passphrase,
                header.salt,
                fpForKey,
                kdfId = header.kdfId,
                iterations = header.kdfIterations
            )

            val headerSeed = ZyloCrypto.sha256(passphrase.toByteArray(Charsets.UTF_8) + fp + "HEADER_SEED_V3".toByteArray(Charsets.US_ASCII))
            val headerBitsMap = computeCapacityMap(argb, width, height, ZyloCrypto.DEFAULT_BPC_HINT, headerSeed, 0, 0)
            val headerSlots = findValidHeaderSlots(headerBitsMap, ZyloCrypto.HEADER_FIXED_LEN * 8, headerSeed)
            val headerSlotsSet = BooleanArray(totalSlots)
            for (slot in headerSlots) {
                if (slot in 0 until totalSlots) {
                    headerSlotsSet[slot] = true
                }
            }

            val payloadSeed = ZyloCrypto.sha256(header.salt + passphrase.toByteArray(Charsets.UTF_8) + "PAYLOAD_V3".toByteArray(Charsets.US_ASCII))
            val payloadBitsMap = computeCapacityMap(argb, width, height, header.bpc, payloadSeed, header.flags, header.migrationEpoch.toInt())

            require(header.shareDataLen <= Int.MAX_VALUE / 8) {
                "Encrypted share is too large: ${header.shareDataLen} bytes"
            }
            val neededBits = Math.multiplyExact(header.shareDataLen.toInt(), 8)
            val extractedBits = ArrayList<Boolean>()
            var sPtr = 0

            while (extractedBits.size < neededBits && sPtr < totalSlots) {
                val idx = ZyloCrypto.keyedPermutation(sPtr++, totalSlots, payloadSeed)
                if (headerSlotsSet[idx]) continue
                val cap = payloadBitsMap[idx]
                if (cap == 0) continue
                val v = getChannelValue(argb, idx) and ((1 shl cap) - 1)
                for (b in (cap - 1) downTo 0) {
                    extractedBits.add(((v shr b) and 1) == 1)
                }
            }

            if (extractedBits.size < neededBits) {
                throw IllegalArgumentException("Carrier image corrupted or missing payload bits")
            }

            val ciphertext = ZyloCrypto.bitsToBytes(extractedBits.take(neededBits).toBooleanArray())
            val shareData = try {
                if (header.version >= ZyloCrypto.VERSION) {
                    ZyloCrypto.aeadDecrypt(key, header.iv, ciphertext, headerBytesForAad(header))
                } else {
                    ZyloCrypto.aeadDecrypt(key, header.iv, ciphertext)
                }
            } catch (e: Exception) {
                val nextAttempt = (header.attUsed + 1).coerceAtMost(255)
                if (header.isSelfDestruct && header.maxAtt > 0 && nextAttempt >= header.maxAtt) {
                    // Poison carrier image on device
                    val destroySeed = ZyloCrypto.sha256(header.salt + header.iv + "DESTROY".toByteArray(Charsets.US_ASCII))
                    val poisoned = ZyloCrypto.applyAntiForensicPoisoning(argb, payloadBitsMap, destroySeed, BooleanArray(totalSlots) { true })
                    val pBmp = saveArgbPixelsToBitmap(poisoned, width, height)
                    val fos = FileOutputStream(stegoFile)
                    pBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush(); fos.close()
                    pBmp.recycle()
                    throw IllegalStateException("SELF-DESTRUCT TRIGGERED: Decryption failed. Carrier image has been irreversibly poisoned!")
                }
                if (header.version >= ZyloCrypto.VERSION && header.isSelfDestruct && header.maxAtt > 0) {
                    val updatedHeader = header.copy(attUsed = nextAttempt)
                    val updatedBits = ZyloCrypto.bytesToBits(headerBytesForStorage(updatedHeader))
                    for (hIdx in headerSlots.indices) {
                        val bitVal = if (updatedBits[hIdx]) 1 else 0
                        setChannelValue(argb, headerSlots[hIdx], 0xFE, bitVal)
                    }
                    val updatedBitmap = saveArgbPixelsToBitmap(argb, width, height)
                    FileOutputStream(stegoFile).use { fos ->
                        updatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    updatedBitmap.recycle()
                }
                throw IllegalArgumentException("Decryption failed: incorrect passphrase or modified carrier")
            }

            bitmap.recycle()
            return Pair(header, shareData)
        }
    }

    /**
     * Master multi-carrier embedding using TRUE Shamir K-of-N secret sharing.
     */
    suspend fun embedMaster(
        context: Context,
        secretBytes: ByteArray,
        secretFileName: String,
        carrierImages: List<CarrierImage>,
        options: EmbedOptions,
        onProgress: (StegoProgress) -> Unit
    ): EmbedResult = withContext(Dispatchers.IO) {
        onProgress(StegoProgress(stage = "Compressing Payload", percent = 5, detail = secretFileName, isRunning = true))

        // Prepend filename: [2 bytes len] + [name utf8] + data
        val nameBytes = secretFileName.toByteArray(Charsets.UTF_8)
        val bbBlob = ByteBuffer.allocate(2 + nameBytes.size + secretBytes.size).order(ByteOrder.BIG_ENDIAN)
        bbBlob.putShort(nameBytes.size.toShort())
        bbBlob.put(nameBytes)
        bbBlob.put(secretBytes)
        val compressed = ZyloCrypto.compressPayload(bbBlob.array())

        onProgress(StegoProgress(stage = "Encrypting Master Payload", percent = 15, detail = "AES-256-GCM", isRunning = true))

        val masterKey = ZyloCrypto.randomBytes(32)
        val masterIv = ZyloCrypto.randomBytes(ZyloCrypto.IV_LEN)
        val encryptedPayload = ZyloCrypto.aeadEncrypt(masterKey, masterIv, compressed)

        // Combined blob: MasterKey(32) + MasterIV(12) + EncryptedPayload
        val fullBlob = masterKey + masterIv + encryptedPayload

        val n = carrierImages.size
        require(n in 1..255) { "Number of carriers must be between 1 and 255" }
        require(options.kNeeded in 1..n) {
            "Shamir threshold must be between 1 and the number of carriers ($n)"
        }
        val k = options.kNeeded

        val eachShareSize = fullBlob.size
        val requiredBits = (eachShareSize.toLong() + 16L + ZyloCrypto.HEADER_FIXED_LEN) * 8L
        for (carrier in carrierImages) {
            val availableBits = carrier.width.toLong() * carrier.height.toLong() * 3L *
                    ZyloCrypto.DEFAULT_BPC_HINT
            if (availableBits < requiredBits) {
                val neededBytes = (requiredBits + 7L) / 8L
                val haveKb = carrier.capacityBytes / 1024
                throw IllegalArgumentException(
                    "Carrier '${carrier.originalName}' is too small ($haveKb KB capacity). " +
                    "Need at least ${neededBytes / 1024} KB. Please use higher-resolution carriers or split across more images."
                )
            }
        }

        onProgress(StegoProgress(stage = "Shamir Secret Splitting", percent = 25, detail = "Splitting into $n shares (K=$k)", isRunning = true))

        val shares = GF256.shamirSplit(fullBlob, k, n)
        val shareId = System.currentTimeMillis() and 0xFFFFFFFFL

        val outputDir = File(context.filesDir, "stego_output_${System.currentTimeMillis()}").apply { mkdirs() }
        val outputFiles = ArrayList<File>()
        var capacityUsedTotal = 0L
        var capacityAvailableTotal = 0L

        for (i in 0 until n) {
            val progressVal = 30 + ((65 * (i + 1)) / n)
            val carrier = carrierImages[i]
            onProgress(StegoProgress(
                stage = "Embedding Share ${i + 1}/$n",
                percent = progressVal,
                detail = carrier.originalName,
                isRunning = true
            ))

            val (shareIdx, shareData) = shares[i]
            val safeName = carrier.originalName.substringBeforeLast('.')
            val outFile = File(outputDir, "stego_${i + 1}of${n}_$safeName.png")

            embedShare(
                carrierFile = carrier.file,
                outputFile = outFile,
                shareData = shareData,
                passphrase = options.passphrase,
                flags = options.toFlags(),
                bpcHint = ZyloCrypto.DEFAULT_BPC_HINT,
                startTs = options.startDate,
                endTs = options.endDate,
                maxAtt = if (options.selfDestruct) options.maxAttempts else 0,
                shareIdx = shareIdx,
                kNeeded = k,
                nTotal = n,
                shareId = shareId
            )
            outputFiles.add(outFile)
            capacityUsedTotal += shareData.size + 16L + ZyloCrypto.HEADER_FIXED_LEN
            capacityAvailableTotal += carrier.capacityBytes
        }

        onProgress(StegoProgress(stage = "Embedding Complete", percent = 100, detail = "${outputFiles.size} carriers generated", isRunning = false))

        EmbedResult(
            outputFiles = outputFiles,
            kNeeded = k,
            nTotal = n,
            shareId = shareId,
            capacityUsed = capacityUsedTotal,
            capacityTotal = capacityAvailableTotal
        )
    }

    /**
     * Master multi-carrier extraction using TRUE Shamir K-of-N secret reconstruction.
     */
    suspend fun extractMaster(
        stegoFiles: List<File>,
        passphrase: String,
        allowMutation: Boolean = false,
        onProgress: (StegoProgress) -> Unit
    ): ExtractResult = withContext(Dispatchers.IO) {
        onProgress(StegoProgress(stage = "Scanning Carriers", percent = 10, detail = "Extracting steganographic shares", isRunning = true))

        val shares = ArrayList<Pair<Int, ByteArray>>()
        var targetShareId: Long? = null
        var kNeeded = 1
        var headerFound: ZyloHeader? = null

        var lastException: Exception? = null

        for (i in stegoFiles.indices) {
            val file = stegoFiles[i]
            val progressVal = 10 + ((50 * (i + 1)) / stegoFiles.size)
            onProgress(StegoProgress(stage = "Reading Share ${i + 1}/${stegoFiles.size}", percent = progressVal, detail = file.name, isRunning = true))

            try {
                val (head, shareData) = extractShare(file, passphrase, allowMutation)
                if (targetShareId == null) {
                    targetShareId = head.shareId
                    kNeeded = head.kNeeded
                    headerFound = head
                } else if (head.shareId != targetShareId) {
                    continue // Share from different set
                }
                shares.add(Pair(head.shareIdx, shareData))
            } catch (e: Exception) {
                lastException = e
            }
        }

        if (shares.isEmpty()) {
            val errorMsg = lastException?.message ?: "No valid ZYLO VEIL shares found with the provided passphrase"
            throw IllegalArgumentException(errorMsg)
        }

        if (shares.size < kNeeded) {
            throw IllegalArgumentException("Insufficient shares: found ${shares.size}, but need at least $kNeeded shares to reconstruct payload")
        }

        onProgress(StegoProgress(stage = "Lagrange Interpolation", percent = 70, detail = "Reconstructing master ciphertext", isRunning = true))

        val fullBlob = GF256.shamirRecover(shares, kNeeded)
        if (fullBlob.size < 32 + ZyloCrypto.IV_LEN) {
            throw IllegalArgumentException("Reconstructed blob is invalid or corrupted")
        }

        val masterKey = fullBlob.copyOfRange(0, 32)
        val masterIv = fullBlob.copyOfRange(32, 32 + ZyloCrypto.IV_LEN)
        val encryptedPayload = fullBlob.copyOfRange(32 + ZyloCrypto.IV_LEN, fullBlob.size)

        onProgress(StegoProgress(stage = "Decrypting Payload", percent = 85, detail = "AES-256-GCM Authentication", isRunning = true))

        val compressed = ZyloCrypto.aeadDecrypt(masterKey, masterIv, encryptedPayload)

        onProgress(StegoProgress(stage = "Decompressing Secret", percent = 95, detail = "Decompressing payload", isRunning = true))

        val decompressed = ZyloCrypto.decompressPayload(compressed)
        var fileName = "extracted_secret"
        var finalData = decompressed

        if (decompressed.size >= 2) {
            val bb = ByteBuffer.wrap(decompressed).order(ByteOrder.BIG_ENDIAN)
            val nameLen = bb.short.toInt() and 0xFFFF
            if (nameLen in 1 until min(256, decompressed.size - 2)) {
                val nameBytes = ByteArray(nameLen)
                bb.get(nameBytes)
                fileName = String(nameBytes, Charsets.UTF_8)
                val remaining = ByteArray(decompressed.size - 2 - nameLen)
                bb.get(remaining)
                finalData = remaining
            }
        }

        onProgress(StegoProgress(stage = "Extraction Complete", percent = 100, detail = fileName, isRunning = false))

        ExtractResult(
            data = finalData,
            filename = fileName,
            header = headerFound
        )
    }

    /**
     * Stego inspection tool: calculates bit distribution and checks for ZYLO signatures.
     */
    suspend fun analyzeImage(file: File): AnalysisReport = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val origBmp = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalArgumentException("Cannot decode file: ${file.name}")

        val maxDim = 1600
        val origW = origBmp.width
        val origH = origBmp.height
        val scale = minOf(1.0f, maxDim.toFloat() / maxOf(origW, origH))
        val bitmap = if (scale < 1.0f) {
            val newW = (origW * scale).toInt().coerceAtLeast(1)
            val newH = (origH * scale).toInt().coerceAtLeast(1)
            val scaledBmp = Bitmap.createScaledBitmap(origBmp, newW, newH, true)
            origBmp.recycle()
            scaledBmp
        } else {
            origBmp
        }

        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val argb = loadArgbPixels(bitmap)

        var rLsbOnes = 0
        var gLsbOnes = 0
        var bLsbOnes = 0
        var lumSum = 0f
        var lumSqSum = 0f

        for (i in 0 until totalPixels) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            if ((r and 1) == 1) rLsbOnes++
            if ((g and 1) == 1) gLsbOnes++
            if ((b and 1) == 1) bLsbOnes++

            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            lumSum += lum
            lumSqSum += lum * lum
        }

        val rEntropy = calculateBitEntropy(rLsbOnes, totalPixels)
        val gEntropy = calculateBitEntropy(gLsbOnes, totalPixels)
        val bEntropy = calculateBitEntropy(bLsbOnes, totalPixels)

        val meanLum = lumSum / totalPixels
        val varianceLum = (lumSqSum / totalPixels) - (meanLum * meanLum)

        bitmap.recycle()

        AnalysisReport(
            width = origW,
            height = origH,
            totalPixels = origW * origH,
            estimatedCapacityBytes = (origW * origH * 3 * 2) / 8,
            averageLuminanceVariance = varianceLum,
            detectedHeader = null,
            isStatelessLikely = rEntropy > 0.98f && gEntropy > 0.98f && bEntropy > 0.98f,
            redLsbEntropy = rEntropy,
            greenLsbEntropy = gEntropy,
            blueLsbEntropy = bEntropy
        )
    }

    private fun calculateBitEntropy(ones: Int, total: Int): Float {
        if (total == 0) return 0f
        val p1 = ones.toFloat() / total
        val p0 = 1.0f - p1
        if (p0 <= 0f || p1 <= 0f) return 0f
        val log2 = ln(2.0).toFloat()
        return -((p0 * ln(p0.toDouble()).toFloat() / log2) + (p1 * ln(p1.toDouble()).toFloat() / log2))
    }
}
