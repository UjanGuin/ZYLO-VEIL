package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.StegoEngine
import com.example.model.CarrierImage
import com.example.model.EmbedOptions
import com.example.model.EmbedResult
import com.example.model.StegoProgress
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max

class VeilViewModel : ViewModel() {
    var secretType by mutableStateOf("TEXT") // "TEXT" or "FILE"
    var secretText by mutableStateOf("")
    var secretFileName by mutableStateOf("")
    var secretBytes by mutableStateOf<ByteArray?>(null)

    val carrierList = mutableStateListOf<CarrierImage>()
    var passphrase by mutableStateOf("")
    var passphraseVisible by mutableStateOf(false)

    var options by mutableStateOf(EmbedOptions())
    var showAdvancedFlags by mutableStateOf(false)
    var progress by mutableStateOf(StegoProgress())

    var latestResult by mutableStateOf<EmbedResult?>(null)
    var conversionNotice by mutableStateOf<String?>(null)
    var isCarrierLoading by mutableStateOf(false)
    var carrierLoadingStatus by mutableStateOf("")
    var isEmbedding by mutableStateOf(false)

    fun loadSecretFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val cr = context.contentResolver
                var name = "secret_payload.dat"
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) name = cursor.getString(idx)
                    }
                }
                val bytes = cr.openInputStream(uri)?.use { s -> s.readBytes() }
                if (bytes != null) {
                    HapticHelper.click(context)
                    secretFileName = name
                    secretBytes = bytes
                    secretType = "FILE"
                    Toast.makeText(context, "Loaded $name (${bytes.size} bytes)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                Toast.makeText(context, "Failed to load secret: $msg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadCarriers(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            isCarrierLoading = true
            carrierLoadingStatus = "Preparing ${uris.size} carrier image(s)..."
            var convertedCount = 0
            try {
                for ((index, uri) in uris.withIndex()) {
                    carrierLoadingStatus = "Processing carrier ${index + 1} of ${uris.size}..."
                    try {
                        val carrier = StegoEngine.prepareCarrierImage(
                            context = context,
                            uri = uri,
                            forcePngConversion = options.autoConvertJpgToPng
                        )
                        carrierList.add(carrier)
                        if (carrier.isConvertedFromJpg) {
                            convertedCount++
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                        Toast.makeText(context, "Error loading carrier: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
                if (convertedCount > 0) {
                    conversionNotice = "Auto-converted $convertedCount JPG/WEBP carrier(s) to lossless PNG"
                }
                HapticHelper.click(context)
                if (options.kNeeded > carrierList.size) {
                    options = options.copy(kNeeded = max(1, carrierList.size))
                }
            } finally {
                isCarrierLoading = false
                carrierLoadingStatus = ""
            }
        }
    }

    fun removeCarrier(index: Int) {
        if (index in carrierList.indices) {
            carrierList.removeAt(index)
            if (options.kNeeded > carrierList.size) {
                options = options.copy(kNeeded = max(1, carrierList.size))
            }
        }
    }

    fun clearCarriers() {
        carrierList.clear()
        options = options.copy(kNeeded = 1)
    }

    fun autoUpscaleUndersizedCarriers(context: Context, targetShareSize: Int) {
        viewModelScope.launch {
            try {
                isCarrierLoading = true
                carrierLoadingStatus = "Rescaling carriers to required size..."
                for (idx in carrierList.indices) {
                    if (carrierList[idx].capacityBytes < targetShareSize) {
                        val upscaled = StegoEngine.upscaleCarrierImage(
                            context = context,
                            carrier = carrierList[idx],
                            targetCapacityBytes = (targetShareSize * 1.15).toInt()
                        )
                        carrierList[idx] = upscaled
                    }
                }
                HapticHelper.success(context)
                Toast.makeText(context, "Carriers upscaled to required dimensions!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(context, "Upscaling failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isCarrierLoading = false
                carrierLoadingStatus = ""
            }
        }
    }

    fun startEmbedding(context: Context, onResultGenerated: (EmbedResult) -> Unit) {
        if (isEmbedding) return

        if (passphrase.length < 8) {
            HapticHelper.error(context)
            Toast.makeText(context, "Passphrase must be at least 8 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (carrierList.isEmpty()) {
            HapticHelper.error(context)
            Toast.makeText(context, "Please select at least 1 carrier image", Toast.LENGTH_SHORT).show()
            return
        }

        val finalPayloadBytes = if (secretType == "TEXT") {
            if (secretText.isBlank()) {
                HapticHelper.error(context)
                Toast.makeText(context, "Secret message is empty", Toast.LENGTH_SHORT).show()
                return
            }
            secretText.toByteArray(Charsets.UTF_8)
        } else {
            if (secretBytes == null || secretBytes!!.isEmpty()) {
                HapticHelper.error(context)
                Toast.makeText(context, "Please choose a secret file", Toast.LENGTH_SHORT).show()
                return
            }
            secretBytes!!
        }

        val finalName = if (secretType == "TEXT") "secret_message.txt" else secretFileName
        HapticHelper.heavy(context)

        viewModelScope.launch {
            isEmbedding = true
            try {
                val result = StegoEngine.embedMaster(
                    context = context.applicationContext,
                    secretBytes = finalPayloadBytes,
                    secretFileName = finalName,
                    carrierImages = carrierList.toList(),
                    options = options.copy(passphrase = passphrase),
                    onProgress = { progress = it }
                )
                latestResult = result
                onResultGenerated(result)
                HapticHelper.success(context)
                Toast.makeText(context, "Veiled successfully into ${result.outputFiles.size} carriers!", Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                progress = StegoProgress()
                if (e is CancellationException) return@launch
                HapticHelper.error(context)
                val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                Toast.makeText(context, "Embedding failed: $msg", Toast.LENGTH_LONG).show()
            } finally {
                isEmbedding = false
            }
        }
    }
}
