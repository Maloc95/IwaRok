package iwarok.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------------- *
 * Iwarok theme — Obsidian × Champagne.
 *
 * Near-black neutral surfaces, warm-white type and a single restrained
 * champagne accent (#D4BE98) with bronze (#A88B60) as its deep counterpart.
 * No pink / violet / glow / colored shadows.
 * ------------------------------------------------------------------------- */

/** Primary accent (champagne). Name kept for source compatibility. */
val FlowPink = Color(0xFFD4BE98)

/** Deep bronze counterpart of the accent. */
val FlowViolet = Color(0xFFA88B60)

val FlowPinkSoft = Color(0xFFE7DCC8)
val FlowVioletSoft = Color(0xFFC6AE85)

val FlowBackground = Color(0xFF0A0A0B)
val FlowBackgroundDeep = Color(0xFF050506)
val FlowSurface = Color(0xFF111113)
val FlowSurfaceElevated = Color(0xFF18181B)
val FlowSurfaceGlass = Color(0xE6121214)

val FlowTextPrimary = Color(0xFFF3F0E9)
val FlowTextSecondary = Color(0xFFA19E96)
val FlowTextTertiary = Color(0xFF6F6D68)

val FlowOutline = Color(0xFF2A2A2E)
val FlowOutlineSoft = Color(0x33FFFFFF)
val FlowError = Color(0xFFC97B6E)
val FlowSuccess = Color(0xFF97A98E)

/** TikTok-style red used only for the un-followed "+" badge. */
val TikTokRed = Color(0xFFFE2C55)

/** Deep bronze gradient: used behind white text (logo, dark buttons). */
val BrandGradient: Brush = Brush.linearGradient(
    listOf(Color(0xFF7B5F3C), Color(0xFF4A3826)),
)

/** Warm white -> champagne gradient for the wordmark / accent text. */
val BrandTextGradient: Brush = Brush.linearGradient(
    listOf(Color(0xFFF3F0E9), Color(0xFFD4BE98)),
)

/** Champagne gradient used by the light primary button (dark text on top). */
val ChampagneGradient: Brush = Brush.linearGradient(
    listOf(Color(0xFFEADFCB), Color(0xFFD4BE98)),
)

/** Top scrim used by the immersive video feed. */
val TopScrimGradient: Brush =
    Brush.verticalGradient(
        0f to Color(0xF2000000),
        0.45f to Color(0x8C000000),
        1f to Color(0x00000000),
    )

/** Bottom scrim: fades the video into the description / action rail. */
val BottomScrimGradient: Brush =
    Brush.verticalGradient(
        0f to Color(0x00000000),
        0.55f to Color(0x99000000),
        1f to Color(0xF2000000),
    )

/** Ambient splash / empty-state background: neutral deep grey, no colour tint. */
val AmbientGradient: Brush =
    Brush.verticalGradient(
        0f to Color(0xFF141416),
        0.55f to FlowBackground,
        1f to FlowBackgroundDeep,
    )

private val DarkColors = darkColorScheme(
    primary = FlowPink,
    onPrimary = Color(0xFF17120A),
    primaryContainer = Color(0xFF2A2318),
    onPrimaryContainer = FlowPinkSoft,
    secondary = FlowViolet,
    onSecondary = Color(0xFF17120A),
    secondaryContainer = Color(0xFF26221C),
    onSecondaryContainer = FlowVioletSoft,
    tertiary = FlowVioletSoft,
    onTertiary = Color(0xFF17120A),
    background = FlowBackground,
    onBackground = FlowTextPrimary,
    surface = FlowSurface,
    onSurface = FlowTextPrimary,
    surfaceVariant = FlowSurfaceElevated,
    onSurfaceVariant = FlowTextSecondary,
    surfaceContainer = FlowSurface,
    surfaceContainerHigh = FlowSurfaceElevated,
    surfaceContainerHighest = Color(0xFF1E1D1A),
    inverseSurface = FlowTextPrimary,
    inverseOnSurface = FlowBackground,
    outline = FlowOutline,
    outlineVariant = FlowOutline,
    error = FlowError,
    onError = Color(0xFF17120A),
)

private val FlowShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val FlowTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/**
 * Gradient text style helper (serif, warm white -> champagne) for the word mark
 * and other premium accents.
 */
fun brandGradientTextStyle(
    fontSize: TextUnit = 30.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
): TextStyle = TextStyle(
    brush = BrandTextGradient,
    fontFamily = FontFamily.Serif,
    fontSize = fontSize,
    fontWeight = fontWeight,
    letterSpacing = 0.6.sp,
)

@Composable
fun IwarokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = FlowTypography,
        shapes = FlowShapes,
        content = content,
    )
}
