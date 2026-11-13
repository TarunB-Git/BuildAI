package com.buildai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStateAtLeast
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import com.buildai.app.ui.theme.Background
import com.buildai.app.ui.theme.CardBackground
import com.buildai.app.ui.theme.Error
import com.buildai.app.ui.theme.OnBackground
import com.buildai.app.ui.theme.OnSurface
import com.buildai.app.ui.theme.OnSurfaceVariant
import com.buildai.app.ui.theme.Primary
import com.buildai.app.viewmodel.GenerationState
import com.buildai.app.viewmodel.GenerationViewModel

@Composable
fun GenerationScreen(
    projectId: String,
    plan: ProjectPlan,
    design: DesignSpec,
    generationVm: GenerationViewModel,
    onDone: () -> Unit,
    onBackground: () -> Unit
) {
    val state by generationVm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BackHandler(onBack = onBackground)

    LaunchedEffect(state, lifecycle) {
        if (state is GenerationState.Done) lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) { onDone() }
    }

    val palette    = DesignDefaults.palette(design.paletteId)
    val appIcon    = DesignDefaults.icon(design.iconKey)
    val primaryColor = Color(palette.primary)

    // Pulsing icon animation
    val pulse = rememberInfiniteTransition(label = "pulse")
    val iconScale by pulse.animateFloat(
        initialValue  = 0.92f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // ── App icon ──────────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(96.dp)
                    .scale(if (state is GenerationState.Running) iconScale else 1f)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector        = appIcon.vector,
                    contentDescription = null,
                    tint               = primaryColor,
                    modifier           = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── App name ──────────────────────────────────────────────────────
            Text(
                text  = plan.appName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = OnBackground
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is GenerationState.Running -> {
                    Text(
                        text      = s.step,
                        style     = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text      = "You can leave while the build continues",
                        style     = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant.copy(alpha = 0.6f)),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(), color = primaryColor,
                        trackColor = primaryColor.copy(alpha = 0.15f)
                    )

                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick  = onBackground,
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = CardBackground)
                    ) {
                        Text("Continue in background", color = OnSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { generationVm.cancel(); onBackground() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackground)
                    ) { Text("Cancel build", color = OnSurfaceVariant) }
                }

                is GenerationState.Failure -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color    = Error.copy(alpha = 0.1f),
                        shape    = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text     = s.message,
                            style    = MaterialTheme.typography.bodyMedium.copy(color = Error),
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { generationVm.generate(projectId, plan, design) },
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) { Text("Try Again") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBackground,
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = CardBackground)
                    ) { Text("Go Back", color = OnSurface) }
                }

                else -> { /* Idle / Done — handled by LaunchedEffect */ }
            }
        }
    }
}
