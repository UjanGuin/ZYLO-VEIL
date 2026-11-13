package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.crypto.StegoEngine
import com.example.model.ExtractResult
import com.example.model.StegoProgress
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnveilScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val stegoFileList = remember { mutableStateListOf<File>() }
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var allowMutation by remember { mutableStateOf(false) }

    var progress by remember { mutableStateOf(StegoProgress()) }
    var extractionResult by remember { mutableStateOf<ExtractResult?>(null) }
    var extractedTextPreview by remember { mutableStateOf<String?>(null) }
    var isStegoLoading by remember { mutableStateOf(false) }
    var stegoLoadingStatus by remember { mutableStateOf("") }

    val stegoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
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
                            stegoFileList.add(tempFile)
                            HapticHelper.click(context)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
                            Toast.makeText(context, "Error importing carrier: $msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    isStegoLoading = false
                    stegoLoadingStatus = ""
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
        // Minimalist Header
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
                            text = "UNVEIL",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Reconstruct & Decrypt Secret",
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
                            text = "SHAMIR DECODER",
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

        // Stego Carriers Upload Box
        item {
            GlassCard {
                SectionHeader(
                    title = "Stego Carriers",
                    subtitle = "${stegoFileList.size} carrier image(s) loaded",
                    icon = Icons.Default.CloudUpload,
                    iconTint = LimeSprout
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppleGlassSecondaryButton(
                        text = "Add Stego Carriers",
                        onClick = { stegoPicker.launch("image/*") },
                        icon = Icons.Default.AddPhotoAlternate,
                        iconTint = LimeSprout,
                        modifier = Modifier.weight(1f),
                        testTag = "select_stego_carriers_button"
                    )

                    if (stegoFileList.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.click(context)
                                stegoFileList.clear()
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

                if (isStegoLoading) {
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
                                    text = stegoLoadingStatus,
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
            }
        }

        // Selected stego carriers list
        if (stegoFileList.isNotEmpty()) {
            itemsIndexed(stegoFileList) { idx, file ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                    color = ObsidianCardVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Cyprus.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = LimeSprout, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Sand,
                                maxLines = 1
                            )
                            Text(
                                text = "Carrier #${idx + 1} • ${file.length() / 1024} KB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandSecondary
                            )
                        }
                        IconButton(onClick = {
                            HapticHelper.click(context)
                            stegoFileList.removeAt(idx)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = SandMuted)
                        }
                    }
                }
            }
        }

        // Passphrase & Options Card
        item {
            GlassCard {
                SectionHeader(
                    title = "Decryption Key",
                    subtitle = "Passphrase entered during embedding",
                    icon = Icons.Default.VpnKey,
                    iconTint = LimeSprout
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_passphrase_input"),
                    label = { Text("Passphrase", color = SandSecondary) },
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

                Spacer(modifier = Modifier.height(10.dp))

                FlagToggleItem(
                    title = "Stego Mutation Mode",
                    description = "Rotates seeds upon extraction; invalidates past stego copies.",
                    icon = Icons.Default.Autorenew,
                    checked = allowMutation,
                    onCheckedChange = { allowMutation = it }
                )
            }
        }

        // Primary Action: Bold Luxury Button in Lime Sprout
        // Primary Action: Apple-style Solid Glass Button
        item {
            AppleGlassButton(
                text = "UNVEIL & RECONSTRUCT SECRET",
                onClick = {
                    if (stegoFileList.isEmpty()) {
                        HapticHelper.error(context)
                        Toast.makeText(context, "Select at least 1 stego carrier image", Toast.LENGTH_SHORT).show()
                        return@AppleGlassButton
                    }
                    if (passphrase.isEmpty()) {
                        HapticHelper.error(context)
                        Toast.makeText(context, "Please enter the passphrase", Toast.LENGTH_SHORT).show()
                        return@AppleGlassButton
                    }

                    HapticHelper.heavy(context)

                    coroutineScope.launch {
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
                        }
                    }
                },
                icon = Icons.Default.Key,
                iconTint = LimeSprout,
                testTag = "extract_action_button"
            )
        }

        // Extracted Result Card
        extractionResult?.let { res ->
            item {
                GlassCard(
                    borderColor = LimeSprout.copy(alpha = 0.5f)
                ) {
                    SectionHeader(
                        title = "Secret Unveiled",
                        subtitle = res.filename,
                        icon = Icons.Default.FileDownloadDone,
                        iconTint = LimeSprout
                    )

                    res.header?.let { head ->
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Steganographic Telemetry",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LimeSprout
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Shamir Threshold: ${head.kNeeded} of ${head.nTotal} shares • Share ID: #${head.shareId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Sand
                                )
                                Text(
                                    text = "Flags: ${if (head.isCarrierBound) "Carrier-Bound " else ""}${if (head.isNoiseAdaptive) "Noise-Adaptive " else ""}${if (head.isPoisonAntiForensic) "Anti-Forensics " else ""}${if (head.isStateless) "Stateless " else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (extractedTextPreview != null) {
                        Text(
                            text = "Decrypted Text:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Sand
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = extractedTextPreview ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Sand
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    val imageBitmap = remember(res.data) {
                        try {
                            BitmapFactory.decodeByteArray(res.data, 0, res.data.size)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (imageBitmap != null) {
                        Text(
                            text = "Decrypted Image Preview:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Sand
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Decrypted Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (extractedTextPreview != null) {
                            AppleGlassSecondaryButton(
                                text = "Copy Text",
                                onClick = {
                                    HapticHelper.click(context)
                                    clipboardManager.setText(AnnotatedString(extractedTextPreview ?: ""))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Default.ContentCopy,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        AppleGlassButton(
                            text = "Export / Save",
                            onClick = {
                                HapticHelper.click(context)
                                saveAndShareExtracted(context, res.filename, res.data)
                            },
                            icon = Icons.Default.Share,
                            iconTint = LimeSprout,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun saveAndShareExtracted(context: Context, filename: String, data: ByteArray) {
    try {
        val outDir = File(context.filesDir, "extracted_secrets").apply { mkdirs() }
        val outFile = File(outDir, filename)
        FileOutputStream(outFile).use { it.write(data) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save or Export Extracted Secret"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
