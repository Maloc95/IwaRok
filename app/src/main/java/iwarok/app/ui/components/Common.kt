package iwarok.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import iwarok.app.R
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraUser
import iwarok.app.core.avatarUrl
import iwarok.app.ui.util.localizedErrorMessage
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.ChampagneGradient
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowBackgroundDeep
import iwarok.app.ui.theme.FlowError
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowSurfaceElevated
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import iwarok.app.ui.theme.FlowViolet
import iwarok.app.ui.theme.brandGradientTextStyle

/* ------------------------------------------------------------------------- *
 * Shared building blocks: brand mark, avatars, buttons, chips and the
 * loading / error / empty states used by every screen.
 * ------------------------------------------------------------------------- */

/** Iwarok brand mark — a gradient tile with a play glyph (no official logo). */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = size * 0.32f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(BrandGradient)
            .border(1.dp, FlowPink.copy(alpha = 0.55f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.cd_brand_logo),
            tint = Color(0xFFF3F0E9),
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** "Iwarok" word mark: serif, warm white -> champagne gradient. */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp,
) {
    Text(
        text = "IwaRok",
        style = brandGradientTextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold),
        modifier = modifier,
    )
}

/** Small pill with the 18+ badge used on the age gate / disclaimer cards. */
@Composable
fun AdultBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FlowPink.copy(alpha = 0.16f))
            .border(1.dp, FlowPink.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "18+",
            color = FlowPink,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Light champagne primary button with dark text (Obsidian × Champagne). */
@Composable
fun FlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(
                if (active) ChampagneGradient
                else Brush.linearGradient(listOf(FlowSurfaceElevated, FlowSurfaceElevated)),
            )
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (active) Color(0xFF17120A) else FlowTextTertiary
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFF17120A),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.loading),
                    color = Color(0xFF17120A),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Outlined / glassy secondary button. */
@Composable
fun FlowSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, FlowOutline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) FlowTextPrimary else FlowTextTertiary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Rounded single-choice chip used for the feed sort switcher. */
@Composable
fun FlowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) FlowPink.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (selected) FlowPink.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) FlowTextPrimary else FlowTextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** Circular user avatar with Coil loading + gradient fallback initial. */
@Composable
fun UserAvatar(
    user: IwaraUser?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    borderWidth: Dp = 1.5.dp,
    onClick: (() -> Unit)? = null,
) {
    val url = avatarUrl(user)
    val shape = CircleShape
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(FlowPink, FlowViolet)))
            .border(borderWidth, Color.White.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = user?.displayName ?: stringResource(R.string.cd_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Iwara's own default avatar (bundled locally, so it also shows while offline).
            Image(
                painter = painterResource(R.drawable.default_avatar),
                contentDescription = user?.displayName ?: stringResource(R.string.cd_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Centered spinner with an optional caption. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.loading),
    tint: Color = FlowPink,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = tint, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            color = FlowTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Error state with a retry button. Hides the retry when [onRetry] is null. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    title: String = stringResource(R.string.error_generic),
) {
    val localizedMessage = localizedErrorMessage(LocalContext.current, message)
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = FlowError.copy(alpha = 0.85f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = FlowTextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = localizedMessage,
            color = FlowTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, FlowOutline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = FlowTextPrimary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.retry),
                    color = FlowTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/** Empty-state card with an icon and copy. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(
            text = title,
            color = FlowTextPrimary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = FlowTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Shimmering placeholder box used by the initial feed skeleton. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )
    val highlight = FlowSurfaceElevated
    val base = Color(0xFF17171A)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(progress * 900f - 300f, 0f),
                    end = Offset(progress * 900f, 300f),
                ),
            ),
    )
}

/** Full-screen skeleton shown while the very first feed page is loading. */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FlowBackground, FlowBackgroundDeep))),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 96.dp, bottom = 40.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.42f).height(15.dp))
            Spacer(Modifier.height(12.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.82f).height(19.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(19.dp))
            Spacer(Modifier.height(14.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(13.dp))
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShimmerBox(modifier = Modifier.size(44.dp), shape = CircleShape)
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShimmerBox(modifier = Modifier.size(34.dp), shape = CircleShape)
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(modifier = Modifier.size(width = 22.dp, height = 10.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 18.dp, end = 18.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(modifier = Modifier.size(width = 118.dp, height = 26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    ShimmerBox(modifier = Modifier.size(width = 54.dp, height = 26.dp), shape = RoundedCornerShape(50))
                }
            }
        }
    }
}

/** Shared "follow us on Iwara" escape hatch used when no browser can handle it. */
@Composable
fun WebsiteHint(modifier: Modifier = Modifier) {
    Text(
        text = IwaraConfig.SITE_ORIGIN,
        color = FlowTextTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/** Card container used by static screens (settings / login / sheets). */
@Composable
fun FlowCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121214))
            .border(1.dp, FlowOutline, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** Back arrow + title header used by the static screens (settings / search). */
@Composable
fun ScreenTopBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = FlowTextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (!title.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = FlowTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** Small pill used for tags / labels on static screens. */
@Composable
fun FlowPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FlowTextSecondary,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

