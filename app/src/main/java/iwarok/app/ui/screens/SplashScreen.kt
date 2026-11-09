package iwarok.app.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iwarok.app.R
import iwarok.app.ui.components.BrandWordmark
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowPink

/**
 * Obsidian splash: new IwaRok "splash i" mark (the launcher foreground vector)
 * over near-black, with a serif word mark and a soft fade / scale / water-line
 * intro. No slogan, no progress text.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val appear by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "splash_appear",
    )
    val logoAlpha = appear
    val logoScale = 0.94f + 0.06f * appear
    val wordAlpha = ((appear - 0.25f) / 0.75f).coerceIn(0f, 1f)
    val lineWidth = 72.dp * ((appear - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground),
    ) {
        // Very subtle champagne halo behind the mark.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(FlowPink.copy(alpha = 0.06f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    },
            )
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.alpha(wordAlpha)) {
                BrandWordmark(fontSize = 30.sp)
            }
            Spacer(Modifier.height(14.dp))
            // Thin "water line" that grows in under the word mark.
            Box(
                modifier = Modifier
                    .width(lineWidth)
                    .height(1.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(FlowPink.copy(alpha = 0.75f * wordAlpha)),
            )
        }
    }
}
