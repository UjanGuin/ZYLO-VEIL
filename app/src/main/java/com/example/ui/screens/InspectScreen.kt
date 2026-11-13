package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.StegoEngine
import com.example.model.AnalysisReport
import com.example.model.CarrierSetVerificationReport
import com.example.model.BenchmarkReport
import com.example.model.VerificationStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@Composable
fun InspectScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var analyzedFile by remember { mutableStateOf<File?>(null) }
    var report by remember { mutableStateOf<AnalysisReport?>(null) }
    var lsbPlaneBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var verifyPassphrase by remember { mutableStateOf("") }
    var verificationReport by remember { mutableStateOf<CarrierSetVerificationReport?>(null) }
    var benchmarkReport by remember { mutableStateOf<BenchmarkReport?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isAnalyzing = true
                HapticHelper.click(context)
                try {
                    val cr = context.contentResolver
                    val tempFile = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.png")
                    cr.openInputStream(it)?.use { input ->
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

    val verifyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isVerifying = true
                try {
                    val files = uris.mapIndexed { index, uri ->
                        val file = File(context.cacheDir, "verify_${System.currentTimeMillis()}_$index.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        } ?: throw IllegalArgumentException("Cannot read selected carrier")
                        file
                    }
                    verificationReport = StegoEngine.verifyCarrierSet(files, verifyPassphrase)
                    benchmarkReport = StegoEngine.benchmarkCarriers(files, verifyPassphrase)
                } catch (e: Throwable) {
                    if (e is CancellationException) return@launch
                    Toast.makeText(context, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isVerifying = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INSPECT",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Bit-Plane Shannon Entropy Scanner",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandSecondary
                        )
                    }

                    Surface(
                        color = Cyprus.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "STEGANALYSIS",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = LimeSprout,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionHeader(
                    title = "Carrier Telemetry",
                    subtitle = "Examine raw LSB noise distribution",
                    icon = Icons.Default.Analytics,
                    iconTint = LimeSprout
                )

                Text(
                    text = "Detects artificial perturbation, sensor camera noise, and high-entropy steganographic ciphertext hidden in image bit-planes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppleGlassButton(
                    text = "Select Image to Inspect",
                    onClick = { imagePicker.launch("image/*") },
                    icon = Icons.Default.Upload,
                    iconTint = LimeSprout,
                    testTag = "inspect_select_image_button"
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Verify-only mode authenticates headers and encrypted share metadata, checks format/capacity, and validates set membership without extracting the secret or mutating carriers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SandSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = verifyPassphrase,
                    onValueChange = { verifyPassphrase = it },
                    modifier = Modifier.fillMaxWidth().testTag("verify_passphrase_input"),
                    label = { Text("Passphrase for verification", color = SandSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LimeSprout,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = Sand,
                        unfocusedTextColor = Sand
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppleGlassButton(
                    text = if (isVerifying) "Verifying..." else "Verify Carrier Set",
                    onClick = {
                        if (verifyPassphrase.isBlank()) {
                            Toast.makeText(context, "Enter the passphrase to verify authenticated metadata", Toast.LENGTH_SHORT).show()
                        } else {
                            verifyPicker.launch("image/*")
                        }
                    },
                    icon = Icons.Default.VerifiedUser,
                    iconTint = LimeSprout,
                    testTag = "verify_carrier_set_button"
                )
            }
        }

        if (isAnalyzing) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LimeSprout)
                }
            }
        }

        report?.let { rep ->
            item {
                GlassCard {
                    SectionHeader(
                        title = "Entropy Report",
                        subtitle = "${rep.width} × ${rep.height} px (${rep.totalPixels} pixels)",
                        icon = Icons.Default.Info,
                        iconTint = LimeSprout
                    )

                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Capacity (2 BPC):", style = MaterialTheme.typography.bodyMedium, color = SandSecondary)
                                Text("${rep.estimatedCapacityBytes / 1024} KB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Sand)
                            }
                            Column {
                                Text("Texture Variance:", style = MaterialTheme.typography.bodyMedium, color = SandSecondary)
                                Text("%.1f".format(rep.averageLuminanceVariance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Sand)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "LSB Bit-Plane Shannon Entropy (0.0 to 1.0):",
                        style = MaterialTheme.typography.labelMedium,
                        color = Sand
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    EntropyBar(label = "Red Channel LSB", value = rep.redLsbEntropy, color = LimeSprout)
                    EntropyBar(label = "Green Channel LSB", value = rep.greenLsbEntropy, color = CyprusLight)
                    EntropyBar(label = "Blue Channel LSB", value = rep.blueLsbEntropy, color = FreshCanopyLight)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Benchmark & steganalysis indicators",
                        style = MaterialTheme.typography.titleMedium,
                        color = Sand,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Capacity: ${rep.estimatedCapacityBytes / 1024} KB (estimated from pixels)", color = SandSecondary)
                    Text("Visual distortion: %.2f%% (estimated; original carrier not available)".format(rep.estimatedVisualDistortionPercent), color = SandSecondary)
                    Text("Statistical detectability indicator: %.3f (heuristic, not a resistance guarantee)".format(rep.estimatedDetectabilityScore), color = SandSecondary)
                    Text("Extraction reliability: ${rep.extractionReliability}", color = SandSecondary)

                    if (rep.isStatelessLikely) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Cyprus.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = LimeSprout, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "High uniform entropy detected (>0.98). Signature matches encrypted stego payload or anti-forensic perturbation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand
                                )
                            }
                        }

                    }
                }
            }

            verificationReport?.let { verification ->
                item {
                    GlassCard {
                        SectionHeader(
                            title = "Verify-only result",
                            subtitle = if (verification.passed) "No secret plaintext extracted and no carrier mutation performed" else "One or more checks failed",
                            icon = if (verification.passed) Icons.Default.Verified else Icons.Default.Warning,
                            iconTint = if (verification.passed) LimeSprout else Color(0xFFFFB74D)
                        )
                        verification.checks.forEach { check ->
                            val color = when (check.status) {
                                VerificationStatus.PASS -> LimeSprout
                                VerificationStatus.WARNING -> Color(0xFFFFB74D)
                                VerificationStatus.FAIL -> Color(0xFFFF6B6B)
                            }
                            Text("${check.status}: ${check.name}", color = color, fontWeight = FontWeight.Bold)
                            Text(check.detail, color = SandSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        verification.carriers.forEach { carrier ->
                            Text(carrier.fileName, color = Sand, fontWeight = FontWeight.SemiBold)
                            carrier.checks.forEach { check ->
                                Text("  ${check.status}: ${check.name} — ${check.detail}", color = SandSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            benchmarkReport?.let { benchmark ->
                item {
                    GlassCard {
                        SectionHeader(
                            title = "Benchmark & Steganalysis Report",
                            subtitle = "Per-carrier measurements and estimates",
                            icon = Icons.Default.Assessment,
                            iconTint = LimeSprout
                        )
                        Text(
                            text = "Aggregate available capacity: ${benchmark.aggregateAvailableCapacityBytes / 1024} KB",
                            color = Sand
                        )
                        Text(
                            text = "Aggregate used capacity: ${benchmark.aggregateUsedCapacityBytes?.let { "${it / 1024} KB (measured after verification)" } ?: "Not measured"}",
                            color = SandSecondary
                        )
                        Text(
                            text = "Repeated verification reliability: ${benchmark.aggregateVerificationReliability?.let { "%.1f%% measured".format(it * 100f) } ?: "Not measured"}",
                            color = SandSecondary
                        )
                        Text(
                            text = "Changed-channel ratio: ${benchmark.aggregateChangedChannelRatio?.let { "%.3f measured".format(it) } ?: "Not measured (original carrier required)"}",
                            color = SandSecondary
                        )
                        Text(
                            text = "Mean pixel error: ${benchmark.aggregateMeanAbsolutePixelError?.let { "%.3f measured".format(it) } ?: "Not measured (original carrier required)"}",
                            color = SandSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        benchmark.carriers.forEach { carrier ->
                            Text(carrier.fileName, color = Sand, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Capacity: ${carrier.availableCapacityBytes / 1024} KB available; ${carrier.usedCapacityBytes?.let { "${it / 1024} KB used" } ?: "used not measured"}",
                                color = SandSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Verification: ${carrier.verificationSuccesses}/${carrier.verificationTrials} trials; LSB entropy %.3f; detectability indicator %.3f".format(
                                    carrier.lsbEntropy,
                                    carrier.detectabilityIndicator
                                ),
                                color = SandSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Changed-channel ratio: not measured; pixel error: not measured (original carrier unavailable)",
                                color = SandSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = benchmark.disclaimer,
                            color = Color(0xFFFFB74D),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            lsbPlaneBitmap?.let { bmp ->
                item {
                    GlassCard {
                        SectionHeader(
                            title = "Visual LSB Bit-Plane Rendering",
                            subtitle = "Binary representation of the lowest bit-plane",
                            icon = Icons.Default.Visibility,
                            iconTint = LimeSprout
                        )

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "LSB Bit-plane",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntropyBar(label: String, value: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SandSecondary)
            Text("%.4f".format(value), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = ObsidianSurface
        )
    }
}
