package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.hardware.HardwareAccelerator
import com.example.ui.screens.*
import com.example.ui.components.GlassBottomNavigation
import com.example.ui.theme.*
import com.example.util.HapticHelper

enum class AppState {
    SPLASH,
    LOCKED,
    UNLOCKED
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZyloVeilTheme {
                val context = LocalContext.current
                var appState by remember { mutableStateOf(AppState.SPLASH) }

                Crossfade(
                    targetState = appState,
                    animationSpec = tween(durationMillis = 400),
                    label = "AppStateCrossfade"
                ) { state ->
                    when (state) {
                        AppState.SPLASH -> {
                            SplashScreen(onAnimationComplete = {
                                appState = AppState.LOCKED
                            })
                        }
                        AppState.LOCKED -> {
                            LockScreen(onUnlocked = {
                                appState = AppState.UNLOCKED
                            })
                        }
                        AppState.UNLOCKED -> {
                            ZyloVeilApp(onLockRequested = {
                                HapticHelper.click(context)
                                appState = AppState.LOCKED
                            })
                        }
                    }
                }
            }
        }
    }
}

enum class ZyloScreen(val title: String, val icon: ImageVector) {
    VEIL("Veil", Icons.Default.Shield),
    UNVEIL("Unveil", Icons.Default.LockOpen),
    INSPECT("Inspect", Icons.Default.Analytics),
    ABOUT("About", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZyloVeilApp(
    onLockRequested: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(ZyloScreen.VEIL) }
    val hardware = remember { HardwareAccelerator.profile }

    if (currentScreen != ZyloScreen.VEIL) {
        BackHandler {
            HapticHelper.click(context)
            currentScreen = ZyloScreen.VEIL
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ZYLO VEIL",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Sand,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Cyprus.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "v3.2",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = LimeSprout,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Hardware Acceleration Badge
                        Surface(
                            color = CyprusDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, LimeSprout.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (hardware.isSnapdragon || hardware.isMediaTek) Icons.Default.Bolt else Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = LimeSprout,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (hardware.isSnapdragon) "Snapdragon" else if (hardware.isMediaTek) "Dimensity" else "HW-Accelerated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Sand,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Fingerprint Quick-Lock Action
                        IconButton(
                            onClick = onLockRequested,
                            modifier = Modifier.testTag("lock_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Lock Vault",
                                tint = LimeSprout
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack,
                    titleContentColor = Sand
                )
            )
        },
        bottomBar = {
            GlassBottomNavigation(
                items = ZyloScreen.values().map {
                    Triple(if (it == ZyloScreen.ABOUT) "Info" else it.title, it.icon, currentScreen == it)
                },
                onItemSelected = { index ->
                    val screen = ZyloScreen.values()[index]
                    if (currentScreen != screen) {
                        HapticHelper.click(context)
                        currentScreen = screen
                    }
                },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (slideInHorizontally(tween(420)) { it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(360)) { -it / 3 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(420)) { -it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(360)) { it / 3 } + fadeOut(tween(180)))
                    }
                },
                label = "screenSlideTransition"
            ) { screen ->
                when (screen) {
                    ZyloScreen.VEIL -> VeilScreen(onResultGenerated = {})
                    ZyloScreen.UNVEIL -> UnveilScreen()
                    ZyloScreen.INSPECT -> InspectScreen()
                    ZyloScreen.ABOUT -> AboutScreen()
                }
            }
        }
    }
}
