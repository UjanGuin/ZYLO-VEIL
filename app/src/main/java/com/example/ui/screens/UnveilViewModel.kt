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
import com.example.model.ExtractResult
import com.example.model.StegoProgress
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class UnveilViewModel : ViewModel() {
    val stegoFileList = mutableStateListOf<File>()
    var passphrase by mutableStateOf("")
    var passphraseVisible by mutableStateOf(false)
    var allowMutation by mutableStateOf(false)

    var progress by mutableStateOf(StegoProgress())
    var extractionResult by mutableStateOf<ExtractResult?>(null)
    var extractedTextPreview by mutableStateOf<String?>(null)
    var isStegoLoading by mutableStateOf(false)
    var stegoLoadingStatus by mutableStateOf("")
    var isExtracting by mutableStateOf(false)

    fun loadStegoFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            isStegoLoading = true
            stegoLoadingStatus = "Loading ${uris.size} stego carrier(s)..."
            try {
                for ((index, uri) in uris.withIndex()) {
                    stegoLoadingStatus = "Importing stego carrier ${index + 1} of ${uris.size}..."
                    try {
                        val cr = context.contentResolver
                        var name = "stego_${System.currentTimeMillis()}.png"
                        cr.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx != -1) name = cursor.getString(idx)
                            }
                        }

                        val tempFile = File(context.cacheDir, "extract_${name}")
                        cr.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (tempFile.exists() && tempFile.length() > 0) {
                            stegoFileList.add(tempFile)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                        Toast.makeText(context, "Error importing file: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
                HapticHelper.click(context)
            } finally {
                isStegoLoading = false
                stegoLoadingStatus = ""
            }
        }
    }

    fun removeStegoFile(index: Int) {
        if (index in stegoFileList.indices) {
            stegoFileList.removeAt(index)
        }
    }

    fun clearStegoFiles() {
        stegoFileList.clear()
        extractionResult = null
        extractedTextPreview = null
    }

    fun startExtraction(context: Context) {
        if (isExtracting) return

        if (stegoFileList.isEmpty()) {
            HapticHelper.error(context)
            Toast.makeText(context, "Select at least 1 stego carrier image", Toast.LENGTH_SHORT).show()
            return
        }
        if (passphrase.isEmpty()) {
            HapticHelper.error(context)
            Toast.makeText(context, "Please enter the passphrase", Toast.LENGTH_SHORT).show()
            return
        }

        HapticHelper.heavy(context)

        viewModelScope.launch {
            isExtracting = true
            try {
                val result = StegoEngine.extractMaster(
                    stegoFiles = stegoFileList.toList(),
                    passphrase = passphrase,
                    allowMutation = allowMutation,
                    onProgress = { progress = it }
                )
                extractionResult = result

                try {
                    val text = String(result.data, Charsets.UTF_8)
                    val isPrintable = text.all { it.isLetterOrDigit() || it.isWhitespace() || "!@#$%^&*()_+-=[]{}|;':\",./<>?`~".contains(it) }
                    extractedTextPreview = if (isPrintable && text.length < 50_000) text else null
                } catch (e: Exception) {
                    extractedTextPreview = null
                }

                HapticHelper.success(context)
                Toast.makeText(context, "Extracted '${result.filename}' successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                progress = StegoProgress()
                if (e is CancellationException) return@launch
                HapticHelper.error(context)
                val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                Toast.makeText(context, "Extraction error: $msg", Toast.LENGTH_LONG).show()
            } finally {
                isExtracting = false
            }
        }
    }
}
