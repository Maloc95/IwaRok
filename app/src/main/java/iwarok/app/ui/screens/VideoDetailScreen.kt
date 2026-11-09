package iwarok.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.IwaraUser
import iwarok.app.core.IwaraVideo
import iwarok.app.core.QualityPreference
import iwarok.app.ui.components.ErrorState
import iwarok.app.ui.components.LoadingState
import iwarok.app.ui.feed.CommentsPanel
import iwarok.app.ui.feed.PlaybackUiState
import iwarok.app.ui.feed.VideoPage
import iwarok.app.ui.player.PlayerManager
import iwarok.app.ui.player.VideoSurface
import iwarok.app.ui.theme.TopScrimGradient
import iwarok.app.ui.util.FavoritesStore
import iwarok.app.ui.util.HistoryStore
import iwarok.app.ui.util.rememberFullscreenController
import iwarok.app.ui.util.shareVideo
import kotlinx.coroutines.launch

/** Fraction of the screen height used by the video while comments are open. */
private const val DETAIL_COMMENT_VIDEO_FRACTION = 0.44f

/**
 * In-app video detail / playback screen.
 *
 * Reuses the feed's full-screen [VideoPage] (title / author / tags / quality /
 * like / comment / share / mute / draggable progress) plus the shared
 * [PlayerManager] and [VideoSurface]. Comments use the half-screen
 * [CommentsPanel] with the video continuing to play on top.
 */
@Composable
fun VideoDetailScreen(
    graph: AppGraph,
    videoId: String,
    quality: QualityPreference,
    playerManager: PlayerManager,
    favorites: FavoritesStore,
    history: HistoryStore,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onRequestLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var video by remember(videoId) { mutableStateOf<IwaraVideo?>(null) }
    var detailLoading by remember(videoId) { mutableStateOf(true) }
    var detailError by remember(videoId) { mutableStateOf<String?>(null) }
    var reloadTick by remember(videoId) { mutableIntStateOf(0) }

    var playbackState by remember(videoId) { mutableStateOf<PlaybackUiState?>(null) }
    var playbackAttempt by remember(videoId) { mutableIntStateOf(0) }
    var likedOverride by remember(videoId) { mutableStateOf<Boolean?>(null) }
    var followOverride by remember(videoId) { mutableStateOf<Boolean?>(null) }

    var commentsOpen by remember(videoId) { mutableStateOf(false) }
    var commentsVideo by remember(videoId) { mutableStateOf<IwaraVideo?>(null) }
    // Two-finger clean-screen mode: hides the page's own overlays.
    var cleanMode by remember(videoId) { mutableStateOf(false) }
    // Pinch zoom / pan applied to this screen's own VideoSurface.
    val surfaceScale = remember { mutableFloatStateOf(1f) }
    val surfaceOffsetX = remember { mutableFloatStateOf(0f) }
    val surfaceOffsetY = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(videoId) {
        surfaceScale.floatValue = 1f
        surfaceOffsetX.floatValue = 0f
        surfaceOffsetY.floatValue = 0f
    }

    // Landscape fullscreen + local favorites state.
    val fullscreenController = rememberFullscreenController()
    val fullscreen = fullscreenController.isFullscreen
    val favoritesState by favorites.items.collectAsState()
    val isFavorited = favoritesState.any { it.id == videoId }
    BackHandler(enabled = fullscreen) { fullscreenController.exit() }

    val videoAreaFraction by animateFloatAsState(
        targetValue = if (commentsOpen) DETAIL_COMMENT_VIDEO_FRACTION else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "detail_video_area_fraction",
    )

    // Video metadata (title / author / tags / rating ...).
    LaunchedEffect(videoId, reloadTick) {
        detailLoading = true
        detailError = null
        graph.repository.fetchVideo(videoId)
            .onSuccess {
                video = it
                history.record(it)
            }
            .onFailure { detailError = it.message ?: context.getString(R.string.error_video_load_failed) }
        detailLoading = false
    }

    // Resolve a playable source; `playbackAttempt` forces a refresh on retry.
    LaunchedEffect(video?.id, playbackAttempt) {
        val currentVideo = video ?: return@LaunchedEffect
        playbackState = PlaybackUiState.Loading
        graph.repository.fetchPlayback(currentVideo.id, quality, refresh = playbackAttempt > 0)
            .onSuccess { playbackState = PlaybackUiState.Ready(it) }
            .onFailure { playbackState = PlaybackUiState.Failed(it.message ?: context.getString(R.string.error_playback_resolve_failed)) }
    }

    // This screen owns the shared player while it is visible.
    DisposableEffect(Unit) {
        playerManager.setScreenActive(true)
        onDispose {
            playerManager.releaseScreen()
        }
    }

    // Bind the shared player to the resolved source (comments keep playing).
    LaunchedEffect(video?.id, playbackState) {
        val currentVideo = video ?: return@LaunchedEffect
        val ready = playbackState as? PlaybackUiState.Ready
        val sources = ready?.info?.sources
        if (sources.isNullOrEmpty()) {
            playerManager.pausePlayback()
            return@LaunchedEffect
        }
        val index = ((playbackAttempt % sources.size) + sources.size) % sources.size
        val source = sources[index]
        playerManager.play(currentVideo.id, source.url, source.mime)
    }

    fun showLoginPrompt(message: String) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.go_login),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onRequestLogin()
        }
    }

    fun applyLike(currentVideo: IwaraVideo, targetLiked: Boolean) {
        if (!isLoggedIn) {
            showLoginPrompt(context.getString(R.string.login_required_like))
            return
        }
        val original = currentVideo.liked == true
        val current = likedOverride ?: original
        if (current == targetLiked) return
        likedOverride = targetLiked
        scope.launch {
            graph.repository.setLiked(currentVideo.id, targetLiked).onFailure { error ->
                likedOverride = current
                snackbarHostState.showSnackbar(
                            iwarok.app.ui.util.localizedErrorMessage(context, error.message ?: context.getString(R.string.unknown_error)),
                        )
            }
        }
    }

    /** Whether the current user follows this video's author (optimistic override). */
    fun isFollowingFor(user: IwaraUser?): Boolean {
        val userId = user?.id?.takeIf { it.isNotBlank() } ?: return false
        return followOverride ?: (user.following == true)
    }

    /** Real follow / unfollow: optimistic update, rollback + snackbar on failure. */
    fun toggleFollow(currentVideo: IwaraVideo) {
        val user = currentVideo.user ?: return
        val userId = user.id.takeIf { it.isNotBlank() } ?: return
        if (!isLoggedIn) {
            showLoginPrompt(context.getString(R.string.login_required_follow))
            return
        }
        val original = user.following == true
        val current = followOverride ?: original
        val target = !current
        followOverride = target
        scope.launch {
            graph.repository.setFollowing(userId, target).onFailure { error ->
                followOverride = current
                snackbarHostState.showSnackbar(
                    error.message ?: context.getString(R.string.unknown_error),
                )
            }
        }
    }

    fun likeCountFor(currentVideo: IwaraVideo): Int {
        val original = currentVideo.liked == true
        val liked = likedOverride ?: original
        val delta = when {
            liked && !original -> 1
            !liked && original -> -1
            else -> 0
        }
        return (currentVideo.numLikes ?: 0) + delta
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // Video viewport; shrinks to the top when the comment panel opens.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(videoAreaFraction)
                .align(Alignment.TopCenter),
        ) {
            val currentVideo = video
            if (currentVideo != null) {
                VideoSurface(
                    playerManager = playerManager,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = surfaceScale.floatValue
                            scaleY = surfaceScale.floatValue
                            translationX = surfaceOffsetX.floatValue
                            translationY = surfaceOffsetY.floatValue
                            clip = true
                        },
                )
                val liked = likedOverride ?: (currentVideo.liked == true)
                val detailReadyInfo = playbackState as? PlaybackUiState.Ready
                val isVideoLandscape = playerManager.isLandscapeVideo ||
                    (currentVideo.file?.width ?: 0) > (currentVideo.file?.height ?: 0) ||
                    ((detailReadyInfo?.info?.width ?: 0) > (detailReadyInfo?.info?.height ?: 0))
                VideoPage(
                    video = currentVideo,
                    isCurrent = true,
                    playerManager = playerManager,
                    playbackState = playbackState,
                    liked = liked,
                    likeCount = likeCountFor(currentVideo),
                    isFavorited = isFavorited,
                    isFollowing = isFollowingFor(currentVideo.user),
                    onTapTogglePlay = { playerManager.togglePlayPause() },
                    onDoubleTapLike = { applyLike(currentVideo, true) },
                    onLikeClick = { applyLike(currentVideo, !liked) },
                    onCommentClick = {
                        // Fullscreen: leave it first, then show the comment panel.
                        if (fullscreen) fullscreenController.exit()
                        commentsVideo = currentVideo
                        commentsOpen = true
                    },
                    onShareClick = { shareVideo(context, currentVideo) },
                    onFavoriteClick = {
                        val added = favorites.toggle(currentVideo)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (added) R.string.favorites_added else R.string.favorites_removed,
                                ),
                            )
                        }
                    },
                    onFollowClick = { toggleFollow(currentVideo) },
                    onUserClick = {
                        currentVideo.user?.username
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onOpenProfile)
                    },
                    onTagClick = { tagId -> onOpenTag(tagId) },
                    onRetryPlayback = {
                        playbackAttempt += 1
                        playerManager.clearPlaybackError()
                        playerManager.retryCurrent()
                    },
                    qualityLabel = detailReadyInfo?.info?.primary?.label,
                    onQualityClick = null,
                    isLandscapeVideo = isVideoLandscape,
                    fullscreen = fullscreen,
                    onEnterFullscreen = { fullscreenController.enter() },
                    onExitFullscreen = { fullscreenController.exit() },
                    compact = commentsOpen,
                    onCleanModeChanged = { cleanMode = it },
                    onVideoTransformChanged = { scale, offset ->
                        surfaceScale.floatValue = scale
                        surfaceOffsetX.floatValue = offset.x
                        surfaceOffsetY.floatValue = offset.y
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val currentError = detailError
                when {
                    detailLoading -> {
                        LoadingState(
                            text = stringResource(R.string.loading),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    currentError != null -> {
                        ErrorState(
                            message = currentError,
                            title = stringResource(R.string.video_detail_load_failed),
                            onRetry = { reloadTick++ },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        ErrorState(
                            message = stringResource(R.string.video_detail_load_failed),
                            title = stringResource(R.string.video_detail_load_failed),
                            onRetry = { reloadTick++ },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Top scrim + back button (system back is handled by the navigator).
        // Both are hidden in landscape fullscreen (the exit-fullscreen control is
        // rendered by VideoPage there) and in clean-screen mode.
        if (!fullscreen && !cleanMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(TopScrimGradient),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 6.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Snackbars stay available in fullscreen too (e.g. a guest tapping the
        // follow badge gets the login prompt there as well).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        )

        val panelVideo = commentsVideo
        if (commentsOpen && panelVideo != null) {
            // Landscape: land the panel on the real FIT picture bottom (no gap);
            // portrait: keep the regular 44% / 56% split.
            val readyInfo = playbackState as? PlaybackUiState.Ready
            val videoW = playerManager.videoWidth.takeIf { it > 0 }
                ?: panelVideo.file?.width?.takeIf { it > 0 }
                ?: (readyInfo?.info?.width?.takeIf { it > 0 } ?: 0)
            val videoH = playerManager.videoHeight.takeIf { it > 0 }
                ?: panelVideo.file?.height?.takeIf { it > 0 }
                ?: (readyInfo?.info?.height?.takeIf { it > 0 } ?: 0)
            val videoAreaHeight = maxHeight * videoAreaFraction
            val panelTop = if (videoW > videoH && videoW > 0 && videoH > 0) {
                val scale = minOf(
                    maxWidth.value / videoW.toFloat(),
                    videoAreaHeight.value / videoH.toFloat(),
                )
                val displayHeight = (videoH.toFloat() * scale).dp
                val contentTop = (videoAreaHeight - displayHeight) * 0.5f
                (contentTop + displayHeight).coerceIn(0.dp, maxHeight)
            } else {
                videoAreaHeight
            }
            val panelHeight = (maxHeight - panelTop).coerceAtLeast(0.dp)
            CommentsPanel(
                graph = graph,
                video = panelVideo,
                isLoggedIn = isLoggedIn,
                onOpenVideo = { relatedId ->
                    commentsOpen = false
                    commentsVideo = null
                    onOpenVideo(relatedId)
                },
                onDismiss = {
                    commentsOpen = false
                    commentsVideo = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(panelHeight),
            )
        }
    }
}
