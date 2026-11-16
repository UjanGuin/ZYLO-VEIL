package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.StegoEngine
import com.example.model.AnalysisReport
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class InspectViewModel : ViewModel() {
    var analyzedFile by mutableStateOf<File?>(null)
    var report by mutableStateOf<AnalysisReport?>(null)
    var lsbPlaneBitmap by mutableStateOf<Bitmap?>(null)
    var isAnalyzing by mutableStateOf(false)

    fun analyzeImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            isAnalyzing = true
            HapticHelper.click(context)
            try {
                val cr = context.contentResolver
                val tempFile = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.png")
                cr.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                analyzedFile = tempFile

                val rep = StegoEngine.analyzeImage(tempFile)
                report = rep

                withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply {
                        inScaled = false
                    }
                    val fullBmp = BitmapFactory.decodeFile(tempFile.absolutePath, options) ?: return@withContext

                    val maxDim = 1080
                    val origW = fullBmp.width
                    val origH = fullBmp.height
                    val scale = minOf(1.0f, maxDim.toFloat() / maxOf(origW, origH))
                    val w = (origW * scale).toInt().coerceAtLeast(1)
                    val h = (origH * scale).toInt().coerceAtLeast(1)

                    val scaled = if (scale < 1.0f) {
                        Bitmap.createScaledBitmap(fullBmp, w, h, true)
                    } else {
                        fullBmp
                    }

                    val argb = IntArray(w * h)
                    scaled.getPixels(argb, 0, w, 0, 0, w, h)
                    val lsbPixels = IntArray(w * h)
                    for (i in 0 until (w * h)) {
                        val c = argb[i]
                        val rLsb = (c shr 16) and 1
                        val gLsb = (c shr 8) and 1
                        val bLsb = c and 1
                        val gray = if ((rLsb xor gLsb xor bLsb) == 1) 0xFF else 0x00
                        lsbPixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                    lsbPlaneBitmap = Bitmap.createBitmap(lsbPixels, w, h, Bitmap.Config.ARGB_8888)
                    if (scaled != fullBmp) scaled.recycle()
                    fullBmp.recycle()
                }
                HapticHelper.success(context)
            } catch (e: Throwable) {
                if (e is CancellationException) return@launch
                HapticHelper.error(context)
                val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                Toast.makeText(context, "Analysis failed: $msg", Toast.LENGTH_SHORT).show()
            } finally {
                isAnalyzing = false
            }
        }
    }
}
