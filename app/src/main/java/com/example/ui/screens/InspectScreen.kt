package com.example.ui.screens

import android.net.Uri
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AnalysisReport
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun InspectScreen(
    viewModel: InspectViewModel = viewModel()
) {
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.analyzeImage(context, it)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
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
            }
        }

        if (viewModel.isAnalyzing) {
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

        viewModel.report?.let { rep ->
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
                                    text = "High uniform entropy detected (>0.92). Signature matches encrypted stego payload or anti-forensic perturbation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand
                                )
                            }
                        }
                    }
                }
            }

            viewModel.lsbPlaneBitmap?.let { bmp ->
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
