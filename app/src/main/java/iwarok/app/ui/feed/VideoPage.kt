package iwarok.app.ui.feed

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import android.media.AudioManager
import android.provider.Settings

import android.view.WindowManager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

import iwarok.app.R
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraVideo
import iwarok.app.core.formatDuration
import iwarok.app.core.tagLabel
import iwarok.app.core.videoThumbnailUrl
import iwarok.app.ui.util.localizedErrorMessage
import iwarok.app.ui.util.findHostActivity
import iwarok.app.ui.util.localizedCount
import iwarok.app.ui.util.localizedPlaybackQualityLabel
import iwarok.app.ui.util.localizedRatingLabel
import iwarok.app.ui.util.localizedRelativeDate
import iwarok.app.ui.util.localizedVideoTitle
import iwarok.app.ui.util.openUrl
import iwarok.app.ui.util.VideoDownloader
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.player.PlayerManager
import iwarok.app.ui.theme.BottomScrimGradient
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.ChampagneGradient
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowPinkSoft
import iwarok.app.ui.theme.FlowSurfaceGlass
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowVioletSoft
import iwarok.app.ui.theme.TikTokRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A single full-screen TikTok-style video page.
 *
 * Layout order matters: the shared [iwarok.app.ui.player.VideoSurface] is
 * rendered by the feed behind the pager content, then this page adds the poster,
 * scrims, gestures and overlays.
 *
 * @param compact when true only the video itself is rendered (no action rail,
 * description or progress bar). The feed uses it while the comment panel is open
 * so the video can keep playing in the top part of the screen.
 * @param fullscreen landscape full-screen mode (landscape videos only): the feed
 * header / rail / description are hidden and the system bars are managed by the
 * caller through [iwarok.app.ui.util.FullscreenController].
 */
@Composable
fun VideoPage(
    video: IwaraVideo,
    isCurrent: Boolean,
    playerManager: PlayerManager,
    playbackState: PlaybackUiState?,
    liked: Boolean,
    likeCount: Int,
    isFavorited: Boolean,
    isFollowing: Boolean,
    onTapTogglePlay: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onRetryPlayback: () -> Unit,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    isLandscapeVideo: Boolean = false,
    fullscreen: Boolean = false,
    onEnterFullscreen: (() -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
    compact: Boolean = false,
    onCleanModeChanged: (Boolean) -> Unit = {},
    onGestureMenuVisibilityChanged: (Boolean) -> Unit = {},
    onVideoTransformChanged: (Float, Offset) -> Unit = { _, _ -> },
    /** How far the shared portrait surface is lifted (26dp in the feed, 0 in detail). */
    contentLift: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val untitledFallback = stringResource(R.string.video_untitled)
    val ready = playbackState is PlaybackUiState.Ready

    // Cover stays visible until the first video frame has actually been drawn.
    val showCover = !isCurrent ||
        !ready ||
        playerManager.boundVideoId != video.id ||
        !playerManager.firstFrameRendered
    val coverAlpha by animateFloatAsState(
        targetValue = if (showCover) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "cover_alpha",
    )

    // Center play/pause indicator fades in/out on tap.
    var centerIconVisible by remember { mutableStateOf(false) }
    var centerIconIsPause by remember { mutableStateOf(false) }
    var tapTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapTick) {
        if (tapTick == 0) return@LaunchedEffect
        centerIconVisible = true
        delay(650)
        centerIconVisible = false
    }

    // Double-tap heart burst.
    var likeBurstTick by remember { mutableIntStateOf(0) }
    var likeBurstPosition by remember { mutableStateOf<Offset?>(null) }

    // Fullscreen controls: visible on entry / after any interaction, auto-fade
    // after ~3s of inactivity. Tapping the video toggles them (playback keeps
    // running); any control interaction or seek restarts the countdown.
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsPokeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(fullscreen) {
        if (!fullscreen) controlsVisible = true
    }
    LaunchedEffect(fullscreen, controlsPokeTick) {
        if (!fullscreen) return@LaunchedEffect
        controlsVisible = true
        delay(3000)
        controlsVisible = false
    }
    fun pokeFullscreenControls() {
        if (!fullscreen) return
        controlsVisible = true
        controlsPokeTick++
    }

    // The tap gesture detector below is not restarted on every recomposition, so
    // reading `fullscreen` / `controlsVisible` directly inside pointerInput would
    // capture stale values (the original bug: waking the controls never worked
    // after entering fullscreen). rememberUpdatedState always exposes the latest
    // value to the long-lived gesture coroutine.
    val currentFullscreen by rememberUpdatedState(fullscreen)
    val isPortraitScreen =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val currentControlsVisible by rememberUpdatedState(controlsVisible)
    val currentTapTogglePlay by rememberUpdatedState(onTapTogglePlay)

    // ---- Douyin-style gesture state ------------------------------------------
    var cleanMode by remember { mutableStateOf(false) }
    var speedBoosting by remember { mutableStateOf(false) }
    var speedLocked by remember { mutableStateOf(false) }
    var speedPillVisible by remember { mutableStateOf(false) }
    // Bottom-state hint strip shown while a 2x long-press is in progress.
    var speedHintVisible by remember { mutableStateOf(false) }
    var speedHintArmed by remember { mutableStateOf(false) }
    // Short "locked / unlocked" notice shown in the same top capsule.
    var speedLockedNoticeVisible by remember { mutableStateOf(false) }
    var speedLockedNoticeTick by remember { mutableIntStateOf(0) }
    // false = locked notice, true = unlocked notice (never reuse the wrong text).
    var speedNoticeCancelled by remember { mutableStateOf(false) }
    LaunchedEffect(speedLockedNoticeTick) {
        if (speedLockedNoticeTick == 0) return@LaunchedEffect
        speedLockedNoticeVisible = true
        delay(1800)
        speedLockedNoticeVisible = false
    }
    var gestureMenuOpen by remember { mutableStateOf(false) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }

    // Video transform (pinch zoom + pan) applied to the shared VideoSurface by
    // the parent via onVideoTransformChanged.
    var videoScale by remember { mutableStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }
    val currentVideoScale by rememberUpdatedState(videoScale)
    val currentVideoOffset by rememberUpdatedState(videoOffset)
    val currentOnVideoTransform by rememberUpdatedState(onVideoTransformChanged)

    LaunchedEffect(videoScale, videoOffset) {
        onVideoTransformChanged(videoScale, videoOffset)
    }

    fun resetVideoTransform() {
        videoScale = 1f
        videoOffset = Offset.Zero
    }

    val currentCleanMode by rememberUpdatedState(cleanMode)
    val currentOnCleanModeChanged by rememberUpdatedState(onCleanModeChanged)
    val currentOnUserClick by rememberUpdatedState(onUserClick)
    val currentOnDoubleTapLike by rememberUpdatedState(onDoubleTapLike)
    val haptic = LocalHapticFeedback.current
    val gestureScope = rememberCoroutineScope()
    // Douyin-style: a short downward drag (~48dp) arms the lock; there is no
    // need to reach the very bottom of the screen. Sliding back up disarms.
    val speedSlideLockPx = with(LocalDensity.current) { 48.dp.toPx() }
    // The speed chosen from the menu / clean-mode speed button. A temporary
    // long-press boost must restore this on release, not always 1x.
    var baseSpeed by remember { mutableStateOf(1f) }

    // ---- Fullscreen: lock / brightness / volume / temporary 2x -------------
    var fullscreenLocked by remember { mutableStateOf(false) }
    var fsTempSpeed by remember { mutableStateOf(false) }
    var fsBaseSpeed by remember { mutableStateOf(1f) }
    val initialSystemBrightness = remember(context) {
        runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            ) / 255f
        }.getOrDefault(0.5f).coerceIn(0.05f, 1f)
    }
    var fsBrightness by remember { mutableStateOf(initialSystemBrightness) }
    var fsBrightnessOverridden by remember { mutableStateOf(false) }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxMusicVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var fsVolume by remember {
        mutableStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMusicVolume,
        )
    }
    var fsHud by remember { mutableStateOf<FullscreenHud?>(null) }
    var adjustingLevel by remember { mutableStateOf<FullscreenHudKind?>(null) }
    val hostActivity = remember(context) { context.findHostActivity() }

    fun applyFullscreenBrightness(value: Float) {
        val clamped = value.coerceIn(0.05f, 1f)
        fsBrightness = clamped
        fsBrightnessOverridden = true
        hostActivity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = clamped
            window.attributes = attrs
        }
    }

    fun restoreFullscreenBrightness() {
        if (!fsBrightnessOverridden) return
        fsBrightnessOverridden = false
        hostActivity?.window?.let { window ->
            val attrs = window.attributes
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = attrs
        }
    }

    var lastAppliedVolumeIndex by remember { mutableIntStateOf(-1) }
    fun applyFullscreenVolume(value: Float) {
        val index = (value.coerceIn(0f, 1f) * maxMusicVolume).roundToInt()
            .coerceIn(0, maxMusicVolume)
        fsVolume = index.toFloat() / maxMusicVolume
        if (index != lastAppliedVolumeIndex) {
            lastAppliedVolumeIndex = index
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
    }

    fun stopTemporaryFullscreenSpeed() {
        if (!fsTempSpeed) return
        fsTempSpeed = false
        playerManager.setPlaybackSpeed(fsBaseSpeed)
    }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            val currentBrightness =
                hostActivity?.window?.attributes?.screenBrightness ?: -1f
            fsBrightness = if (currentBrightness >= 0f) {
                currentBrightness
            } else {
                initialSystemBrightness
            }
            fsVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                .toFloat() / maxMusicVolume
            lastAppliedVolumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            fullscreenLocked = false
        } else {
            fullscreenLocked = false
            fsHud = null
            stopTemporaryFullscreenSpeed()
            restoreFullscreenBrightness()
        }
    }

    // Leaving the page while fullscreen must also restore the system brightness
    // override and the user's base playback speed.
    DisposableEffect(Unit) {
        onDispose {
            restoreFullscreenBrightness()
            if (fsTempSpeed) {
                fsTempSpeed = false
                playerManager.setPlaybackSpeed(fsBaseSpeed)
            }
        }
    }

    fun beginSpeedHold() {
        speedLockedNoticeVisible = false
        speedHintVisible = true
        speedHintArmed = false
        speedBoosting = true
        speedPillVisible = true
        playerManager.setPlaybackSpeed(2f)
    }

    fun updateSpeedHold(currentY: Float, startY: Float, previousY: Float) {
        val slid = currentY - startY > speedSlideLockPx
        val movingUp = currentY - previousY < -2f
        val armed = slid && !movingUp
        if (armed != speedHintArmed) {
            speedHintArmed = armed
            if (armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun endSpeedHold(finalY: Float, startY: Float) {
        val armed = speedHintArmed && (finalY - startY > speedSlideLockPx)
        if (armed) {
            if (speedLocked) {
                speedLocked = false
                speedNoticeCancelled = true
                playerManager.setPlaybackSpeed(baseSpeed)
                speedLockedNoticeTick++
            } else {
                speedLocked = true
                speedNoticeCancelled = false
                playerManager.setPlaybackSpeed(2f)
                speedLockedNoticeTick++
            }
        } else if (speedLocked) {
            // A plain hold while locked keeps 2x and never shows a notice.
            playerManager.setPlaybackSpeed(2f)
        } else {
            playerManager.setPlaybackSpeed(baseSpeed)
        }
        speedBoosting = false
        speedPillVisible = false
        speedHintVisible = false
        speedHintArmed = false
    }

    // Long-press-to-2x gesture used on the chrome containers (rail / bottom info).
    // It is attached to those containers themselves — never to a full-screen
    // overlay — so short taps still reach the child buttons below; only after the
    // long-press fires do we consume, which cancels the button's click.
    val chromeSpeedGesture = Modifier.pointerInput(Unit) {
        val holdMs = 350L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var fired = false
            var aborted = false
            var lastUptime = down.uptimeMillis
            var lastPosition = down.position
            while (!fired && !aborted) {
                val remaining = (holdMs - (lastUptime - down.uptimeMillis)).coerceAtLeast(1L)
                val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                if (event == null) {
                    fired = true
                    break
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    aborted = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    aborted = true
                    break
                }
                lastPosition = change.position
                lastUptime = change.uptimeMillis
                if (!change.pressed || change.isConsumed) {
                    aborted = true
                    break
                }
                if (hypot(
                        lastPosition.x - down.position.x,
                        lastPosition.y - down.position.y,
                    ) > viewConfiguration.touchSlop * 2f
                ) {
                    aborted = true
                    break
                }
            }
            if (!fired) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            beginSpeedHold()
            var lastFingerY = down.position.y
            var previousFingerY = down.position.y
            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                    previousFingerY = lastFingerY
                    lastFingerY = change.position.y
                    if (pressedCount > 0) {
                        updateSpeedHold(lastFingerY, down.position.y, previousFingerY)
                    }
                }
                event.changes.forEach { it.consume() }
                if (pressedCount == 0) break
            }
            endSpeedHold(lastFingerY, down.position.y)
        }
    }

    fun resetGestures() {
        baseSpeed = 1f
        playerManager.setPlaybackSpeed(1f)
        speedBoosting = false
        speedLocked = false
        speedPillVisible = false
        speedHintVisible = false
        speedHintArmed = false
        speedLockedNoticeVisible = false
        speedNoticeCancelled = false
    }

    // Fullscreen buttons: long-press = temporary 2x only (never locks, no hints).
    // Attached to the controls root so short taps still reach the child buttons;
    // only a fired long-press consumes and restores the base speed on release.
    val fullscreenSpeedHoldGesture = Modifier.pointerInput(Unit) {
        val holdMs = 350L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var fired = false
            var aborted = false
            var lastUptime = down.uptimeMillis
            var lastPosition = down.position
            while (!fired && !aborted) {
                val remaining = (holdMs - (lastUptime - down.uptimeMillis)).coerceAtLeast(1L)
                val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                if (event == null) {
                    fired = true
                    break
                }
                if (event.changes.count { it.pressed } >= 2) {
                    aborted = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    aborted = true
                    break
                }
                lastPosition = change.position
                lastUptime = change.uptimeMillis
                if (!change.pressed || change.isConsumed) {
                    aborted = true
                    break
                }
                if (hypot(
                        lastPosition.x - down.position.x,
                        lastPosition.y - down.position.y,
                    ) > viewConfiguration.touchSlop * 2f
                ) {
                    aborted = true
                    break
                }
            }
            if (!fired) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            fsBaseSpeed = playerManager.playbackSpeed
            fsTempSpeed = true
            playerManager.setPlaybackSpeed(2f)
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
            if (fsTempSpeed) {
                fsTempSpeed = false
                playerManager.setPlaybackSpeed(fsBaseSpeed)
            }
        }
    }

    // The long-press menu must freeze the pager underneath it.
    LaunchedEffect(gestureMenuOpen) {
        onGestureMenuVisibilityChanged(gestureMenuOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onGestureMenuVisibilityChanged(false) }
    }

    // Leaving the page / switching videos restores 1x and clears gesture state.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            resetGestures()
            resetVideoTransform()
            if (cleanMode) {
                cleanMode = false
                currentOnCleanModeChanged(false)
            }
        }
    }
    DisposableEffect(video.id) {
        onDispose {
            pendingTap?.cancel()
            if (isCurrent) {
                baseSpeed = 1f
                playerManager.setPlaybackSpeed(1f)
                onVideoTransformChanged(1f, Offset.Zero)
            }
        }
    }

    // Bottom progress bar, polled only for the active page.
    var progress by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        while (isCurrent) {
            progress = playerManager.progressFraction()
            delay(200)
        }
        progress = 0f
    }

    val resolverError = (playbackState as? PlaybackUiState.Failed)?.message
    val playbackError = if (isCurrent) playerManager.playbackError else null
    val errorMessage = playbackError ?: resolverError
    val showPlaybackError = isCurrent && errorMessage != null
    // External embed / no-file works cannot be decoded by ExoPlayer: offer the
    // browser instead of a misleading "switch source" retry.
    val playbackErrorExternal = !video.embedUrl.isNullOrBlank() ||
        video.fileUrl.isNullOrBlank()
    val playbackExternalUrl = video.embedUrl?.takeIf { it.isNotBlank() }
        ?: IwaraConfig.webVideoUrl(video.id)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {

        // "全屏观看" capsule position: compute the real FIT rectangle from the
        // current video aspect ratio (4:3, 16:9, 21:9 ... all work) so the pill
        // always sits ~11dp below the picture's bottom edge, centred.
        val pillVideoWidth = playerManager.videoWidth.takeIf { it > 0 } ?: (video.file?.width ?: 0)
        val pillVideoHeight = playerManager.videoHeight.takeIf { it > 0 } ?: (video.file?.height ?: 0)
        val showFullscreenPill = isCurrent && isLandscapeVideo && !fullscreen && !compact &&
            !cleanMode && onEnterFullscreen != null && pillVideoWidth > 0 && pillVideoHeight > 0
        val fullscreenPillTop: Dp = if (showFullscreenPill) {
            val scale = minOf(
                maxWidth.value / pillVideoWidth.toFloat(),
                maxHeight.value / pillVideoHeight.toFloat(),
            )
            val displayHeight = (pillVideoHeight.toFloat() * scale).dp
            val top = (maxHeight - displayHeight) * 0.5f + displayHeight + 11.dp - contentLift
            top.coerceIn(0.dp, (maxHeight - 56.dp).coerceAtLeast(0.dp))
        } else {
            0.dp
        }

        // 1. Cover / poster. It sits above the shared player surface and fades
        //    out once the first video frame has been rendered.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = coverAlpha }
                .background(Brush.verticalGradient(listOf(Color(0xFF151517), Color.Black))),
        )
        val coverUrl = videoThumbnailUrl(video)
        if (coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(180)
                    .build(),
                contentDescription = localizedVideoTitle(video),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha },
            )
        }

        // 2. Bottom readability scrim (not used in fullscreen: the controls there
        //    have their own shadows instead of a long dark gradient).
        if (!compact && !fullscreen && !cleanMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(BottomScrimGradient),
            )
        }

        // 3. Unified gesture layer: single/double tap, LEFT-swipe to the author
        //    (portrait only), centre long-press menu, side long-press 2x and
        //    two-finger pinch zoom. Chrome buttons keep their own touches: the
        //    rail / bottom info implement long-press 2x on their own containers,
        //    so no full-screen overlay is ever placed above the chrome.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.id, isCurrent, fullscreen) {
                    val centerLongPressMs = 450L
                    val sideLongPressMs = 350L
                    val swipeThresholdPx = 80.dp.toPx()
                    awaitEachGesture {
                        if (!isCurrent || gestureMenuOpen || currentCleanMode ||
                            currentFullscreen
                        ) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val startPosition = down.position
                        val startTime = down.uptimeMillis
                        val widthPx = size.width.toFloat()
                        val inCenterBand = startPosition.x > widthPx * 0.30f &&
                            startPosition.x < widthPx * 0.70f
                        var isLongPress = false
                        var speedHoldActive = false
                        var previousSpeedY = startPosition.y
                        var swipeRight = false
                        var pinchHandled = false
                        var lastPosition = startPosition
                        var lastUptime = startTime
                        var initialPinchDistance = 0f
                        var finished = false
                        // Continuous pinch-zoom / pan state for this gesture.
                        var pinching = false
                        var pinchStartScale = 1f
                        var pinchStartOffset = Offset.Zero
                        var pinchStartCentroid = Offset.Zero
                        var panning = currentVideoScale > 1.001f
                        var panLast = startPosition
                        var singleFingerZoom = currentCleanMode && currentVideoScale <= 1.001f

                        while (!finished) {
                            val elapsed = lastUptime - startTime
                            val longPressMs =
                                if (inCenterBand) centerLongPressMs else sideLongPressMs
                            val timeout = if (isLongPress) {
                                Long.MAX_VALUE
                            } else {
                                (longPressMs - elapsed).coerceAtLeast(1L)
                            }
                            val event = withTimeoutOrNull(timeout) { awaitPointerEvent() }
                            if (event == null) {
                                isLongPress = true
                                if (inCenterBand) {
                                    // Centre band: long-press action sheet.
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    gestureMenuOpen = true
                                } else {
                                    // Left / right of the video: hold for 2x.
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    speedHoldActive = true
                                    beginSpeedHold()
                                }
                                continue
                            }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val points = pressed.map { it.position }
                                val distance = hypot(
                                    points[0].x - points[1].x,
                                    points[0].y - points[1].y,
                                )
                                val centroid = Offset(
                                    points.map { it.x }.average().toFloat(),
                                    points.map { it.y }.average().toFloat(),
                                )
                                if (!pinching) {
                                    pinching = true
                                    pinchHandled = true
                                    isLongPress = true
                                    initialPinchDistance = distance.coerceAtLeast(1f)
                                    pinchStartScale = currentVideoScale
                                    pinchStartOffset = currentVideoOffset
                                    pinchStartCentroid = centroid
                                } else {
                                    val zoom = distance / initialPinchDistance
                                    val newScale = (pinchStartScale * zoom).coerceIn(1f, 4f)
                                    val centroidDelta = centroid - pinchStartCentroid
                                    if (newScale > 1.001f) {
                                        videoScale = newScale
                                        videoOffset = clampVideoOffset(
                                            pinchStartOffset + centroidDelta,
                                            newScale,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        )
                                    } else {
                                        videoScale = 1f
                                        videoOffset = Offset.Zero
                                    }
                                    if (newScale > 1.02f && !currentCleanMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        cleanMode = true
                                        currentOnCleanModeChanged(true)
                                    } else if (newScale <= 1.01f && currentCleanMode) {
                                        cleanMode = false
                                        currentOnCleanModeChanged(false)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (pinching) {
                                // A finger lifted after pinching: continue with the
                                // remaining finger as pan (zoomed) or as a vertical
                                // single-finger zoom fallback while in clean mode.
                                pinching = false
                                val remaining = event.changes.firstOrNull { it.id == down.id }
                                if (remaining != null) {
                                    panLast = remaining.position
                                    panning = currentVideoScale > 1.001f && currentCleanMode
                                    singleFingerZoom =
                                        currentCleanMode && currentVideoScale <= 1.001f
                                    remaining.consume()
                                    continue
                                }
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val previousY = lastPosition.y
                            lastPosition = change.position
                            lastUptime = change.uptimeMillis
                            if (!change.pressed) {
                                finished = true
                                continue
                            }
                            val dx = lastPosition.x - startPosition.x
                            val dy = lastPosition.y - startPosition.y

                            if (speedHoldActive) {
                                updateSpeedHold(lastPosition.y, startPosition.y, previousY)
                                speedBoosting = true
                                change.consume()
                                continue
                            }
                            if (panning) {
                                val delta = lastPosition - panLast
                                panLast = lastPosition
                                videoOffset = clampVideoOffset(
                                    currentVideoOffset + delta,
                                    currentVideoScale,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                                pinchHandled = true
                                change.consume()
                                continue
                            }
                            if (singleFingerZoom && abs(dy) >= abs(dx)) {
                                val delta = lastPosition - panLast
                                panLast = lastPosition
                                val newScale =
                                    (currentVideoScale - delta.y / 420f).coerceIn(1f, 4f)
                                if (newScale <= 1.01f) {
                                    videoScale = 1f
                                    videoOffset = Offset.Zero
                                    if (currentCleanMode) {
                                        cleanMode = false
                                        currentOnCleanModeChanged(false)
                                    }
                                } else {
                                    videoScale = newScale
                                    if (newScale > 1.02f && !currentCleanMode) {
                                        cleanMode = true
                                        currentOnCleanModeChanged(true)
                                    }
                                    panning = true
                                }
                                pinchHandled = true
                                change.consume()
                                continue
                            }

                            if (isLongPress && inCenterBand) {
                                change.consume()
                            } else if (currentCleanMode && !isLongPress &&
                                abs(dx) > swipeThresholdPx && abs(dx) > abs(dy) * 1.5f
                            ) {
                                // Clean mode keeps its old behaviour: a horizontal
                                // swipe just restores the chrome (either direction).
                                swipeRight = true
                                change.consume()
                                cleanMode = false
                                currentOnCleanModeChanged(false)
                                finished = true
                            } else if (!isLongPress && !currentFullscreen && isPortraitScreen &&
                                dx < -swipeThresholdPx && abs(dx) > abs(dy) * 1.5f
                            ) {
                                // Portrait left-swipe opens the author page. Right
                                // swipes, landscape and fullscreen do nothing.
                                swipeRight = true
                                change.consume()
                                currentOnUserClick()
                                finished = true
                            }
                        }

                        // Released: refresh armed from the FINAL pointer position,
                        // then apply the lock / unlock (Douyin state machine).
                        if (speedHoldActive) {
                            endSpeedHold(lastPosition.y, startPosition.y)
                        }

                        if (!isLongPress && !swipeRight && !pinchHandled) {
                            val dx = lastPosition.x - startPosition.x
                            val dy = lastPosition.y - startPosition.y
                            val moved = hypot(dx, dy)
                            val duration = lastUptime - startTime
                            val isTap = moved <= viewConfiguration.touchSlop * 2f &&
                                duration <= viewConfiguration.longPressTimeoutMillis
                            if (isTap) {
                                if (currentCleanMode) {
                                    // A clean-screen tap only restores the overlays.
                                    cleanMode = false
                                    currentOnCleanModeChanged(false)
                                } else if (pendingTap?.isActive == true) {
                                    pendingTap?.cancel()
                                    pendingTap = null
                                    likeBurstPosition = lastPosition
                                    likeBurstTick++
                                    currentOnDoubleTapLike()
                                } else {
                                    pendingTap = gestureScope.launch {
                                        delay(260L)
                                        if (currentFullscreen) {
                                            val wasVisible = currentControlsVisible
                                            controlsVisible = true
                                            controlsPokeTick++
                                            if (wasVisible) currentTapTogglePlay()
                                        } else {
                                            currentTapTogglePlay()
                                            centerIconIsPause = playerManager.userPaused
                                            tapTick++
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
        )

        // 4. Center play/pause icon (tap feedback, hidden in fullscreen which has
        //    its own persistent play button).
        AnimatedVisibility(
            visible = centerIconVisible && !fullscreen && !cleanMode,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.7f, animationSpec = tween(140)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 1.25f, animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (centerIconIsPause) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (centerIconIsPause) {
                        stringResource(R.string.video_play)
                    } else {
                        stringResource(R.string.video_pause)
                    },
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(46.dp),
                )
            }
        }

        // 5. Buffering indicator (hidden while the user is scrubbing so the
        //    seek does not flash a spinner on every jump).
        val showBuffering = isCurrent && !showPlaybackError && !seeking && when {
            playbackState is PlaybackUiState.Loading -> true
            playbackState is PlaybackUiState.Ready && playerManager.isBuffering -> true
            else -> false
        }
        if (showBuffering) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp),
            )
        }

        // 6. Playback failure card with retry / source switching.
        if (showPlaybackError) {
            PlaybackErrorOverlay(
                message = errorMessage ?: stringResource(R.string.playback_error_message),
                onRetry = onRetryPlayback,
                useBrowser = playbackErrorExternal,
                onOpenUrl = { openUrl(context, playbackExternalUrl) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 7-8. Portrait feed chrome (action rail + description). The progress bar
        //      is a separate layer so it can stay visible in clean-screen mode.
        AnimatedVisibility(
            visible = !compact && !fullscreen && !cleanMode,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(200)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RightActionRail(
                    video = video,
                    liked = liked,
                    likeCount = likeCount,
                    isFavorited = isFavorited,
                    isFollowing = isFollowing,
                    onUserClick = onUserClick,
                    onLikeClick = onLikeClick,
                    onCommentClick = onCommentClick,
                    onFavoriteClick = onFavoriteClick,
                    onFollowClick = onFollowClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 140.dp),
                    speedGestureModifier = chromeSpeedGesture,
                )

                BottomVideoInfo(
                    video = video,
                    qualityLabel = qualityLabel,
                    onQualityClick = onQualityClick,
                    onUserClick = onUserClick,
                    onTagClick = onTagClick,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 88.dp, bottom = 56.dp),
                    speedGestureModifier = chromeSpeedGesture,
                )
            }
        }

        // 9b. Fullscreen gesture host (above the video, below the controls):
        //     horizontal drag = live seek; left vertical = brightness; right
        //     vertical = volume; long-press = temporary 2x (no lock, no hints).
        if (fullscreen && !fullscreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(video.id) {
                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startPosition = down.position
                            val startTime = down.uptimeMillis
                            val startSeek = playerManager.progressFraction()
                            val startBrightness = fsBrightness
                            val startVolume = fsVolume
                            var mode: FullscreenHudKind? = null
                            var longPressFired = false
                            var tempBoost = false
                            var moved = false
                            var lastPosition = startPosition
                            var lastUptime = startTime
                            var lastPokeMs = 0L
                            fun pokeThrottled() {
                                val now = android.os.SystemClock.uptimeMillis()
                                if (now - lastPokeMs > 220L) {
                                    lastPokeMs = now
                                    pokeFullscreenControls()
                                }
                            }
                            while (true) {
                                val timeout = if (longPressFired || mode != null) {
                                    Long.MAX_VALUE
                                } else {
                                    (350L - (lastUptime - startTime)).coerceAtLeast(1L)
                                }
                                val event = withTimeoutOrNull(timeout) { awaitPointerEvent() }
                                if (event == null) {
                                    if (!longPressFired && mode == null && !moved) {
                                        // Fullscreen: temp 2x only, never lock.
                                        longPressFired = true
                                        tempBoost = true
                                        fsBaseSpeed = playerManager.playbackSpeed
                                        fsTempSpeed = true
                                        playerManager.setPlaybackSpeed(2f)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    continue
                                }
                                event.changes.forEach { it.consume() }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null) break
                                lastUptime = change.uptimeMillis
                                lastPosition = change.position
                                if (!change.pressed) {
                                    if (tempBoost && fsTempSpeed) {
                                        fsTempSpeed = false
                                        playerManager.setPlaybackSpeed(fsBaseSpeed)
                                    }
                                    if (mode == FullscreenHudKind.SEEK) seeking = false
                                    adjustingLevel = null
                                    fsHud = null
                                    pokeFullscreenControls()
                                    val duration = lastUptime - startTime
                                    if (!moved && !longPressFired &&
                                        duration <= viewConfiguration.longPressTimeoutMillis
                                    ) {
                                        if (pendingTap?.isActive == true) {
                                            pendingTap?.cancel()
                                            pendingTap = null
                                            likeBurstPosition = lastPosition
                                            likeBurstTick++
                                            currentOnDoubleTapLike()
                                        } else {
                                            pendingTap = gestureScope.launch {
                                                delay(260L)
                                                val wasVisible = currentControlsVisible
                                                controlsVisible = true
                                                controlsPokeTick++
                                                if (wasVisible) currentTapTogglePlay()
                                            }
                                        }
                                    }
                                    break
                                }
                                val dx = lastPosition.x - startPosition.x
                                val dy = lastPosition.y - startPosition.y
                                if (mode == null &&
                                    hypot(dx, dy) > viewConfiguration.touchSlop
                                ) {
                                    moved = true
                                    mode = if (abs(dx) > abs(dy)) {
                                        FullscreenHudKind.SEEK
                                    } else if (startPosition.x < widthPx * 0.5f) {
                                        FullscreenHudKind.BRIGHTNESS
                                    } else {
                                        FullscreenHudKind.VOLUME
                                    }
                                    if (mode == FullscreenHudKind.SEEK) {
                                        seeking = true
                                        pokeFullscreenControls()
                                    } else {
                                        adjustingLevel = mode
                                    }
                                }
                                when (mode) {
                                    FullscreenHudKind.SEEK -> {
                                        val target = (startSeek + dx / widthPx).coerceIn(0f, 1f)
                                        playerManager.seekToFraction(target)
                                        fsHud = FullscreenHud(FullscreenHudKind.SEEK, target)
                                        pokeThrottled()
                                    }

                                    FullscreenHudKind.BRIGHTNESS -> {
                                        applyFullscreenBrightness(
                                            startBrightness - dy / heightPx,
                                        )
                                        fsHud = FullscreenHud(
                                            FullscreenHudKind.BRIGHTNESS,
                                            fsBrightness,
                                        )
                                        pokeThrottled()
                                    }

                                    FullscreenHudKind.VOLUME -> {
                                        applyFullscreenVolume(startVolume - dy / heightPx)
                                        fsHud = FullscreenHud(
                                            FullscreenHudKind.VOLUME,
                                            fsVolume,
                                        )
                                        pokeThrottled()
                                    }

                                    null -> Unit
                                }
                            }
                        }
                    },
            )
        }

        // 10. Fullscreen control layer: back + title + author row + share on top,
        //     centre play button, bottom time / seek / like-comment-favorite.
        //     Auto-fades after ~3s without interaction; tapping the video shows it
        //     again / hides it. Every control interaction restarts the countdown.
        AnimatedVisibility(
            visible = fullscreen && controlsVisible && !cleanMode && !fullscreenLocked,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
        ) {
            FullscreenControls(
                video = video,
                playerManager = playerManager,
                liked = liked,
                likeCount = likeCount,
                isFavorited = isFavorited,
                isFollowing = isFollowing,
                progress = progress,
                durationMs = playerManager.currentDurationMs(),
                brightness = fsBrightness,
                onBrightnessChange = { value ->
                    pokeFullscreenControls()
                    val clamped = value.coerceIn(0.05f, 1f)
                    applyFullscreenBrightness(clamped)
                    if (adjustingLevel == FullscreenHudKind.BRIGHTNESS) {
                        fsHud = FullscreenHud(FullscreenHudKind.BRIGHTNESS, clamped)
                    }
                },
                onBrightnessAdjustStart = {
                    adjustingLevel = FullscreenHudKind.BRIGHTNESS
                    fsHud = FullscreenHud(FullscreenHudKind.BRIGHTNESS, fsBrightness)
                },
                onBrightnessAdjustEnd = {
                    adjustingLevel = null
                    fsHud = null
                    pokeFullscreenControls()
                },
                hideBrightnessIcon = adjustingLevel == FullscreenHudKind.BRIGHTNESS,
                volume = fsVolume,
                onVolumeChange = { value ->
                    pokeFullscreenControls()
                    val clamped = value.coerceIn(0f, 1f)
                    applyFullscreenVolume(clamped)
                    if (adjustingLevel == FullscreenHudKind.VOLUME) {
                        fsHud = FullscreenHud(FullscreenHudKind.VOLUME, clamped)
                    }
                },
                onVolumeAdjustStart = {
                    adjustingLevel = FullscreenHudKind.VOLUME
                    fsHud = FullscreenHud(FullscreenHudKind.VOLUME, fsVolume)
                },
                onVolumeAdjustEnd = {
                    adjustingLevel = null
                    fsHud = null
                    pokeFullscreenControls()
                },
                hideVolumeIcon = adjustingLevel == FullscreenHudKind.VOLUME,
                onLock = {
                    fullscreenLocked = true
                    fsHud = null
                },
                onTogglePlay = {
                    pokeFullscreenControls()
                    onTapTogglePlay()
                },
                onLikeClick = {
                    pokeFullscreenControls()
                    onLikeClick()
                },
                onCommentClick = onCommentClick,
                onFavoriteClick = {
                    pokeFullscreenControls()
                    onFavoriteClick()
                },
                onFollowClick = {
                    pokeFullscreenControls()
                    onFollowClick()
                },
                onShareClick = {
                    pokeFullscreenControls()
                    onShareClick()
                },
                onUserClick = onUserClick,
                onExit = { onExitFullscreen?.invoke() },
                onSeek = { fraction ->
                    pokeFullscreenControls()
                    playerManager.seekToFraction(fraction)
                },
                onSeekingChanged = { active ->
                    if (active) pokeFullscreenControls()
                    seeking = active
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (fullscreen && fsHud != null) {
            FullscreenGestureHud(
                hud = fsHud!!,
                durationMs = playerManager.currentDurationMs(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 10a-bis. Fullscreen temporary 2x pill ("倍速中"): no lock, no hints.
        AnimatedVisibility(
            visible = fullscreen && fsTempSpeed && !fullscreenLocked,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 66.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = FlowPink,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.fullscreen_speed_boosting),
                    color = FlowPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 10a-ter. Fullscreen locked: swallow every gesture, only unlock remains.
        if (fullscreen && fullscreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                pressed = event.changes.any { it.pressed }
                            }
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 14.dp),
            ) {
                ShadowedIconButton(
                    icon = Icons.Rounded.LockOpen,
                    contentDescription = stringResource(R.string.fullscreen_unlock),
                    size = 24.dp,
                    onClick = {
                        fullscreenLocked = false
                        pokeFullscreenControls()
                    },
                )
            }
        }

        // 10b. "全屏观看" capsule, centred below the landscape video's bottom edge.
        if (showFullscreenPill) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = fullscreenPillTop)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
                    .clickable { onEnterFullscreen?.invoke() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fullscreen_watch),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }


        // 12. Single top hint capsule for the 2x state machine (below the feed
        //     tabs / tag row). All hold / lock-notice states share this one
        //     component; nothing stays on screen after the notice fades.
        AnimatedVisibility(
            visible = (speedHintVisible || speedLockedNoticeVisible) && !cleanMode,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f, animationSpec = tween(160)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (fullscreen) 74.dp else 100.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        speedHintVisible && speedLocked && speedHintArmed ->
                            stringResource(R.string.speed_hint_release_unlock)
                        speedHintVisible && speedLocked ->
                            stringResource(R.string.speed_hint_hold_unlock)
                        speedHintVisible && speedHintArmed ->
                            stringResource(R.string.speed_hint_release_lock)
                        speedHintVisible -> stringResource(R.string.speed_hint_hold_lock)
                        speedLockedNoticeVisible && speedNoticeCancelled ->
                            stringResource(R.string.speed_unlocked_toast)
                        speedLockedNoticeVisible -> stringResource(R.string.speed_locked_toast)
                        // Nothing active: keep the exit animation blank instead of
                        // falling through to a stale "locked" message.
                        else -> ""
                    },
                    color = FlowPink,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 13. Like burst animation.
        LikeBurst(trigger = likeBurstTick, position = likeBurstPosition)

        // 14. Clean-screen gesture host: topmost and self-contained while the
        //     chrome is hidden. It consumes every pointer so a vertical drag can
        //     never reach the pager: single finger zooms (scale≈1) or pans
        //     (scale>1), two fingers pinch-zoom/pan, a clean tap exits.
        if (cleanMode && !gestureMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(video.id) {
                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startTime = down.uptimeMillis
                            var anchorPosition = down.position
                            var moved = false
                            var pinching = false
                            var pinchStartScale = 1f
                            var pinchStartOffset = Offset.Zero
                            var pinchStartCentroid = Offset.Zero
                            var initialPinchDistance = 0f
                            var ended = false
                            while (!ended) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    moved = true
                                    val points = pressed.map { it.position }
                                    val distance = hypot(
                                        points[0].x - points[1].x,
                                        points[0].y - points[1].y,
                                    )
                                    val centroid = Offset(
                                        points.map { it.x }.average().toFloat(),
                                        points.map { it.y }.average().toFloat(),
                                    )
                                    if (!pinching) {
                                        pinching = true
                                        initialPinchDistance = distance.coerceAtLeast(1f)
                                        pinchStartScale = videoScale
                                        pinchStartOffset = videoOffset
                                        pinchStartCentroid = centroid
                                    } else {
                                        val zoom = distance / initialPinchDistance
                                        val newScale = (pinchStartScale * zoom).coerceIn(1f, 4f)
                                        if (newScale > 1.001f) {
                                            videoScale = newScale
                                            videoOffset = clampVideoOffset(
                                                pinchStartOffset + (centroid - pinchStartCentroid),
                                                newScale,
                                                widthPx,
                                                heightPx,
                                            )
                                        } else {
                                            videoScale = 1f
                                            videoOffset = Offset.Zero
                                        }
                                        if (newScale <= 1.01f) {
                                            cleanMode = false
                                            currentOnCleanModeChanged(false)
                                        }
                                    }
                                    continue
                                }
                                if (pinching) {
                                    pinching = false
                                    pressed.firstOrNull()?.let { anchorPosition = it.position }
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null) {
                                    ended = true
                                    continue
                                }
                                if (!change.pressed) {
                                    ended = true
                                    if (!moved &&
                                        change.uptimeMillis - startTime <=
                                        viewConfiguration.longPressTimeoutMillis
                                    ) {
                                        // Clean tap: restore the chrome, keep zoom.
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        cleanMode = false
                                        currentOnCleanModeChanged(false)
                                    }
                                    continue
                                }
                                val delta = change.position - anchorPosition
                                anchorPosition = change.position
                                if (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f) {
                                    moved = true
                                }
                                if (videoScale > 1.001f) {
                                    // Zoomed: single-finger drag pans (clamped).
                                    videoOffset = clampVideoOffset(
                                        videoOffset + delta,
                                        videoScale,
                                        widthPx,
                                        heightPx,
                                    )
                                } else {
                                    // At 1x: vertical drag zooms (adb-friendly fallback).
                                    val newScale =
                                        (videoScale - delta.y / 420f).coerceIn(1f, 4f)
                                    if (newScale <= 1.01f) {
                                        videoScale = 1f
                                        videoOffset = Offset.Zero
                                    } else {
                                        videoScale = newScale
                                    }
                                }
                            }
                        }
                    },
            )
        }

        // 14b. Draggable progress bar: kept visible in clean-screen mode and
        //      composed above the clean host so it can still be dragged.
        AnimatedVisibility(
            visible = !compact && !fullscreen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                DraggableProgressBar(
                    progress = if (isCurrent) progress else 0f,
                    durationMs = playerManager.currentDurationMs(),
                    enabled = isCurrent,
                    onSeek = { fraction -> playerManager.seekToFraction(fraction) },
                    onSeekingChanged = { active -> seeking = active },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }

        // 14c. Playback speed capsule (bottom-right) in clean-screen mode: tap to
        //      cycle 0.5x -> 0.75x -> 1x -> 1.25x -> 1.5x -> 2x -> 3x.
        AnimatedVisibility(
            visible = cleanMode && !gestureMenuOpen,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 44.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clickable {
                        val speeds = SpeedCycle
                        val current = playerManager.playbackSpeed
                        val idx = speeds.indexOfFirst { abs(it - current) < 0.01f }
                        val next = if (idx < 0) 1f else speeds[(idx + 1) % speeds.size]
                        speedLocked = false
                        speedBoosting = false
                        speedHintVisible = false
                        speedLockedNoticeVisible = false
                        playerManager.setPlaybackSpeed(next)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = speedLabel(playerManager.playbackSpeed),
                    color = FlowPink,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 15. "还原" pill (bottom-left) while the picture is zoomed/panned.
        AnimatedVisibility(
            visible = videoScale > 1.001f || videoOffset != Offset.Zero,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f, animationSpec = tween(160)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.9f, animationSpec = tween(160)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clickable {
                        resetVideoTransform()
                        if (cleanMode) {
                            cleanMode = false
                            onCleanModeChanged(false)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = FlowTextPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.video_transform_reset),
                    color = FlowTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 16. Centre long-press action sheet (topmost layer).
        AnimatedVisibility(
            visible = gestureMenuOpen,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(160)),
        ) {
            VideoGestureMenu(
                isFavorited = isFavorited,
                currentSpeed = playerManager.playbackSpeed,
                onDismiss = { gestureMenuOpen = false },
                onSpeedSelected = { speed ->
                    baseSpeed = speed
                    speedLocked = false
                    speedLockedNoticeVisible = false
                    playerManager.setPlaybackSpeed(speed)
                    gestureMenuOpen = false
                },
                onCleanScreen = {
                    gestureMenuOpen = false
                    cleanMode = true
                    onCleanModeChanged(true)
                },
                onFavorite = {
                    gestureMenuOpen = false
                    onFavoriteClick()
                },
                onDownload = {
                    gestureMenuOpen = false
                    val ready = playbackState as? PlaybackUiState.Ready
                    val source = ready?.info?.primary
                    if (source == null) {
                        Toast.makeText(context, R.string.download_not_ready, Toast.LENGTH_SHORT).show()
                    } else if (VideoDownloader.enqueue(context, video, source)) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.download_started,
                                "${video.title?.takeIf { it.isNotBlank() } ?: untitledFallback} (${source.label})",
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    gestureMenuOpen = false
                    onShareClick()
                },
            )
        }
    }
}

/* ------------------------------------------------------------------------- *
 * TikTok-style action rail
 * ------------------------------------------------------------------------- */

/**
 * TikTok-style follow affordance: a red "+" while not following. When the user
 * follows, it flashes a check for ~0.9s and then disappears entirely instead of
 * leaving a permanent tick on the avatar.
 */
@Composable
private fun FollowBadge(
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
    innerSize: Dp = 18.dp,
    iconSize: Dp = 12.dp,
) {
    var showCheck by remember { mutableStateOf(false) }
    var wasFollowing by remember { mutableStateOf(isFollowing) }
    LaunchedEffect(isFollowing) {
        if (isFollowing && !wasFollowing) {
            showCheck = true
            delay(900)
            showCheck = false
        }
        wasFollowing = isFollowing
    }
    AnimatedVisibility(
        visible = !isFollowing || showCheck,
        modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.75f, animationSpec = tween(130)),
        exit = fadeOut(tween(170)) + scaleOut(targetScale = 0.8f, animationSpec = tween(170)),
    ) {
        Box(
            modifier = Modifier
                .size(innerSize + 6.dp)
                .clip(CircleShape)
                .clickable(onClick = onFollowClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .clip(CircleShape)
                    .background(if (isFollowing) Color.White.copy(alpha = 0.16f) else TikTokRed)
                    .border(
                        width = 1.5.dp,
                        color = if (isFollowing) Color.White.copy(alpha = 0.85f) else Color.White,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFollowing) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(
                        if (isFollowing) R.string.cd_unfollow else R.string.cd_follow,
                    ),
                    tint = if (isFollowing) FlowPink else Color.White,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun RightActionRail(
    video: IwaraVideo,
    liked: Boolean,
    likeCount: Int,
    isFavorited: Boolean,
    isFollowing: Boolean,
    onUserClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
    speedGestureModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 60.dp).then(speedGestureModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(
                user = video.user,
                size = 44.dp,
                borderWidth = 1.5.dp,
                onClick = onUserClick,
            )
            // Real follow button (+ / ✓) on the avatar's bottom-right corner.
            // A 24dp touch target keeps it clearly separated from the avatar.
            FollowBadge(
                isFollowing = isFollowing,
                onFollowClick = onFollowClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp),
                innerSize = 18.dp,
                iconSize = 12.dp,
            )
        }
        Spacer(Modifier.height(4.dp))
        RailIconAction(
            icon = Icons.Rounded.Favorite,
            contentDescription = stringResource(R.string.cd_like),
            tint = if (liked) FlowPink else Color.White,
            labelTint = if (liked) FlowPink else Color.White,
            label = localizedCount(likeCount),
            onClick = onLikeClick,
        )
        RailIconAction(
            icon = Icons.AutoMirrored.Rounded.Comment,
            contentDescription = stringResource(R.string.cd_comment),
            label = localizedCount(video.numComments),
            onClick = onCommentClick,
        )
        RailIconAction(
            icon = if (isFavorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = stringResource(
                if (isFavorited) R.string.video_favorited else R.string.video_favorite,
            ),
            tint = if (isFavorited) FlowPink else Color.White,
            labelTint = if (isFavorited) FlowPink else Color.White,
            label = stringResource(
                if (isFavorited) R.string.video_favorited else R.string.video_favorite,
            ),
            onClick = onFavoriteClick,
        )
    }
}

/** Plain white icon + small label below it (no circle background, TikTok style). */
@Composable
private fun RailIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    labelTint: Color = Color.White,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 6.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShadowedIcon(
            icon = icon,
            tint = tint,
            contentDescription = contentDescription,
            size = 30.dp,
        )
        if (label != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = labelTint.copy(alpha = 0.96f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** White glyph with a cheap drop shadow (a black copy offset behind it). */
@Composable
private fun ShadowedIcon(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    size: Dp,
) {
    Box {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.40f),
            modifier = Modifier
                .size(size)
                .offset(x = 1.dp, y = 1.dp),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/* ------------------------------------------------------------------------- *
 * Fullscreen (landscape) control layer
 * ------------------------------------------------------------------------- */

private val FullscreenTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(1f, 1f),
    blurRadius = 3f,
)

@Composable
private fun FullscreenRoundButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FullscreenLevelButton(
    icon: ImageVector,
    value: Float,
    contentDescription: String,
    onValueChange: (Float) -> Unit,
    onAdjustStart: () -> Unit,
    onAdjustEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestValue by rememberUpdatedState(value)
    val trackPx = with(LocalDensity.current) { 190.dp.toPx() }
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .pointerInput(Unit) {
                var dragValue = latestValue
                detectVerticalDragGestures(
                    onDragStart = {
                        dragValue = latestValue
                        onAdjustStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragValue = (dragValue - dragAmount / trackPx).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                    onDragEnd = { onAdjustEnd() },
                    onDragCancel = { onAdjustEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FullscreenControls(
    video: IwaraVideo,
    playerManager: PlayerManager,
    liked: Boolean,
    likeCount: Int,
    isFavorited: Boolean,
    isFollowing: Boolean,
    progress: Float,
    durationMs: Long,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessAdjustStart: () -> Unit,
    onBrightnessAdjustEnd: () -> Unit,
    hideBrightnessIcon: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeAdjustStart: () -> Unit,
    onVolumeAdjustEnd: () -> Unit,
    hideVolumeIcon: Boolean,
    onLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onUserClick: () -> Unit,
    onExit: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Very faint top gradient for readability (the bottom area is untouched).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                    ),
                ),
        )

        // Top block: back button + title/tags + creator row on the left, share on
        // the top-right. Generous top padding keeps it off the screen edge.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, top = 18.dp, end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShadowedIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.video_exit_fullscreen),
                        size = 26.dp,
                        onClick = onExit,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = localizedVideoTitle(video),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = FullscreenTextShadow),
                        modifier = Modifier.weight(1f),
                    )
                }
                // No tags in fullscreen: only title + creator, with a tight gap.
                Spacer(Modifier.height(8.dp))
                // Creator row (avatar + displayName + real follow badge).
                Row(
                    modifier = Modifier.padding(start = 58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        user = video.user,
                        size = 32.dp,
                        borderWidth = 1.5.dp,
                        onClick = onUserClick,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = video.user?.displayName?.takeIf { it.isNotBlank() } ?: "Iwara",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        style = TextStyle(shadow = FullscreenTextShadow),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onUserClick),
                    )
                    Spacer(Modifier.width(7.dp))
                    FollowBadge(
                        isFollowing = isFollowing,
                        onFollowClick = onFollowClick,
                        innerSize = 17.dp,
                        iconSize = 11.dp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // Lock button restored to the original top-right position.
            ShadowedIconButton(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.fullscreen_lock),
                size = 22.dp,
                onClick = onLock,
            )
            Spacer(Modifier.width(6.dp))
            // Share on the top-right; the weighted title/tags column keeps clear.
            ShadowedIconButton(
                icon = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.cd_share),
                size = 24.dp,
                onClick = onShareClick,
            )
        }

        // Default Douyin-style fullscreen chrome: only round icon buttons.
        // The actual level bars appear temporarily while dragging (HUD).
        FullscreenLevelButton(
            icon = Icons.Rounded.Brightness6,
            value = brightness,
            contentDescription = stringResource(R.string.fullscreen_brightness),
            onValueChange = onBrightnessChange,
            onAdjustStart = onBrightnessAdjustStart,
            onAdjustEnd = onBrightnessAdjustEnd,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .alpha(if (hideBrightnessIcon) 0f else 1f),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FullscreenLevelButton(
                icon = Icons.Rounded.VolumeUp,
                value = volume,
                contentDescription = stringResource(R.string.fullscreen_volume),
                onValueChange = onVolumeChange,
                onAdjustStart = onVolumeAdjustStart,
                onAdjustEnd = onVolumeAdjustEnd,
                modifier = Modifier.alpha(if (hideVolumeIcon) 0f else 1f),
            )
        }

        // ------------------------------------------ centre play (when paused)
        if (!playerManager.isPlaying && !playerManager.isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                ShadowedIcon(
                    icon = Icons.Rounded.PlayArrow,
                    tint = Color.White.copy(alpha = 0.92f),
                    contentDescription = stringResource(R.string.video_play),
                    size = 62.dp,
                )
            }
        }

        // ----------------------- bottom: time / draggable seek / action row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            val durationSeconds = (durationMs / 1000L).toInt()
            val currentSeconds = (durationSeconds * progress.coerceIn(0f, 1f)).toInt()
            Text(
                text = "${formatDuration(currentSeconds)} / ${formatDuration(durationSeconds)}",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = FullscreenTextShadow),
            )
            Spacer(Modifier.height(2.dp))
            DraggableProgressBar(
                progress = progress,
                durationMs = durationMs,
                enabled = true,
                onSeek = onSeek,
                onSeekingChanged = onSeekingChanged,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                FullscreenActionItem(
                    icon = Icons.Rounded.Favorite,
                    tint = if (liked) FlowPink else Color.White,
                    label = localizedCount(likeCount),
                    contentDescription = stringResource(R.string.cd_like),
                    onClick = onLikeClick,
                )
                FullscreenActionItem(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    label = localizedCount(video.numComments),
                    contentDescription = stringResource(R.string.cd_comment),
                    onClick = onCommentClick,
                )
                FullscreenActionItem(
                    icon = if (isFavorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    tint = if (isFavorited) FlowPink else Color.White,
                    label = stringResource(
                        if (isFavorited) R.string.video_favorited else R.string.video_favorite,
                    ),
                    contentDescription = stringResource(
                        if (isFavorited) R.string.video_favorited else R.string.video_favorite,
                    ),
                    onClick = onFavoriteClick,
                )
            }
        }
    }
}

@Composable
private fun ShadowedIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ShadowedIcon(
            icon = icon,
            tint = Color.White,
            contentDescription = contentDescription,
            size = size,
        )
    }
}

@Composable
private fun FullscreenActionItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowedIcon(
            icon = icon,
            tint = tint,
            contentDescription = contentDescription,
            size = 26.dp,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = tint.copy(alpha = 0.96f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            style = TextStyle(shadow = FullscreenTextShadow),
        )
    }
}

/* ------------------------------------------------------------------------- *
 * Bottom description block
 * ------------------------------------------------------------------------- */

@Composable
private fun BottomVideoInfo(
    video: IwaraVideo,
    qualityLabel: String?,
    onQualityClick: (() -> Unit)?,
    onUserClick: () -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    speedGestureModifier: Modifier = Modifier,
) {
    val username = video.user?.username?.takeIf { it.isNotBlank() }
    val displayName = video.user?.displayName?.takeIf { it.isNotBlank() }
        ?: username
        ?: "Iwara"
    val tags = remember(video.id) {
        video.tags
            .asSequence()
            .filter { it.id.isNotBlank() }
            .take(3)
            .toList()
    }
    Column(modifier = modifier.then(speedGestureModifier)) {
        // Author line: names only; the rating badge now lives in the meta row.
        val rating = localizedRatingLabel(video.rating)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onUserClick)
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "@$displayName",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (username != null && displayName != username) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "@$username",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        var titleExpanded by remember(video.id) { mutableStateOf(false) }
        var titleOverflows by remember(video.id) { mutableStateOf(false) }
        Text(
            text = localizedVideoTitle(video),
            color = Color.White.copy(alpha = 0.96f),
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!titleExpanded) {
                    titleOverflows = layout.hasVisualOverflow || layout.lineCount > 2
                }
            },
        )
        if (titleOverflows) {
            Text(
                text = stringResource(
                    if (titleExpanded) R.string.video_collapse else R.string.video_expand,
                ),
                color = FlowPink,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { titleExpanded = !titleExpanded }
                    .padding(top = 2.dp, bottom = 1.dp, end = 6.dp),
            )
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            // Plain pink highlighted text (no chip background), each tappable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = "#${tagLabel(tag.id)}",
                        color = FlowPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onTagClick(tag.id) }
                            .padding(vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.views_count, localizedCount(video.numViews)),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
            )
            val date = localizedRelativeDate(video.createdAt)
            if (date.isNotBlank()) {
                Text(
                    text = "  ·  $date",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val qualityBadge = if (qualityLabel.isNullOrBlank()) {
                ""
            } else {
                localizedPlaybackQualityLabel(qualityLabel)
            }
            if (qualityBadge.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .then(
                            if (onQualityClick != null) {
                                Modifier.clickable(onClick = onQualityClick)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                ) {
                    Text(
                        text = stringResource(R.string.video_quality_badge, qualityBadge),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (rating.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                ) {
                    Text(
                        text = rating,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Draggable progress bar (TikTok style): thin visible track, large touch area,
 * live seek + current / total time bubble while dragging.
 * ------------------------------------------------------------------------- */

@Composable
private fun DraggableProgressBar(
    progress: Float,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    onSeekingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }

    val displayFraction = (if (dragging) dragFraction else progress).coerceIn(0f, 1f)
    val durationSeconds = (durationMs / 1000L).toInt()

    fun setDragging(active: Boolean) {
        if (dragging != active) {
            dragging = active
            onSeekingChanged(active)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Time bubble shown only while dragging.
        if (dragging) {
            val seekSeconds = (durationSeconds * dragFraction).toInt()
            Box(
                modifier = Modifier
                    .padding(bottom = 7.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "${formatDuration(seekSeconds)} / ${formatDuration(durationSeconds)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            setDragging(true)
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            lastSeekAt = System.currentTimeMillis()
                            onSeek(dragFraction)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (now - lastSeekAt >= 90L) {
                                lastSeekAt = now
                                onSeek(dragFraction)
                            }
                        },
                        onDragEnd = {
                            setDragging(false)
                            onSeek(dragFraction)
                        },
                        onDragCancel = { setDragging(false) },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    detectTapGestures { offset ->
                        onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                    }
                },
        ) {
            // Visible track: only 2.5dp tall, the 26dp box is the touch target.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(displayFraction)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(BrandGradient),
            )
            // Small thumb for the drag affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val x = (maxWidth * displayFraction - 5.dp)
                            .coerceIn(0.dp, (maxWidth - 10.dp).coerceAtLeast(0.dp))
                        IntOffset(x.roundToPx(), 0)
                    }
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    useBrowser: Boolean,
    onOpenUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localizedMessage = localizedErrorMessage(LocalContext.current, message)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FlowSurfaceGlass)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = FlowPink,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.playback_error_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = localizedMessage,
                color = FlowTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandGradient)
                    .pointerInput(useBrowser, onOpenUrl) {
                        detectTapGestures(
                            onTap = { if (useBrowser) onOpenUrl() else onRetry() },
                        )
                    }
                    .padding(horizontal = 26.dp, vertical = 11.dp),
            ) {
                Text(
                    text = stringResource(
                        if (useBrowser) R.string.video_open_in_browser else R.string.video_retry_switch,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Double-tap "like" burst: one large heart plus a few floating mini hearts.
 * ------------------------------------------------------------------------- */

private data class BurstParticle(
    val dxDp: Float,
    val dyDp: Float,
    val riseDp: Float,
    val rotation: Float,
    val sizeDp: Float,
    val tint: Color,
)

private fun burstParticles(): List<BurstParticle> = listOf(
    BurstParticle(dxDp = -42f, dyDp = 10f, riseDp = 92f, rotation = -22f, sizeDp = 15f, tint = FlowPinkSoft),
    BurstParticle(dxDp = 38f, dyDp = -6f, riseDp = 116f, rotation = 18f, sizeDp = 17f, tint = FlowVioletSoft),
    BurstParticle(dxDp = -16f, dyDp = -34f, riseDp = 138f, rotation = -8f, sizeDp = 13f, tint = FlowPink),
    BurstParticle(dxDp = 18f, dyDp = 30f, riseDp = 84f, rotation = 26f, sizeDp = 14f, tint = FlowPinkSoft),
    BurstParticle(dxDp = 0f, dyDp = 2f, riseDp = 158f, rotation = 4f, sizeDp = 19f, tint = FlowVioletSoft),
)

@Composable
private fun LikeBurst(trigger: Int, position: Offset?) {
    if (trigger <= 0 || position == null) return

    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val particles = remember { burstParticles() }

    LaunchedEffect(trigger) {
        heartScale.snapTo(0.2f)
        heartAlpha.snapTo(0f)
        burst.snapTo(0f)
        launch {
            heartScale.animateTo(
                targetValue = 1.12f,
                animationSpec = spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessHigh),
            )
            heartScale.animateTo(targetValue = 1f, animationSpec = tween(110))
        }
        heartAlpha.animateTo(1f, tween(90))
        launch {
            burst.animateTo(1f, tween(durationMillis = 760, easing = LinearOutSlowInEasing))
        }
        delay(380)
        heartAlpha.animateTo(0f, tween(240))
        heartScale.animateTo(1.5f, tween(240))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    val half = 48.dp.toPx()
                    translationX = position.x - half
                    translationY = position.y - half
                    alpha = heartAlpha.value
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                },
        )
        particles.forEach { particle ->
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = particle.tint,
                modifier = Modifier
                    .size(particle.sizeDp.dp)
                    .graphicsLayer {
                        val half = (particle.sizeDp / 2f).dp.toPx()
                        translationX = position.x - half + (particle.dxDp * burst.value).dp.toPx()
                        translationY = position.y - half + (particle.dyDp * burst.value - particle.riseDp * burst.value).dp.toPx()
                        alpha = (1f - burst.value).coerceIn(0f, 1f)
                        rotationZ = particle.rotation * burst.value
                        val scale = 0.7f + 0.6f * burst.value
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Douyin-style gesture helpers
 * ------------------------------------------------------------------------- */

/** Centre long-press action sheet (Obsidian x Champagne). */
@Composable
private fun VideoGestureMenu(
    isFavorited: Boolean,
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onCleanScreen: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    val sheetScope = rememberCoroutineScope()
    val sheetDragOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, sheetDragOffset.value.roundToInt()) }
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121214))
                .border(1.dp, FlowOutline, RoundedCornerShape(20.dp))
                .draggable(
                    state = rememberDraggableState { delta ->
                        sheetScope.launch {
                            sheetDragOffset.snapTo(
                                (sheetDragOffset.value + delta).coerceAtLeast(0f),
                            )
                        }
                    },
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        if (sheetDragOffset.value > dismissThresholdPx) {
                            onDismiss()
                        } else {
                            sheetScope.launch { sheetDragOffset.animateTo(0f) }
                        }
                    },
                )
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.gesture_menu_speed),
                color = FlowTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            // 7 speeds in two equal-width rows (4 + 3) so every label fits.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedCycle.take(4).forEach { speed ->
                        SpeedChip(
                            speed = speed,
                            currentSpeed = currentSpeed,
                            onPick = onSpeedSelected,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedCycle.drop(4).forEach { speed ->
                        SpeedChip(
                            speed = speed,
                            currentSpeed = currentSpeed,
                            onPick = onSpeedSelected,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = FlowOutline.copy(alpha = 0.6f))
            GestureMenuRow(
                icon = Icons.Rounded.Fullscreen,
                label = stringResource(R.string.gesture_menu_clean),
                onClick = onCleanScreen,
            )
            GestureMenuRow(
                icon = if (isFavorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = stringResource(
                    if (isFavorited) R.string.video_favorited else R.string.video_favorite,
                ),
                onClick = onFavorite,
            )
            GestureMenuRow(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.video_download),
                onClick = onDownload,
            )
            GestureMenuRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.cd_share),
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    currentSpeed: Float,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = abs(currentSpeed - speed) < 0.01f
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(ChampagneGradient)
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.06f))
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else FlowOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onPick(speed) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speedLabel(speed),
            color = if (selected) Color(0xFF17120A) else FlowTextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun GestureMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlowTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = FlowTextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Clamps a pan offset so a zoomed video never exposes empty space. */
private fun clampVideoOffset(offset: Offset, scale: Float, width: Float, height: Float): Offset {
    if (scale <= 1.001f) return Offset.Zero
    val maxX = (scale - 1f) * width / 2f
    val maxY = (scale - 1f) * height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Speeds offered by the long-press menu and cycled by the clean-screen capsule. */
private val SpeedCycle = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

private fun speedLabel(speed: Float): String = "${speed}x"

/* ------------------------------------------------------------------------- *
 * Fullscreen gesture HUD + level sliders (brightness / volume).
 * ------------------------------------------------------------------------- */

private enum class FullscreenHudKind { SEEK, BRIGHTNESS, VOLUME }

private data class FullscreenHud(val kind: FullscreenHudKind, val value: Float)

@Composable
private fun FullscreenGestureHud(
    hud: FullscreenHud,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (hud.kind) {
            FullscreenHudKind.SEEK -> {
                val totalSeconds = (durationMs / 1000L).toInt()
                val currentSeconds = (totalSeconds * hud.value).toInt()
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(percent = 50),
                        )
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = FlowPink,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatDuration(currentSeconds)} / ${formatDuration(totalSeconds)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            FullscreenHudKind.BRIGHTNESS -> FullscreenLevelHud(
                icon = Icons.Rounded.Brightness6,
                value = hud.value,
                contentDescription = stringResource(R.string.fullscreen_brightness),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp),
            )

            FullscreenHudKind.VOLUME -> FullscreenLevelHud(
                icon = Icons.Rounded.VolumeUp,
                value = hud.value,
                contentDescription = stringResource(R.string.fullscreen_volume),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
            )
        }
    }
}

@Composable
private fun FullscreenLevelHud(
    icon: ImageVector,
    value: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.34f)),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LevelTrack(value: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(5.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(percent = 50))
                .background(FlowPink),
        )
    }
}
