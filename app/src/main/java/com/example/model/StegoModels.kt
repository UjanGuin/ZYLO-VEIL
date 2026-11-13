package com.example.model

import java.io.File

data class CarrierImage(
    val id: String,
    val originalName: String,
    val file: File,
    val width: Int,
    val height: Int,
    val capacityBytes: Int,
    val isConvertedFromJpg: Boolean = false,
    val originalFormat: String = "PNG",
    val conversionMessage: String? = null
)

data class ZyloHeader(
    val magic: ByteArray,
    val version: Int,
    val flags: Int,
    val bpc: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val shareDataLen: Long,
    val startTs: Long,
    val endTs: Long,
    val maxAtt: Int,
    val attUsed: Int,
    val shareIdx: Int,
    val kNeeded: Int,
    val nTotal: Int,
    val shareId: Long,
    val migrationEpoch: Long,
    val kdfId: Int = 1,
    val kdfIterations: Int = 50_000
) {
    val isCarrierBound: Boolean get() = (flags and (1 shl 2)) != 0
    val isNoiseAdaptive: Boolean get() = (flags and (1 shl 3)) != 0
    val isStateless: Boolean get() = (flags and (1 shl 4)) != 0
    val isSemanticCamo: Boolean get() = (flags and (1 shl 5)) != 0
    val isPoisonAntiForensic: Boolean get() = (flags and (1 shl 6)) != 0
    val isMutateOnExtract: Boolean get() = (flags and (1 shl 7)) != 0
    val isBitMigration: Boolean get() = (flags and (1 shl 8)) != 0
    val isSelfDestruct: Boolean get() = (flags and (1 shl 1)) != 0
    val isTimeLocked: Boolean get() = (flags and (1 shl 0)) != 0
}

data class EmbedOptions(
    val passphrase: String = "",
    val kNeeded: Int = 1,
    val carrierBound: Boolean = true,
    val noiseAdaptive: Boolean = true,
    val semanticCamo: Boolean = true,
    val antiForensicPoison: Boolean = false,
    val timeLock: Boolean = false,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val statelessMode: Boolean = false,
    val mutationMode: Boolean = false,
    val bitMigration: Boolean = false,
    val selfDestruct: Boolean = false,
    val maxAttempts: Int = 3,
    val autoConvertJpgToPng: Boolean = true
) {
    fun toFlags(): Int {
        var flags = 0
        if (timeLock) flags = flags or (1 shl 0)
        if (selfDestruct) flags = flags or (1 shl 1)
        if (carrierBound) flags = flags or (1 shl 2)
        if (noiseAdaptive) flags = flags or (1 shl 3)
        if (statelessMode) flags = flags or (1 shl 4)
        if (semanticCamo) flags = flags or (1 shl 5)
        if (antiForensicPoison) flags = flags or (1 shl 6)
        if (mutationMode) flags = flags or (1 shl 7)
        if (bitMigration) flags = flags or (1 shl 8)
        return flags
    }
}

data class StegoProgress(
    val stage: String = "Idle",
    val percent: Int = 0,
    val detail: String = "",
    val isRunning: Boolean = false
)

data class EmbedResult(
    val outputFiles: List<File>,
    val kNeeded: Int,
    val nTotal: Int,
    val shareId: Long,
    val capacityUsed: Long,
    val capacityTotal: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExtractResult(
    val data: ByteArray,
    val filename: String,
    val mutatedFiles: List<File> = emptyList(),
    val header: ZyloHeader? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AnalysisReport(
    val width: Int,
    val height: Int,
    val totalPixels: Int,
    val estimatedCapacityBytes: Int,
    val averageLuminanceVariance: Float,
    val detectedHeader: ZyloHeader?,
    val isStatelessLikely: Boolean,
    val redLsbEntropy: Float,
    val greenLsbEntropy: Float,
    val blueLsbEntropy: Float,
    val estimatedVisualDistortionPercent: Float = 0f,
    val estimatedDetectabilityScore: Float = 0f,
    val extractionReliability: String = "Not measured"
)

enum class VerificationStatus { PASS, WARNING, FAIL }

data class VerificationCheck(
    val name: String,
    val status: VerificationStatus,
    val detail: String
)

data class CarrierVerificationReport(
    val fileName: String,
    val checks: List<VerificationCheck>,
    val header: ZyloHeader? = null
) {
    val passed: Boolean get() = checks.none { it.status == VerificationStatus.FAIL }
}

data class CarrierSetVerificationReport(
    val carriers: List<CarrierVerificationReport>,
    val checks: List<VerificationCheck>
) {
    val passed: Boolean get() = checks.none { it.status == VerificationStatus.FAIL } &&
            carriers.all { it.passed }
}

data class CarrierBenchmark(
    val fileName: String,
    val availableCapacityBytes: Long,
    val usedCapacityBytes: Long?,
    val changedChannelRatio: Float?,
    val meanAbsolutePixelError: Float?,
    val verificationSuccesses: Int,
    val verificationTrials: Int,
    val lsbEntropy: Float,
    val detectabilityIndicator: Float
)

data class BenchmarkReport(
    val carriers: List<CarrierBenchmark>,
    val aggregateAvailableCapacityBytes: Long,
    val aggregateUsedCapacityBytes: Long?,
    val aggregateChangedChannelRatio: Float?,
    val aggregateMeanAbsolutePixelError: Float?,
    val aggregateVerificationReliability: Float?,
    val disclaimer: String = "Indicators are measured or estimated from available inputs; they do not claim immunity from specialized steganalysis."
)
