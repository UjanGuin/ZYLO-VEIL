package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.HardwareAccelerator
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*

@Composable
fun AboutScreen() {
    val hardware = remember { HardwareAccelerator.profile }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Summary Card
        item {
            GlassCard(
                borderColor = LimeSprout.copy(alpha = 0.35f),
                backgroundColor = ObsidianCard
            ) {
                SectionHeader(
                    title = "ZYLO VEIL v3",
                    subtitle = "Cryptographic Steganography Architecture",
                    icon = Icons.Default.Security,
                    iconTint = LimeSprout
                )
                Text(
                    text = "ZYLO VEIL is a research-grade cryptographic steganography system that treats carrier images as a distributed, adaptive cryptographic substrate. Payloads are encrypted, split via Shamir's Secret Sharing (K-of-N), adaptively embedded based on local noise, and protected with anti-forensic countermeasures.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )
            }
        }

        // Hardware Acceleration & SoC Telemetry
        item {
            GlassCard(
                borderColor = CyprusLight.copy(alpha = 0.4f)
            ) {
                SectionHeader(
                    title = "Hardware Acceleration Engine",
                    subtitle = "Snapdragon & MediaTek Processor Optimization",
                    icon = Icons.Default.Memory,
                    iconTint = LimeSprout
                )

                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detected SoC Platform:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandSecondary,
                                modifier = Modifier.weight(0.42f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(0.58f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Surface(
                                    color = Cyprus.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = hardware.processorFamily.displayName,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = LimeSprout
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        HardwareInfoRow(label = "Hardware Board / SoC", value = hardware.socModel)
                        HardwareInfoRow(label = "CPU Concurrency", value = "${hardware.cpuCores} Threads Active (Chunked Parallel Processing)")
                        HardwareInfoRow(label = "GPU Acceleration", value = hardware.gpuPipeline)
                        HardwareInfoRow(label = "NPU / Vector Unit", value = hardware.npuEngine)
                        HardwareInfoRow(label = "Vector Extensions", value = hardware.vectorExtension)
                        HardwareInfoRow(label = "App Heap Limit", value = "${hardware.memoryLimitMb} MB (Large Heap Enabled)")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "• Snapdragon: Optimized for Kryo core topologies and Hexagon DSP acceleration for fast GF(256) arithmetic.\n• MediaTek: Multi-cluster parallel threads for real-time luminance cross-difference edge scoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )
            }
        }

        item {
            GlassCard {
                SectionHeader(
                    title = "Core Capabilities",
                    subtitle = "Engine features & cryptographic primitives",
                    icon = Icons.Default.Star,
                    iconTint = LimeSprout
                )

                FeatureItem(
                    icon = Icons.Default.AutoFixHigh,
                    title = "Auto JPG to PNG Conversion",
                    desc = "Automatically converts lossy JPEG carriers to lossless ARGB PNG during upload to prevent lossy compression from destroying LSB bit-planes.",
                    color = LimeSprout
                )

                FeatureItem(
                    icon = Icons.Default.Hub,
                    title = "True Shamir Split (K-of-N)",
                    desc = "Payload is encrypted first, then split across N carrier images. Any K shares reconstruct the master key and ciphertext.",
                    color = CyprusLight
                )

                FeatureItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Carrier Binding",
                    desc = "Key derivation mixes the carrier image's MSB sensor fingerprint into the salt. Modifying the carrier invalidates decryption.",
                    color = Sand
                )

                FeatureItem(
                    icon = Icons.Default.Shield,
                    title = "Anti-Forensic Sensor Noise",
                    desc = "Injects Gaussian-like camera sensor noise into unused color channels to defeat chi-squared and sample-pair steganalysis.",
                    color = LimeSprout
                )

                FeatureItem(
                    icon = Icons.Default.VisibilityOff,
                    title = "Stateless Zero-Header Mode",
                    desc = "Eliminates all magic bytes and explicit headers. Extraction uses deterministic, keyed implicit markers derived from the passphrase and image.",
                    color = CyprusLight
                )

                FeatureItem(
                    icon = Icons.Default.Warning,
                    title = "Real Self-Destruct",
                    desc = "After N failed extraction attempts, embedded bits are permanently and irreversibly poisoned with keyed noise.",
                    color = AlertRed
                )
            }
        }

        item {
            GlassCard {
                SectionHeader(
                    title = "Threat Model",
                    subtitle = "Formal security assumptions",
                    icon = Icons.Default.Policy,
                    iconTint = LimeSprout
                )

                Text(
                    text = "Defends Against:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LimeSprout
                )
                Text(
                    text = "• Casual visual inspection\n• LSB plane bit extraction\n• Naive sample-pair & Chi-squared steganalysis\n• Compromise of fewer than K carrier shares\n• Delayed replay of old stego copies (via bit migration)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sand
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Does NOT Defend Against:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AlertRed
                )
                Text(
                    text = "• Full plaintext knowledge of both pristine carrier & payload\n• Active recompression (e.g. converting carrier to low-quality JPEG)\n• Compromise of the master passphrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )
            }
        }
    }
}

@Composable
fun HardwareInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SandSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Sand,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    desc: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Cyprus.copy(alpha = 0.35f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Sand)
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = SandSecondary)
        }
    }
}
