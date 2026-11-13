package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.crypto.StegoEngine
import com.example.hardware.HardwareAccelerator
import com.example.model.CarrierImage
import com.example.model.EmbedOptions
import com.example.model.EmbedResult
import com.example.model.StegoProgress
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeilScreen(
    onResultGenerated: (EmbedResult) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var secretType by remember { mutableStateOf("TEXT") } // "TEXT" or "FILE"
    var secretText by remember { mutableStateOf("") }
    var secretFileName by remember { mutableStateOf("") }
    var secretBytes by remember { mutableStateOf<ByteArray?>(null) }

    val carrierList = remember { mutableStateListOf<CarrierImage>() }
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }

    var options by remember { mutableStateOf(EmbedOptions()) }
    var showAdvancedFlags by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(StegoProgress()) }

    var latestResult by remember { mutableStateOf<EmbedResult?>(null) }
    var conversionNotice by remember { mutableStateOf<String?>(null) }
    var isCarrierLoading by remember { mutableStateOf(false) }
    var carrierLoadingStatus by remember { mutableStateOf("") }

    val secretFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val cr = context.contentResolver
                    var name = "secret_payload.dat"
                    cr.query(it, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) name = cursor.getString(idx)
                        }
                    }
                    val bytes = cr.openInputStream(it)?.use { s -> s.readBytes() }
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
    }

    val carrierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
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
    }

    StegoProgressDialog(progress = progress)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Minimalist Hero Header
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
                            text = "VEIL",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Distributed Shamir Steganography",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandSecondary
                        )
                    }

                    // Refined badge
                    Surface(
                        color = Cyprus.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "K-OF-N VAULT",
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

        // Clean Auto-converted notification banner (if active)
        if (conversionNotice != null) {
            item {
                Surface(
                    color = Cyprus.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = LimeSprout,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = conversionNotice ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sand,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { conversionNotice = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SandSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 1. Secret Payload Card
        item {
            GlassCard {
                SectionHeader(
                    title = "Secret Payload",
                    subtitle = "Data to encrypt and conceal",
                    icon = Icons.Default.Lock,
                    iconTint = LimeSprout
                )

                // Clean Segmented Tabs (Text vs File)
                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (secretType == "TEXT") Cyprus else Color.Transparent)
                                .clickable {
                                    HapticHelper.click(context)
                                    secretType = "TEXT"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Confidential Text",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (secretType == "TEXT") Sand else SandSecondary,
                                fontWeight = if (secretType == "TEXT") FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (secretType == "FILE") Cyprus else Color.Transparent)
                                .clickable {
                                    HapticHelper.click(context)
                                    secretType = "FILE"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Secret Document / File",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (secretType == "FILE") Sand else SandSecondary,
                                fontWeight = if (secretType == "FILE") FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (secretType == "TEXT") {
                    OutlinedTextField(
                        value = secretText,
                        onValueChange = { secretText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secret_text_input"),
                        placeholder = { Text("Enter confidential message, seed phrase, or tokens...", color = SandMuted) },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LimeSprout,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                            focusedTextColor = Sand,
                            unfocusedTextColor = Sand
                        )
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .clickable { secretFilePicker.launch("*/*") }
                            .padding(16.dp),
                        color = ObsidianSurface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (secretFileName.isNotBlank()) secretFileName else "Tap to choose a secret file",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (secretFileName.isNotBlank()) Sand else SandMuted
                                )
                                Text(
                                    text = if (secretBytes != null) "${secretBytes!!.size / 1024} KB loaded" else "PDF, TXT, DOCX, ZIP, JSON supported",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandSecondary
                                )
                            }
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = LimeSprout)
                        }
                    }
                }

                // Payload Telemetry & Capacity Requirement
                val payloadSize = if (secretType == "TEXT") secretText.toByteArray(Charsets.UTF_8).size else (secretBytes?.size ?: 0)
                if (payloadSize > 0) {
                    val approxEncryptedSize = payloadSize + 44 + 81 // Overhead + header
                    val minCarrierPixels = ((approxEncryptedSize * 8) / (2 * 3)).coerceAtLeast(10_000)
                    val minDimSide = sqrt(minCarrierPixels.toDouble()).toInt().coerceAtLeast(64)
                    val minCarrierKb = (approxEncryptedSize / 1024) + 1

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Cyprus.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = LimeSprout, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Carrier Capacity Requirement",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LimeSprout
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Min. Carrier Resolution: ~${minDimSide}×${minDimSide} px (~$minCarrierKb KB raw capacity)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sand
                            )
                            val nLoaded = carrierList.size
                            if (nLoaded > 0) {
                                val shareSize = approxEncryptedSize
                                val hasUnderCapacity = carrierList.any { it.capacityBytes < shareSize }
                                if (hasUnderCapacity) {
                                    Text(
                                        text = "• Caution: One or more selected carriers is smaller than $minCarrierKb KB.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AlertRed
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AppleGlassSecondaryButton(
                                        text = "Auto-Upscale Undersized Carriers",
                                        onClick = {
                                            HapticHelper.heavy(context)
                                            coroutineScope.launch {
                                                try {
                                                    isCarrierLoading = true
                                                    carrierLoadingStatus = "Rescaling carriers to required size..."
                                                    for (idx in carrierList.indices) {
                                                        if (carrierList[idx].capacityBytes < shareSize) {
                                                            val upscaled = StegoEngine.upscaleCarrierImage(
                                                                context = context,
                                                                carrier = carrierList[idx],
                                                                targetCapacityBytes = (shareSize * 1.15).toInt()
                                                            )
                                                            carrierList[idx] = upscaled
                                                        }
                                                    }
                                                    HapticHelper.success(context)
                                                    Toast.makeText(context, "Carriers upscaled to required dimensions!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Upscaling failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isCarrierLoading = false
                                                    carrierLoadingStatus = ""
                                                }
                                            }
                                        },
                                        icon = Icons.Default.AspectRatio,
                                        iconTint = LimeSprout,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "• All $nLoaded selected carrier(s) satisfy the minimum capacity requirement.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SandSecondary
                                    )
                                }
                            } else {
                                Text(
                                    text = "• For Shamir secret sharing (K-of-N), each carrier must be at least ~$minCarrierKb KB in capacity.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Carrier Images Card
        item {
            GlassCard {
                SectionHeader(
                    title = "Carrier Substrate",
                    subtitle = "${carrierList.size} carrier image(s) loaded",
                    icon = Icons.Default.Collections,
                    iconTint = LimeSprout
                )

                // Select Carriers Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppleGlassSecondaryButton(
                        text = "Add Carriers (JPG/PNG)",
                        onClick = { carrierPicker.launch("image/*") },
                        icon = Icons.Default.AddPhotoAlternate,
                        iconTint = LimeSprout,
                        modifier = Modifier.weight(1f),
                        testTag = "add_carrier_button"
                    )

                    if (carrierList.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.click(context)
                                carrierList.clear()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear all", tint = AlertRed)
                        }
                    }
                }

                if (isCarrierLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = carrierLoadingStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LimeSprout,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = LimeSprout,
                                trackColor = CyprusDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Auto JPG to PNG toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            options = options.copy(autoConvertJpgToPng = !options.autoConvertJpgToPng)
                            HapticHelper.click(context)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = options.autoConvertJpgToPng,
                        onCheckedChange = {
                            options = options.copy(autoConvertJpgToPng = it)
                            HapticHelper.click(context)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = LimeSprout,
                            checkmarkColor = ObsidianBlack,
                            uncheckedColor = SandMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Auto-convert JPG to PNG on upload",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Sand
                        )
                        Text(
                            text = "Decodes to lossless ARGB_8888; protects sensitive bit-planes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandSecondary
                        )
                    }
                }

                if (carrierList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    carrierList.forEachIndexed { index, carrier ->
                        CarrierItemCard(
                            carrier = carrier,
                            index = index,
                            total = carrierList.size,
                            onRemove = {
                                HapticHelper.click(context)
                                carrierList.removeAt(index)
                                if (options.kNeeded > carrierList.size) {
                                    options = options.copy(kNeeded = max(1, carrierList.size))
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Security & Threshold Card
        item {
            GlassCard {
                SectionHeader(
                    title = "Passphrase & Threshold",
                    subtitle = "Cryptographic access credentials",
                    icon = Icons.Default.Key,
                    iconTint = LimeSprout
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passphrase_input"),
                    label = { Text("Vault Passphrase (min 8 chars)", color = SandSecondary) },
                    visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                            Icon(
                                imageVector = if (passphraseVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle visibility",
                                tint = SandSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LimeSprout,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface,
                        focusedTextColor = Sand,
                        unfocusedTextColor = Sand
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                val nTotal = max(1, carrierList.size)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Required Shares (K): ${options.kNeeded} of $nTotal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Sand
                        )
                        Text(
                            text = if (options.kNeeded == 1) {
                                "Single carrier mode: any share can unlock the secret."
                            } else {
                                "Any ${options.kNeeded} of $nTotal images must be collected to reconstruct."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandSecondary
                        )
                    }
                }

                Slider(
                    value = options.kNeeded.toFloat(),
                    onValueChange = {
                        options = options.copy(kNeeded = it.toInt())
                    },
                    valueRange = 1f..nTotal.toFloat(),
                    steps = if (nTotal > 1) nTotal - 2 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = LimeSprout,
                        activeTrackColor = LimeSprout,
                        inactiveTrackColor = CyprusDark
                    )
                )
            }
        }

        // 4. Advanced Security Parameters (Accordion Folded to prevent clutter)
        item {
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticHelper.click(context)
                            showAdvancedFlags = !showAdvancedFlags
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "Adversarial Parameters",
                        subtitle = if (showAdvancedFlags) "Tap to collapse" else "Carrier binding, anti-forensics, zero-header...",
                        icon = Icons.Default.Tune,
                        iconTint = LimeSprout
                    )
                    Icon(
                        imageVector = if (showAdvancedFlags) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = SandSecondary
                    )
                }

                AnimatedVisibility(visible = showAdvancedFlags) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        FlagToggleItem(
                            title = "Carrier Bound",
                            description = "Binds payload key to image sensor MSB fingerprint.",
                            icon = Icons.Default.Fingerprint,
                            checked = options.carrierBound,
                            onCheckedChange = { options = options.copy(carrierBound = it) }
                        )

                        FlagToggleItem(
                            title = "Noise Adaptive",
                            description = "Embeds more bits in high-frequency edge textures.",
                            icon = Icons.Default.Grain,
                            checked = options.noiseAdaptive,
                            onCheckedChange = { options = options.copy(noiseAdaptive = it) }
                        )

                        FlagToggleItem(
                            title = "Semantic Camouflage",
                            description = "Blends payload bits with luminance contrast boundaries.",
                            icon = Icons.Default.Psychology,
                            checked = options.semanticCamo,
                            onCheckedChange = { options = options.copy(semanticCamo = it) }
                        )

                        FlagToggleItem(
                            title = "Anti-Forensic Poisoning",
                            description = "Injects camera-sensor noise profile to defeat steganalysis scanners.",
                            icon = Icons.Default.Shield,
                            checked = options.antiForensicPoison,
                            onCheckedChange = { options = options.copy(antiForensicPoison = it) },
                            activeColor = LimeSprout
                        )

                        FlagToggleItem(
                            title = "Stateless Zero-Header Mode",
                            description = "Removes magic bytes; uses implicit keyed marker.",
                            icon = Icons.Default.VisibilityOff,
                            checked = options.statelessMode,
                            onCheckedChange = { options = options.copy(statelessMode = it) }
                        )

                        FlagToggleItem(
                            title = "Real Self-Destruct",
                            description = "Irreversibly poisons embedded bits after max failed decryption attempts.",
                            icon = Icons.Default.Warning,
                            checked = options.selfDestruct,
                            onCheckedChange = { options = options.copy(selfDestruct = it) },
                            isDestructive = true
                        )

                        if (options.selfDestruct) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = ObsidianBlack,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Self-Destruct Threshold:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Sand
                                        )
                                        Text(
                                            text = "${options.maxAttempts} failed attempt(s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AlertRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Slider(
                                        value = options.maxAttempts.toFloat(),
                                        onValueChange = {
                                            options = options.copy(maxAttempts = it.toInt())
                                        },
                                        valueRange = 1f..10f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = AlertRed,
                                            activeTrackColor = AlertRed,
                                            inactiveTrackColor = CyprusDark
                                        )
                                    )
                                    Text(
                                        text = "Warning: The carrier file will be completely corrupted with noise after ${options.maxAttempts} failed decryption passphrases.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SandSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Primary Action: Apple-style Solid Glass Button
        item {
            AppleGlassButton(
                text = "VEIL & ENCRYPT SUBSTRATE",
                onClick = {
                    if (passphrase.length < 8) {
                        HapticHelper.error(context)
                        Toast.makeText(context, "Passphrase must be at least 8 characters", Toast.LENGTH_SHORT).show()
                        return@AppleGlassButton
                    }
                    if (carrierList.isEmpty()) {
                        HapticHelper.error(context)
                        Toast.makeText(context, "Please select at least 1 carrier image", Toast.LENGTH_SHORT).show()
                        return@AppleGlassButton
                    }

                    val finalPayloadBytes = if (secretType == "TEXT") {
                        if (secretText.isBlank()) {
                            HapticHelper.error(context)
                            Toast.makeText(context, "Secret message is empty", Toast.LENGTH_SHORT).show()
                            return@AppleGlassButton
                        }
                        secretText.toByteArray(Charsets.UTF_8)
                    } else {
                        if (secretBytes == null || secretBytes!!.isEmpty()) {
                            HapticHelper.error(context)
                            Toast.makeText(context, "Please choose a secret file", Toast.LENGTH_SHORT).show()
                            return@AppleGlassButton
                        }
                        secretBytes!!
                    }

                    val finalName = if (secretType == "TEXT") "secret_message.txt" else secretFileName
                    HapticHelper.heavy(context)

                    coroutineScope.launch {
                        try {
                            val result = StegoEngine.embedMaster(
                                context = context,
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
                        }
                    }
                },
                icon = Icons.Default.Lock,
                iconTint = LimeSprout,
                testTag = "embed_action_button"
            )
        }

        // Results Card
        latestResult?.let { res ->
            item {
                GlassCard(
                    borderColor = LimeSprout.copy(alpha = 0.5f)
                ) {
                    SectionHeader(
                        title = "Embedding Complete",
                        subtitle = "Generated ${res.outputFiles.size} carrier images (K=${res.kNeeded})",
                        icon = Icons.Default.CheckCircle,
                        iconTint = LimeSprout
                    )

                    Text(
                        text = "Share Set ID: #${res.shareId}",
                        style = MaterialTheme.typography.labelLarge,
                        color = LimeSprout
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total Payload: ${res.capacityUsed} bytes embedded across ${res.nTotal} carriers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    res.outputFiles.forEachIndexed { idx, file ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Sand,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Share #${idx + 1} of ${res.nTotal} • ${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SandSecondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            HapticHelper.click(context)
                                            saveToGallery(context, file)
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Save to Gallery", tint = LimeSprout)
                                    }

                                    IconButton(
                                        onClick = {
                                            HapticHelper.click(context)
                                            shareFile(context, file)
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = LimeSprout)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun saveToGallery(context: Context, file: File) {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ZyloVeil")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Cannot create MediaStore entry")

        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Toast.makeText(context, "Saved to Pictures/ZyloVeil in Gallery!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
        Toast.makeText(context, "Failed to save: $msg", Toast.LENGTH_LONG).show()
    }
}

fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Stego Carrier"))
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        }
        context.startActivity(Intent.createChooser(intent, "Share Stego Carrier"))
    }
}
