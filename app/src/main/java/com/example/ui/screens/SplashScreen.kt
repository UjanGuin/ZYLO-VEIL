package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.HardwareAccelerator
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    onAnimationComplete: () -> Unit
) {
    val hardware = remember { HardwareAccelerator.profile }
    var bootStage by remember { mutableStateOf("INITIALIZING CRYPTOGRAPHIC CORE...") }
    var bootProgress by remember { mutableStateOf(0.1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "irisRings")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRingRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRingRotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val entranceScale = remember { Animatable(0.4f) }
    val entranceAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(1f, animationSpec = tween(600, easing = EaseOutCubic))
        entranceScale.animateTo(1f, animationSpec = tween(800, easing = EaseOutBack))

        delay(350)
        bootStage = "OPTIMIZING FOR ${hardware.processorFamily.displayName.uppercase()}"
        bootProgress = 0.4f

        delay(400)
        bootStage = "ENGAGING ${hardware.npuEngine.uppercase()}"
        bootProgress = 0.7f

        delay(450)
        bootStage = "SYNCHRONIZING SHAMIR GF(256) SUBSTRATE"
        bootProgress = 0.95f

        delay(400)
        bootStage = "ZYLO VEIL SECURE VAULT ONLINE"
        bootProgress = 1.0f

        delay(300)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onAnimationComplete()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(entranceScale.value)
                .alpha(entranceAlpha.value)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                // Ambient Glow
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Cyprus.copy(alpha = glowAlpha * 0.6f),
                                    LimeSprout.copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Outer rotating ring
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .rotate(outerRotation)
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                LimeSprout,
                                Cyprus,
                                FreshCanopy,
                                LimeSprout
                            )
                        ),
                        radius = radius - 6.dp.toPx(),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val ticks = 24
                    for (i in 0 until ticks) {
                        val angle = (i * 360f / ticks) * (Math.PI / 180.0)
                        val startR = radius - 14.dp.toPx()
                        val endR = radius - 8.dp.toPx()
                        val p1 = Offset(center.x + (cos(angle) * startR).toFloat(), center.y + (sin(angle) * startR).toFloat())
                        val p2 = Offset(center.x + (cos(angle) * endR).toFloat(), center.y + (sin(angle) * endR).toFloat())
                        drawLine(
                            color = if (i % 4 == 0) LimeSprout else CyprusLight.copy(alpha = 0.5f),
                            start = p1,
                            end = p2,
                            strokeWidth = if (i % 4 == 0) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                }

                // Inner rotating ring
                Canvas(
                    modifier = Modifier
                        .size(150.dp)
                        .rotate(innerRotation)
                ) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        color = LimeSprout.copy(alpha = 0.5f),
                        radius = radius,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val dots = 8
                    for (i in 0 until dots) {
                        val angle = (i * 360f / dots) * (Math.PI / 180.0)
                        val p = Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat())
                        drawCircle(
                            color = if (i % 2 == 0) LimeSprout else Sand,
                            radius = 3.dp.toPx(),
                            center = p
                        )
                    }
                }

                // Central Luxury Shield Core
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(corePulse)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Cyprus,
                                    FreshCanopy,
                                    ObsidianBlack
                                )
                            )
                        )
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                listOf(
                                    LimeSprout,
                                    Cyprus,
                                    FreshCanopy
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = LimeSprout,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "ZYLO VEIL",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = 2.sp,
                color = Sand
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "CRYPTOGRAPHIC STEGANOGRAPHY VAULT",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = LimeSprout,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Hardware Acceleration Badge
            Surface(
                color = CyprusDark.copy(alpha = 0.8f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hardware.isSnapdragon || hardware.isMediaTek) Icons.Default.Bolt else Icons.Default.Memory,
                        contentDescription = null,
                        tint = LimeSprout,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${hardware.processorFamily.displayName} • ${hardware.cpuCores} Cores Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Sand
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = bootStage,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = SandSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bootProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Cyprus,
                                    LimeSprout
                                )
                            )
                        )
                )
            }
        }

        Text(
            text = "TAP TO ENTER",
            style = MaterialTheme.typography.labelSmall,
            color = SandMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
