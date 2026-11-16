package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ExtractResult
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.HapticHelper
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnveilScreen(
    viewModel: UnveilViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val stegoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.loadStegoFiles(context, uris)
        }
    }

    StegoProgressDialog(progress = viewModel.progress)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
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
                    subtitle = "${viewModel.stegoFileList.size} carrier image(s) loaded",
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

                    if (viewModel.stegoFileList.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.click(context)
                                viewModel.clearStegoFiles()
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

                if (viewModel.isStegoLoading) {
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
                                    text = viewModel.stegoLoadingStatus,
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
        if (viewModel.stegoFileList.isNotEmpty()) {
            itemsIndexed(viewModel.stegoFileList) { idx, file ->
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
                            viewModel.removeStegoFile(idx)
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
                    value = viewModel.passphrase,
                    onValueChange = { viewModel.passphrase = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_passphrase_input"),
                    label = { Text("Passphrase", color = SandSecondary) },
                    visualTransformation = if (viewModel.passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.passphraseVisible = !viewModel.passphraseVisible }) {
                            Icon(
                                imageVector = if (viewModel.passphraseVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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

                Spacer(modifier = Modifier.height(14.dp))

                FlagToggleItem(
                    title = "Stego Mutation Mode",
                    description = "Rotates seeds upon extraction; invalidates past stego copies.",
                    icon = Icons.Default.Autorenew,
                    checked = viewModel.allowMutation,
                    onCheckedChange = { viewModel.allowMutation = it }
                )
            }
        }

        // Primary Action: Apple-style Solid Glass Button
        item {
            AppleGlassButton(
                text = if (viewModel.isExtracting) "UNVEILING SECRET..." else "UNVEIL & RECONSTRUCT SECRET",
                enabled = !viewModel.isExtracting,
                onClick = {
                    viewModel.startExtraction(context)
                },
                icon = Icons.Default.Key,
                iconTint = LimeSprout,
                testTag = "extract_action_button"
            )
        }

        // Extracted Result Card
        viewModel.extractionResult?.let { res ->
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
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Share Set ID: #${head.shareId} (K=${head.kNeeded}, N=${head.nTotal})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LimeSprout,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Total Payload: ${res.data.size} bytes recovered.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Text Content Preview (if text)
                    viewModel.extractedTextPreview?.let { textContent ->
                        Text(
                            text = "Extracted Text Preview:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Sand
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = textContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sand,
                                modifier = Modifier.padding(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AppleGlassSecondaryButton(
                            text = "Copy Secret to Clipboard",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(textContent))
                                HapticHelper.click(context)
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            icon = Icons.Default.ContentCopy,
                            iconTint = LimeSprout,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Image Preview if image
                    var previewBitmap by remember(res.filename) {
                        mutableStateOf(
                            try {
                                BitmapFactory.decodeByteArray(res.data, 0, res.data.size)
                            } catch (e: Exception) {
                                null
                            }
                        )
                    }

                    previewBitmap?.let { bmp ->
                        Text(
                            text = "Secret Image Preview:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Sand
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .background(ObsidianSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Secret Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Export / Share Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppleGlassButton(
                            text = "Export File",
                            onClick = {
                                HapticHelper.click(context)
                                exportExtractedFile(context, res)
                            },
                            icon = Icons.Default.SaveAlt,
                            modifier = Modifier.weight(1f)
                        )

                        AppleGlassSecondaryButton(
                            text = "Share",
                            onClick = {
                                HapticHelper.click(context)
                                shareExtractedFile(context, res)
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

fun exportExtractedFile(context: Context, res: ExtractResult) {
    try {
        val mimeType = getMimeType(res.filename)
        val savedLocation: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, res.filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ZyloVeil")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                ?: throw IllegalStateException("Cannot create MediaStore download entry")

            resolver.openOutputStream(uri)?.use { output ->
                output.write(res.data)
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            "Downloads/ZyloVeil/${res.filename}"
        } else {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ZyloVeil"
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outFile = File(downloadsDir, res.filename)
            FileOutputStream(outFile).use { it.write(res.data) }
            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
            "Downloads/ZyloVeil/${res.filename}"
        }

        Toast.makeText(context, "Saved to $savedLocation", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        try {
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fallbackFile = File(fallbackDir, res.filename)
            FileOutputStream(fallbackFile).use { it.write(res.data) }
            Toast.makeText(context, "Saved to ${fallbackFile.name} in App Storage", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            Toast.makeText(context, "Export error: $msg", Toast.LENGTH_LONG).show()
        }
    }
}

private fun getMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mime.isNullOrEmpty()) return mime
    }
    return "application/octet-stream"
}

fun shareExtractedFile(context: Context, res: ExtractResult) {
    try {
        val outDir = context.cacheDir
        val outFile = File(outDir, res.filename)
        FileOutputStream(outFile).use { it.write(res.data) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Extracted Secret"))
    } catch (e: Exception) {
        val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
        Toast.makeText(context, "Share error: $msg", Toast.LENGTH_LONG).show()
    }
}
