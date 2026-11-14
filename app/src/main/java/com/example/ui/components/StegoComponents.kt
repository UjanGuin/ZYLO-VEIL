package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.window.Dialog
import com.example.model.CarrierImage
import com.example.model.StegoProgress
import com.example.ui.theme.*

/**
 * Apple-style Solid Frosted Glass Card with a specular highlight border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    backgroundColor: Color = ObsidianCard,
    content: @Composable ColumnScope.() -> Unit
) {
    val specularBorder = if (borderColor != null) {
        BorderStroke(1.dp, borderColor)
    } else {
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.08f),
                    Color(0x08FFFFFF)
                )
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(specularBorder, RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        backgroundColor.copy(alpha = 0.90f),
                        backgroundColor.copy(alpha = 0.97f)
                    )
                )
            )
    ) {
        // Upper subtle glass glossy reflection line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun GlassBottomNavigation(
    items: List<Triple<String, ImageVector, Boolean>>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.third }.coerceAtLeast(0)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    val coroutineScope = rememberCoroutineScope()
    var isSwiping by remember { mutableStateOf(false) }
    val animOffsetPx = remember { androidx.compose.animation.core.Animatable(0f) }

    val pillScale by animateFloatAsState(
        targetValue = if (isSwiping) 1.55f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillScaleBounce"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(horizontal = 12.dp)
            .pointerInput(items.size) {
                val itemWidthPx = size.width.toFloat() / items.size
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastX = down.position.x
                        var totalDrag = 0f
                        var isDragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isDragging) {
                                    val startTabCenter = (currentSelectedIndex + 0.5f) * itemWidthPx
                                    val finalPos = (startTabCenter + totalDrag).coerceIn(0f, size.width.toFloat())
                                    val targetIndex = (finalPos / itemWidthPx).toInt().coerceIn(0, items.size - 1)
                                    val currentX = currentSelectedIndex * itemWidthPx + totalDrag
                                    val targetX = targetIndex * itemWidthPx
                                    val snapDelta = currentX - targetX
                                    coroutineScope.launch {
                                        animOffsetPx.snapTo(snapDelta)
                                        currentOnItemSelected(targetIndex)
                                        animOffsetPx.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                } else {
                                    val tappedIndex = (change.position.x / itemWidthPx)
                                        .toInt()
                                        .coerceIn(0, items.size - 1)
                                    if (tappedIndex != currentSelectedIndex) {
                                        val oldTabX = currentSelectedIndex * itemWidthPx
                                        val newTabX = tappedIndex * itemWidthPx
                                        val jumpDelta = oldTabX - newTabX
                                        coroutineScope.launch {
                                            animOffsetPx.snapTo(jumpDelta)
                                            currentOnItemSelected(tappedIndex)
                                            animOffsetPx.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                                isSwiping = false
                                break
                            }
                            val delta = change.position.x - lastX
                            lastX = change.position.x
                            totalDrag += delta

                            if (!isDragging && kotlin.math.abs(totalDrag) > 15f) {
                                isDragging = true
                                isSwiping = true
                            }
                            if (isDragging) {
                                coroutineScope.launch {
                                    animOffsetPx.snapTo(totalDrag)
                                }
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val itemWidth = maxWidth / items.size
        val dynamicOffsetDp = with(density) { animOffsetPx.value.toDp() }
        val indicatorX = itemWidth * selectedIndex + 6.dp + dynamicOffsetDp

        // Frosted capsule track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                    RoundedCornerShape(28.dp)
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Cyprus.copy(alpha = 0.78f),
                            ObsidianBlack.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        // Glass pill indicator (overflows height when scaled during bounce)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset(indicatorX.roundToPx(), 0) }
                .width(itemWidth - 12.dp)
                .height(48.dp)
                .scale(pillScale)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.18f),
                            LimeSprout.copy(alpha = 0.14f)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    RoundedCornerShape(24.dp)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.Center)
        ) {
            items.forEachIndexed { _, item ->
                val selected = item.third
                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .testTag("nav_item_${item.first.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.second,
                        contentDescription = item.first,
                        tint = if (selected) LimeSprout else SandSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.first,
                        color = if (selected) Sand else SandSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Apple-style Solid Glass Primary Action Button.
 */
@Composable
fun AppleGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = ObsidianBlack,
    buttonColor: Color = LimeSprout,
    textColor: Color = ObsidianBlack,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "appleGlassScale"
    )

    val activeColor = if (isDestructive) AlertRed else buttonColor
    val activeTextColor = if (isDestructive) Color.White else textColor
    val activeIconTint = if (isDestructive) Color.White else iconTint

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(animatedScale)
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.5f),
                            activeColor.copy(alpha = 0.8f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        activeColor,
                        activeColor.copy(alpha = 0.90f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled,
                onClick = onClick
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Top specular refraction sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activeIconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp,
                color = activeTextColor
            )
        }
    }
}

/**
 * Apple-style Solid Glass Secondary Button.
 */
@Composable
fun AppleGlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = LimeSprout,
    textColor: Color = Sand,
    height: Int = 48,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "secondaryGlassScale"
    )

    Box(
        modifier = modifier
            .height(height.dp)
            .scale(animatedScale)
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            LimeSprout.copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Cyprus.copy(alpha = 0.55f),
                        ObsidianSurface.copy(alpha = 0.90f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Top gloss sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = textColor
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = LimeSprout
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Cyprus.copy(alpha = 0.5f),
                            ObsidianCardVariant
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                iconTint.copy(alpha = 0.25f)
                            )
                        )
                    ),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Sand
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )
            }
        }
    }
}

@Composable
fun CarrierItemCard(
    carrier: CarrierImage,
    index: Int,
    total: Int,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (carrier.isConvertedFromJpg) {
                        Brush.verticalGradient(
                            listOf(
                                LimeSprout.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    }
                ),
                RoundedCornerShape(14.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        ObsidianCardVariant.copy(alpha = 0.95f),
                        ObsidianCard.copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Cyprus.copy(alpha = 0.6f),
                                ObsidianSurface
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = LimeSprout,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = carrier.originalName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Sand,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#${index + 1}/$total",
                        style = MaterialTheme.typography.labelSmall,
                        color = LimeSprout,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${carrier.width}×${carrier.height} px • ~${carrier.capacityBytes / 1024} KB bit budget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )

                if (carrier.isConvertedFromJpg) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Surface(
                        color = Cyprus.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LimeSprout,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto-converted JPG to lossless PNG",
                                style = MaterialTheme.typography.labelSmall,
                                color = LimeSprout,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove carrier",
                    tint = SandMuted
                )
            }
        }
    }
}

@Composable
fun FlagToggleItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDestructive: Boolean = false,
    activeColor: Color = LimeSprout
) {
    val highlightColor = if (isDestructive) AlertRed else activeColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .border(
                BorderStroke(
                    1.dp,
                    if (checked) {
                        Brush.verticalGradient(
                            listOf(
                                highlightColor.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)).brush
                    }
                ),
                RoundedCornerShape(12.dp)
            )
            .background(
                if (checked) {
                    Brush.verticalGradient(
                        listOf(
                            Cyprus.copy(alpha = 0.35f),
                            ObsidianCardVariant.copy(alpha = 0.8f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            ObsidianCardVariant.copy(alpha = 0.45f),
                            ObsidianCard.copy(alpha = 0.6f)
                        )
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) highlightColor else SandMuted,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive && checked) AlertRed else Sand
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandSecondary
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = highlightColor,
                    uncheckedThumbColor = SandMuted,
                    uncheckedTrackColor = ObsidianSurface
                )
            )
        }
    }
}

@Composable
fun StegoProgressDialog(progress: StegoProgress) {
    if (!progress.isRunning) return

    val animatedPercent by animateFloatAsState(
        targetValue = progress.percent.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "progressPercent"
    )

    Dialog(onDismissRequest = { /* Modal */ }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                LimeSprout.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )
                    ),
                    RoundedCornerShape(22.dp)
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ObsidianSurface.copy(alpha = 0.95f),
                            ObsidianBlack
                        )
                    )
                )
                .padding(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedPercent / 100f },
                        modifier = Modifier.size(76.dp),
                        color = LimeSprout,
                        trackColor = CyprusDark,
                        strokeWidth = 5.dp
                    )
                    Text(
                        text = "${animatedPercent.toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Sand
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = progress.stage,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Sand
                )

                if (progress.detail.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = progress.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandSecondary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { animatedPercent / 100f },
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
