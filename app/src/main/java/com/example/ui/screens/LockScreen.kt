package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.BiometricHelper
import com.example.util.HapticHelper

@Composable
fun LockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasscodeFallback by remember { mutableStateOf(false) }
    var enteredPasscode by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "fingerprintPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val triggerBiometric = {
        HapticHelper.click(context)
        if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
            BiometricHelper.promptFingerprintUnlock(
                activity = activity,
                title = "Unlock ZYLO VEIL",
                subtitle = "Biometric confirmation required for steganographic vault",
                onSuccess = {
                    onUnlocked()
                },
                onError = { err ->
                    errorMessage = err
                    showPasscodeFallback = true
                }
            )
        } else {
            showPasscodeFallback = true
            errorMessage = "No enrolled fingerprint found. Use vault key to unlock."
        }
    }

    LaunchedEffect(Unit) {
        if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // Vault Header Badge
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Cyprus.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = LimeSprout,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ZYLO VEIL VAULT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Sand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Biometric Fingerprint Iris Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulseScale)
                    .clickable { triggerBiometric() }
                    .testTag("fingerprint_sensor_target")
            ) {
                // Subtle Ambient Glow matching main UI Cyprus tones
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Cyprus.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Central Fingerprint Button matching ObsidianCard / Cyprus style
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(ObsidianCard)
                        .border(
                            1.5.dp,
                            CyprusLight,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Scan Fingerprint",
                        tint = LimeSprout,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Biometric Lock Engaged",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Sand
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Touch the fingerprint sensor to unlock ZYLO VEIL",
                style = MaterialTheme.typography.bodyMedium,
                color = SandSecondary
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = AlertRedBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlertRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Scan Button: Apple-style Solid Glass Button
            AppleGlassButton(
                text = "Scan Fingerprint",
                onClick = { triggerBiometric() },
                icon = Icons.Default.Fingerprint,
                iconTint = LimeSprout,
                testTag = "scan_fingerprint_button"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Passcode Button
            AppleGlassSecondaryButton(
                text = if (showPasscodeFallback) "Hide Passcode Input" else "Use Vault Passcode",
                onClick = {
                    HapticHelper.click(context)
                    showPasscodeFallback = !showPasscodeFallback
                },
                icon = Icons.Default.VpnKey,
                iconTint = SandSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            // Fallback Passcode Box (ObsidianCard styled to match Main UI)
            if (showPasscodeFallback) {
                Spacer(modifier = Modifier.height(16.dp))
                GlassCard {
                    Text(
                        text = "Backup Vault Passcode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Sand
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enteredPasscode,
                        onValueChange = { enteredPasscode = it },
                        placeholder = { Text("Enter backup vault passcode...", color = SandMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LimeSprout,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                            focusedTextColor = Sand,
                            unfocusedTextColor = Sand
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppleGlassButton(
                        text = "Unlock Vault",
                        onClick = {
                            if (enteredPasscode == "7149") {
                                HapticHelper.success(context)
                                onUnlocked()
                            } else {
                                HapticHelper.error(context)
                                Toast.makeText(context, "Invalid backup passcode", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.Default.LockOpen,
                        iconTint = LimeSprout
                    )
                }
            }
        }
    }
}
