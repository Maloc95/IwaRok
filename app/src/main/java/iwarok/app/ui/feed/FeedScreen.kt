package iwarok.app.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.AppSettings
import iwarok.app.core.ContentSite
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraRepository
import iwarok.app.core.IwaraTag
import iwarok.app.core.IwaraVideo
import iwarok.app.core.IwaraUser
import iwarok.app.core.QualityPreference
import iwarok.app.core.RatingFilter
import iwarok.app.core.tagLabel
import iwarok.app.ui.components.EmptyState
import iwarok.app.ui.components.ErrorState
import iwarok.app.ui.components.FeedSkeleton
import iwarok.app.ui.components.FlowSecondaryButton
import iwarok.app.ui.components.FlowTextField
import iwarok.app.ui.player.PlayerManager
import iwarok.app.ui.util.HistoryStore
import iwarok.app.ui.util.localizedLabel
import iwarok.app.ui.util.LanguageStore
import iwarok.app.ui.util.LocaleHelper
import kotlinx.coroutines.flow.first
import iwarok.app.ui.player.VideoSurface
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.ChampagneGradient
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowSurfaceGlass
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.TopScrimGradient
import iwarok.app.ui.util.FavoritesStore
import iwarok.app.ui.util.TagCatalog
import iwarok.app.ui.util.TagChannel
import iwarok.app.ui.util.TagChannelsStore
import iwarok.app.ui.util.rememberFullscreenController
import iwarok.app.ui.util.shareVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sort tabs shown in the immersive feed header (推荐 first, the default). */
private val FeedTopTabs = listOf(
    FeedSort.RECOMMENDED,
    FeedSort.DATE,
    FeedSort.TRENDING,
    FeedSort.POPULARITY,
)

/** Fraction of the screen height used by the video while comments are open. */
private const val COMMENT_VIDEO_FRACTION = 0.44f

/**
 * Home screen: full-screen TikTok-style vertical video feed.
 *
 * Owns the paged [FeedStore] for the currently selected sort; the reusable
 * [VideoFeed] below handles the pager, player binding and interactions.
 */
/** Identity of a home feed paged store; changing it rebuilds the feed. */
data class HomeFeedStoreKey(
    val sort: FeedSort,
    val rating: RatingFilter,
    val tagId: String?,
    val contentSite: ContentSite,
)

/**
 * Home-feed state hoisted to MainShell: survives navigation to Settings / Search /
 * Profile / VideoDetail / Favorites / Login, so returning restores the same paged
 * store, sort/tag selection and pager page instead of reloading the first page.
 * Plain fields on purpose: writes happen during composition without recompose loops.
 */
class HomeFeedState {
    var selectedSort: FeedSort? = null
    var selectedTagId: String? = null
    var store: FeedStore? = null
    var storeKey: HomeFeedStoreKey? = null
    var currentPageIndex: Int = 0
    var currentVideoId: String? = null

    /** Reuses the store while [key] is unchanged, otherwise builds a fresh one. */
    fun storeFor(key: HomeFeedStoreKey, factory: () -> FeedStore): FeedStore {
        val current = store
        if (current != null && storeKey == key) return current
        val fresh = factory()
        store = fresh
        storeKey = key
        currentPageIndex = 0
        currentVideoId = null
        return fresh
    }
}

@Composable
fun HomeFeedScreen(
    graph: AppGraph,
    settings: AppSettings,
    playerManager: PlayerManager,
    favorites: FavoritesStore,
    tagChannels: TagChannelsStore,
    history: HistoryStore,
    feedState: HomeFeedState,
    isLoggedIn: Boolean,
    currentUser: IwaraUser?,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onRequestLogin: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val channels by tagChannels.channels.collectAsState()
    // Restored from the hoisted state when coming back from another screen.
    var selectedSort by remember { mutableStateOf(feedState.selectedSort ?: settings.sort) }
    var selectedTagId by remember { mutableStateOf(feedState.selectedTagId) }
    var drawerOpen by remember { mutableStateOf(false) }
    var manageChannelsOpen by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSort, selectedTagId) {
        feedState.selectedSort = selectedSort
        feedState.selectedTagId = selectedTagId
    }

    LaunchedEffect(settings.sort) {
        if (selectedSort != settings.sort) selectedSort = settings.sort
    }

    // Removing (or resetting) the currently selected channel falls back to 全部.
    LaunchedEffect(channels) {
        val current = selectedTagId
        if (current != null && channels.none { it.id == current }) {
            selectedTagId = null
        }
    }

    // A new channel / sort / rating / content site recreates the store: fresh
    // first page and the pager returns to the top. Quality changes never do.
    val store = feedState.storeFor(
        HomeFeedStoreKey(
            sort = selectedSort,
            rating = settings.rating,
            tagId = selectedTagId,
            contentSite = settings.contentSite,
        ),
    ) {
        val tagId = selectedTagId
        FeedStore(defaultErrorMessage = context.getString(R.string.error_network_generic)) { page ->
            if (tagId == null) {
                graph.repository.fetchFeed(selectedSort, settings.rating, page)
            } else {
                graph.repository.fetchTagVideos(tagId, selectedSort, page, settings.rating)
            }
        }
    }

    // System back closes the navigation drawer first.
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoFeed(
            graph = graph,
            store = store,
            playerManager = playerManager,
            favorites = favorites,
            history = history,
            quality = settings.quality,
            isLoggedIn = isLoggedIn,
            onRequestLogin = onRequestLogin,
            initialPage = feedState.currentVideoId
                ?.let { id -> store.items.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: feedState.currentPageIndex,
            onPageChanged = { index ->
                feedState.currentPageIndex = index
                feedState.currentVideoId = store.items.getOrNull(index)?.id
            },
            onQualityClick = onOpenSettings,
            onOpenProfile = onOpenProfile,
            onOpenTag = onOpenTag,
            onOpenVideo = onOpenVideo,
            modifier = Modifier.fillMaxSize(),
            emptyTitle = if (selectedTagId != null) {
                stringResource(R.string.feed_tag_empty_title)
            } else {
                null
            },
            topOverlay = {
                HomeTopBar(
                    selectedSort = selectedSort,
                    onSortSelected = { sort ->
                        if (sort != selectedSort) {
                            selectedSort = sort
                            scope.launch { graph.repository.setSort(sort) }
                        }
                    },
                    contentSite = settings.contentSite,
                    onToggleContentSite = {
                        val target = if (settings.contentSite == ContentSite.AI) {
                            ContentSite.MAIN
                        } else {
                            ContentSite.AI
                        }
                        drawerOpen = false
                        scope.launch { graph.repository.setContentSite(target) }
                    },
                    channels = channels,
                    selectedTagId = selectedTagId,
                    onTagSelected = { tagId ->
                        if (tagId != selectedTagId) {
                            selectedTagId = tagId
                            drawerOpen = false
                        }
                    },
                    onManageChannels = { manageChannelsOpen = true },
                    onOpenSearch = onOpenSearch,
                    onOpenDrawer = { drawerOpen = true },
                )
            },
        )

        // Left drawer: tap-outside scrim + sliding panel (account / favorites / settings).
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .pointerInput(Unit) {
                        detectTapGestures { drawerOpen = false }
                    },
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(220)) +
                fadeIn(tween(140)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(180)) +
                fadeOut(tween(120)),
        ) {
            FeedDrawerPanel(
                user = currentUser,
                onClose = { drawerOpen = false },
                onOpenFavorites = onOpenFavorites,
                onOpenHistory = onOpenHistory,
                onOpenLanguage = { languageDialogOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenLogin = onRequestLogin,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(286.dp),
            )
        }

        // Add / remove tag channels (persisted by [TagChannelsStore]).
        if (manageChannelsOpen) {
            TagChannelManagerDialog(
                store = tagChannels,
                channels = channels,
                repository = graph.repository,
                onDismiss = { manageChannelsOpen = false },
            )
        }

        // Language picker: applies on next Activity recreation.
        if (languageDialogOpen) {
            AppLanguageDialog(
                current = LanguageStore.load(context),
                onSelect = { language ->
                    languageDialogOpen = false
                    LocaleHelper.change(context, language)
                },
                onDismiss = { languageDialogOpen = false },
            )
        }
    }
}

/**
 * Reusable immersive video feed.
 *
 * One [PlayerManager] is shared by all pages; only the page that owns
 * [androidx.compose.foundation.pager.PagerState.currentPage] binds playback.
 * Playback URLs for the current + next few pages are prefetched.
 *
 * Comments are shown in an in-place half-screen panel (no modal scrim): the
 * video area shrinks to the top ~44% of the screen, keeps playing and switches
 * to FIT so the whole frame stays visible.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoFeed(
    graph: AppGraph,
    store: FeedStore,
    playerManager: PlayerManager,
    favorites: FavoritesStore,
    history: HistoryStore,
    quality: QualityPreference,
    isLoggedIn: Boolean,
    onRequestLogin: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
    topOverlay: @Composable BoxScope.() -> Unit = {},
    onQualityClick: (() -> Unit)? = null,
    onOpenProfile: (String) -> Unit = {},
    onOpenTag: (String) -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resolver = remember(store) {
        PlaybackResolver(scope, context.getString(R.string.error_playback_resolve_failed)) { videoId, selectedQuality, refresh ->
            graph.repository.fetchPlayback(videoId, selectedQuality, refresh)
        }
    }
    LaunchedEffect(quality) { resolver.invalidateAll() }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceAtLeast(0),
        pageCount = { store.items.size },
    )
    // Keep the hoisted state in sync so returning here restores the same page.
    LaunchedEffect(pagerState.currentPage, store.items.size) {
        if (pagerState.currentPage in store.items.indices) {
            onPageChanged(pagerState.currentPage)
        }
    }
    // Record the video actually being watched; dedupe / move-to-front in store.
    LaunchedEffect(pagerState.currentPage, store.items.size) {
        store.items.getOrNull(pagerState.currentPage)?.let {
            history.record(it)
            graph.repository.recordInterest(it)
        }
    }
    var pullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Reuse the persisted store after navigation: only a fresh store loads page 0.
    LaunchedEffect(store) {
        if (!store.loadedOnce && !store.isInitialLoading) {
            store.loadFirst()
        }
        if (store.items.isEmpty()) {
            snapshotFlow { store.items.isNotEmpty() }.first { it }
        }
        if (store.items.isNotEmpty()) {
            pagerState.scrollToPage(initialPage.coerceIn(0, store.items.lastIndex))
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val likeOverrides = remember { mutableStateMapOf<String, Boolean>() }
    val followOverrides = remember { mutableStateMapOf<String, Boolean>() }
    val sourceAttempts = remember { mutableStateMapOf<String, Int>() }

    var commentsVideo by remember { mutableStateOf<IwaraVideo?>(null) }
    // Two-finger "clean screen" mode hides this page's overlays + the feed header.
    var cleanMode by remember { mutableStateOf(false) }
    // Long-press action menu open -> freeze the vertical pager underneath.
    var gestureMenuOpen by remember { mutableStateOf(false) }
    // Current page clean-screen / zoom state -> the pager must not steal drags.
    var videoZoomed by remember { mutableStateOf(false) }
    // Shared player surface transform (pinch zoom / pan), reported by the
    // current page. Read only inside graphicsLayer so redraws stay cheap.
    val surfaceScale = remember { mutableFloatStateOf(1f) }
    val surfaceOffsetX = remember { mutableFloatStateOf(0f) }
    val surfaceOffsetY = remember { mutableFloatStateOf(0f) }
    val commentsOpen = commentsVideo != null

    // Landscape fullscreen mode (rotates the activity + hides system bars) and
    // the local favorites state shared with the detail screen.
    val fullscreenController = rememberFullscreenController()
    val fullscreen = fullscreenController.isFullscreen
    val favoritesState by favorites.items.collectAsState()
    val favoriteIds = remember(favoritesState) { favoritesState.mapTo(HashSet()) { it.id } }
    BackHandler(enabled = fullscreen) { fullscreenController.exit() }

    // Switching sort / source recreates the store: dismiss the panel with it.
    LaunchedEffect(store) {
        commentsVideo = null
        gestureMenuOpen = false
    }

    // The player belongs to this screen while it is composed.
    DisposableEffect(Unit) {
        playerManager.setScreenActive(true)
        onDispose {
            playerManager.releaseScreen()
        }
    }

    val currentPage = pagerState.currentPage
    // Zoom/pan belongs to the current page only: reset whenever the page changes.
    LaunchedEffect(currentPage) {
        surfaceScale.floatValue = 1f
        surfaceOffsetX.floatValue = 0f
        surfaceOffsetY.floatValue = 0f
        videoZoomed = false
    }
    val currentVideo = store.items.getOrNull(currentPage)
    val currentPlaybackState = currentVideo?.let { resolver.stateOf(it.id) }
    val currentAttempt = currentVideo?.let { sourceAttempts[it.id] ?: 0 } ?: 0

    // Video viewport height: full screen normally, top 44% while comments are open.
    val videoAreaFraction by animateFloatAsState(
        targetValue = if (commentsOpen) COMMENT_VIDEO_FRACTION else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "video_area_fraction",
    )

    // Keep the pager inside bounds whenever the list resets or grows.
    LaunchedEffect(store.items.size) {
        if (store.items.isEmpty()) {
            if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
        } else if (pagerState.currentPage > store.items.lastIndex) {
            pagerState.scrollToPage(store.items.lastIndex)
        }
    }

    // Prefetch the current page first (it is what the user is waiting for), then
    // warm up the neighbouring pages without competing for the same bandwidth.
    LaunchedEffect(currentPage, store.items, quality) {
        if (store.items.isEmpty()) return@LaunchedEffect
        val current = store.items.getOrNull(currentPage) ?: return@LaunchedEffect
        resolver.prefetch(current.id, quality)
        kotlinx.coroutines.delay(350)
        store.items.getOrNull(currentPage + 1)?.let { resolver.prefetch(it.id, quality) }
        kotlinx.coroutines.delay(200)
        store.items.getOrNull(currentPage + 2)?.let { resolver.prefetch(it.id, quality) }
        store.items.getOrNull(currentPage - 1)?.let { resolver.prefetch(it.id, quality) }
    }

    // Bind the shared player to the current page.
    LaunchedEffect(currentVideo?.id, currentPlaybackState, quality, currentAttempt) {
        val video = currentVideo
        if (video == null) {
            playerManager.detach()
            return@LaunchedEffect
        }
        val ready = currentPlaybackState as? PlaybackUiState.Ready
        if (ready == null) {
            playerManager.pausePlayback()
            return@LaunchedEffect
        }
        val sources = ready.info.sources
        if (sources.isEmpty()) {
            playerManager.pausePlayback()
            return@LaunchedEffect
        }
        val index = ((currentAttempt % sources.size) + sources.size) % sources.size
        val source = sources[index]
        playerManager.play(video.id, source.url, source.mime)
    }

    // Automatic recovery: hls/MP4 signed URLs are short-lived (~6 min) and
    // prefetched pages can go stale. Re-resolve the manifest and rebind once or
    // twice before showing the error card to the user.
    LaunchedEffect(currentVideo?.id, playerManager.playbackError, currentPlaybackState) {
        val video = currentVideo ?: return@LaunchedEffect
        if (playerManager.playbackError == null) return@LaunchedEffect
        if (currentPlaybackState !is PlaybackUiState.Ready) return@LaunchedEffect
        val attempts = sourceAttempts[video.id] ?: 0
        if (attempts >= 2) return@LaunchedEffect
        sourceAttempts[video.id] = attempts + 1
        playerManager.clearPlaybackError()
        resolver.retry(video.id, quality)
    }

    // Automatic "load more" when approaching the end of the list.
    LaunchedEffect(currentPage, store.items.size, store.endReached, store.loadMoreError) {
        if (store.items.isEmpty() || store.endReached) return@LaunchedEffect
        if (currentPage >= store.items.size - 3) {
            store.loadMore(scope)
        }
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

    fun applyLike(video: IwaraVideo, targetLiked: Boolean) {
        if (!isLoggedIn) {
            showLoginPrompt(context.getString(R.string.login_required_like))
            return
        }
        val originalLiked = video.liked == true
        val currentLiked = likeOverrides[video.id] ?: originalLiked
        if (currentLiked == targetLiked) return
        likeOverrides[video.id] = targetLiked
        scope.launch {
            graph.repository.setLiked(video.id, targetLiked).onFailure { error ->
                likeOverrides[video.id] = currentLiked
                snackbarHostState.showSnackbar(
                        iwarok.app.ui.util.localizedErrorMessage(context, error.message ?: context.getString(R.string.unknown_error)),
                    )
            }
        }
    }

    fun likeCountFor(video: IwaraVideo): Int {
        val original = video.liked == true
        val liked = likeOverrides[video.id] ?: original
        val delta = when {
            liked && !original -> 1
            !liked && original -> -1
            else -> 0
        }
        return (video.numLikes ?: 0) + delta
    }

    /** Whether the current user follows this video's author (optimistic override). */
    fun isFollowingUser(user: IwaraUser?): Boolean {
        val userId = user?.id?.takeIf { it.isNotBlank() } ?: return false
        return followOverrides[userId] ?: (user.following == true)
    }

    /** Real follow / unfollow: optimistic update, rollback + snackbar on failure. */
    fun toggleFollow(video: IwaraVideo) {
        val user = video.user ?: return
        val userId = user.id.takeIf { it.isNotBlank() } ?: return
        if (!isLoggedIn) {
            showLoginPrompt(context.getString(R.string.login_required_follow))
            return
        }
        val original = user.following == true
        val current = followOverrides[userId] ?: original
        val target = !current
        followOverrides[userId] = target
        scope.launch {
            graph.repository.setFollowing(userId, target).onFailure { error ->
                followOverrides[userId] = current
                snackbarHostState.showSnackbar(
                    iwarok.app.ui.util.localizedErrorMessage(
                        context,
                        error.message ?: context.getString(R.string.unknown_error),
                    ),
                )
            }
        }
    }

    fun openComments(video: IwaraVideo) {
        // 评论面板不再暂停播放：上半屏视频继续播放（保持当前播放状态）。
        commentsVideo = video
    }

    fun closeComments() {
        commentsVideo = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // Video viewport. It shrinks to the top of the screen while the comment
        // panel is open. The player surface is always RESIZE_MODE_FIT so the whole
        // video frame stays visible (letterbox bars are acceptable).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(videoAreaFraction)
                .align(Alignment.TopCenter),
        ) {
            // Single shared player surface, kept behind the pager content. One
            // PlayerView for the whole feed avoids SurfaceView ownership races when
            // pages are recycled while swiping. Always RESIZE_MODE_FIT now.
            // The shared surface also follows the pager drag vertically so the
            // video picture moves together with the page UI.
            val surfaceParallaxEnabled = !commentsOpen && !fullscreen && !cleanMode
            // Portrait feed: lift the picture slightly so the top half is less
            // empty and the bottom info block feels balanced.
            val surfaceLiftDp = 26.dp
            val surfaceLiftPx = if (surfaceParallaxEnabled) {
                with(LocalDensity.current) { surfaceLiftDp.toPx() }
            } else {
                0f
            }
            if (store.items.isNotEmpty() && currentVideo != null) {
                VideoSurface(
                    playerManager = playerManager,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = surfaceScale.floatValue
                            scaleY = surfaceScale.floatValue
                            translationX = surfaceOffsetX.floatValue
                            translationY = surfaceOffsetY.floatValue - surfaceLiftPx +
                                if (surfaceParallaxEnabled) {
                                    -pagerState.currentPageOffsetFraction * size.height.toFloat()
                                } else {
                                    0f
                                }
                            clip = true
                        },
                )
            }

            when {
                store.items.isNotEmpty() -> {
                    // Pull-to-refresh only makes sense on the first page, outside
                    // comments / fullscreen / clean mode; the indicator is hidden
                    // in those states and the pager keeps consuming its own drags.
                    val canPullRefresh = !commentsOpen && !fullscreen && !cleanMode &&
                        !gestureMenuOpen && !videoZoomed
                    PullToRefreshBox(
                        isRefreshing = pullRefreshing,
                        onRefresh = {
                            if (canPullRefresh && pagerState.currentPage == 0 && !pullRefreshing) {
                                scope.launch {
                                    pullRefreshing = true
                                    try {
                                        store.refresh()
                                    } finally {
                                        pullRefreshing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = pullRefreshState,
                        indicator = {
                            if (canPullRefresh) {
                                PullToRefreshDefaults.Indicator(
                                    state = pullRefreshState,
                                    isRefreshing = pullRefreshing,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                        },
                    ) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !commentsOpen && !fullscreen && !gestureMenuOpen &&
                            !cleanMode && !videoZoomed,
                        key = { index -> store.items.getOrNull(index)?.id ?: "feed-page-$index" },
                    ) { pageIndex ->
                        val video = store.items.getOrNull(pageIndex)
                        if (video != null) {
                            val isCurrent = pageIndex == pagerState.currentPage
                            val liked = likeOverrides[video.id] ?: (video.liked == true)
                            val readyInfo = (resolver.stateOf(video.id) as? PlaybackUiState.Ready)?.info
                            // Landscape videos get the "全屏" button / fullscreen mode.
                            val isLandscape = (readyInfo?.width ?: 0) > (readyInfo?.height ?: 0) ||
                                (video.file?.width ?: 0) > (video.file?.height ?: 0) ||
                                (isCurrent && playerManager.isLandscapeVideo)
                            VideoPage(
                                video = video,
                                isCurrent = isCurrent,
                                playerManager = playerManager,
                                playbackState = resolver.stateOf(video.id),
                                liked = liked,
                                likeCount = likeCountFor(video),
                                isFavorited = video.id in favoriteIds,
                                isFollowing = isFollowingUser(video.user),
                                onTapTogglePlay = { playerManager.togglePlayPause() },
                                onDoubleTapLike = { applyLike(video, true) },
                                onLikeClick = { applyLike(video, !liked) },
                                onCommentClick = {
                                    // Fullscreen has the comment action too: leave fullscreen
                                    // first, then open the half-screen comment panel.
                                    if (fullscreen) {
                                        fullscreenController.exit()
                                    }
                                    openComments(video)
                                },
                                onShareClick = { shareVideo(context, video) },
                                onFavoriteClick = {
                                    val added = favorites.toggle(video)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                if (added) R.string.favorites_added else R.string.favorites_removed,
                                            ),
                                        )
                                    }
                                },
                                onFollowClick = { toggleFollow(video) },
                                onUserClick = {
                                    video.user?.username
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(onOpenProfile)
                                },
                                onTagClick = { tagId -> onOpenTag(tagId) },
                                onRetryPlayback = {
                                    sourceAttempts[video.id] = (sourceAttempts[video.id] ?: 0) + 1
                                    playerManager.clearPlaybackError()
                                    playerManager.retryCurrent()
                                    resolver.retry(video.id, quality)
                                },
                                qualityLabel = readyInfo?.sources?.let { sources ->
        if (sources.isEmpty()) {
            readyInfo?.primary?.label
        } else {
            sources[((currentAttempt % sources.size) + sources.size) % sources.size].label
        }
    },
                                onQualityClick = onQualityClick,
                                isLandscapeVideo = isLandscape,
                                fullscreen = fullscreen,
                                onEnterFullscreen = if (isCurrent && isLandscape) {
                                    { fullscreenController.enter() }
                                } else {
                                    null
                                },
                                onExitFullscreen = { fullscreenController.exit() },
                                compact = commentsOpen,
                                contentLift = surfaceLiftDp,
                                onCleanModeChanged = { enabled ->
                                    if (isCurrent) cleanMode = enabled
                                },
                                onGestureMenuVisibilityChanged = { open ->
                                    if (isCurrent) gestureMenuOpen = open
                                },
                                onVideoTransformChanged = { scale, offset ->
                                    if (isCurrent) {
                                        surfaceScale.floatValue = scale
                                        surfaceOffsetX.floatValue = offset.x
                                        surfaceOffsetY.floatValue = offset.y
                                        videoZoomed = scale > 1.001f
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    }
                }

                store.isInitialLoading || !store.loadedOnce -> {
                    FeedSkeleton(modifier = Modifier.fillMaxSize())
                }

                store.error != null -> {
                    ErrorState(
                        message = store.error ?: stringResource(R.string.feed_load_failed),
                        title = stringResource(R.string.feed_load_failed),
                        onRetry = { scope.launch { store.loadFirst() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    EmptyState(
                        title = emptyTitle ?: stringResource(R.string.feed_empty_title),
                        subtitle = emptySubtitle ?: stringResource(R.string.feed_empty_subtitle),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            FlowSecondaryButton(
                                text = stringResource(R.string.refresh),
                                onClick = { scope.launch { store.loadFirst() } },
                                modifier = Modifier.fillMaxWidth(0.6f),
                            )
                        },
                    )
                }
            }
        }

        // Screen-specific header (centered tabs / search bar); hidden in
        // fullscreen, while the comment panel is open and in clean-screen mode.
        if (!fullscreen && !commentsOpen && !cleanMode) {
            topOverlay()
        }

        // Load-more / end-of-feed status chips (hidden while comments / fullscreen).
        if (!commentsOpen && !fullscreen && !cleanMode && store.items.isNotEmpty()) {
            val showLoadMoreError = store.loadMoreError != null
            val showLoadingMore = store.isLoadingMore
            val showEnd = store.endReached && store.items.isNotEmpty() &&
                currentPage >= store.items.lastIndex - 1
            if (showLoadMoreError || showLoadingMore || showEnd) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(FlowSurfaceGlass)
                            .clickable(enabled = showLoadMoreError) {
                                store.retryLoadMore(scope)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showLoadMoreError) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = FlowPink,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.feed_load_more_failed_retry),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (showLoadingMore) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.6.dp,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = stringResource(R.string.feed_loading_more),
                                color = FlowTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.feed_end),
                                color = FlowTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
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

        // Half-screen comment panel: no modal scrim, the video above keeps playing.
        // For landscape videos the panel top is placed exactly on the FIT picture
        // bottom inside the 0.44H video area (no dark letterbox gap); portrait
        // videos keep the regular 44% / 56% split.
        val panelVideo = commentsVideo
        if (panelVideo != null) {
            val readyInfo = currentPlaybackState as? PlaybackUiState.Ready
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
                    closeComments()
                    onOpenVideo(relatedId)
                },
                onDismiss = { closeComments() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(panelHeight),
            )
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Home feed header: centered TikTok-style tabs with search / settings on the
 * right.
 * ------------------------------------------------------------------------- */

@Composable
private fun HomeTopBar(
    selectedSort: FeedSort,
    onSortSelected: (FeedSort) -> Unit,
    contentSite: ContentSite,
    onToggleContentSite: () -> Unit,
    channels: List<TagChannel>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    onManageChannels: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TopScrimGradient)
            .statusBarsPadding(),
    ) {
        val centeredTabs = LocalConfiguration.current.locales[0].language.let {
            it == "zh" || it == "ko"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hamburger -> left drawer (account / favorites / settings).
                TopBarIconButton(
                    icon = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    onClick = onOpenDrawer,
                )
                Spacer(Modifier.width(2.dp))
                // Iwara main site <-> Iwara AI site toggle (highlighted on AI).
                ContentSiteButton(
                    isAi = contentSite == ContentSite.AI,
                    onClick = onToggleContentSite,
                )
                if (centeredTabs) {
                    // Chinese/Korean keep the original centred tab bar.
                    Spacer(Modifier.weight(1f))
                    TopBarIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.action_search),
                        onClick = onOpenSearch,
                    )
                } else {
                    Spacer(Modifier.width(8.dp))
                    // Long English/Japanese labels sit in the flow after the AI
                    // button and scroll in the remaining space.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FeedTopTabs.forEach { sort ->
                            FeedTab(
                                label = sort.localizedLabel(),
                                selected = selectedSort == sort,
                                onClick = { onSortSelected(sort) },
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    TopBarIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.action_search),
                        onClick = onOpenSearch,
                    )
                }
            }
            if (centeredTabs) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeedTopTabs.forEach { sort ->
                        FeedTab(
                            label = sort.localizedLabel(),
                            selected = selectedSort == sort,
                            onClick = { onSortSelected(sort) },
                        )
                    }
                }
            }
        }
        // Douyin-style horizontally scrolling channel tabs (compact, 13.5sp).
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "__all__") {
                FeedTagTabItem(
                    label = stringResource(R.string.feed_tag_all),
                    selected = selectedTagId == null,
                    onClick = { onTagSelected(null) },
                )
            }
            items(channels, key = { it.id }) { channel ->
                FeedTagTabItem(
                    label = channel.localizedLabel(),
                    selected = selectedTagId == channel.id,
                    onClick = { onTagSelected(channel.id) },
                )
            }
            item(key = "__manage_channels__") {
                ChannelManageButton(onClick = onManageChannels)
            }
        }
    }
}

@Composable
private fun FeedTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.60f),
        animationSpec = tween(durationMillis = 160),
        label = "feed_tab_color",
    )
    val underlineWidth by animateDpAsState(
        targetValue = if (selected) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "feed_tab_underline",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

/** Compact channel tab (Douyin style): white + short underline when selected. */
@Composable
private fun FeedTagTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
        animationSpec = tween(durationMillis = 160),
        label = "feed_tag_color",
    )
    val underlineWidth by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "feed_tag_underline",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(2.5.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

/** Iwara main site <-> Iwara AI site toggle, highlighted while AI is active. */
@Composable
private fun ContentSiteButton(
    isAi: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(percent = 50))
            .then(
                if (isAi) {
                    Modifier.background(ChampagneGradient)
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.06f))
                },
            )
            .border(
                width = 1.dp,
                color = if (isAi) {
                    Color.Transparent
                } else {
                    FlowOutline
                },
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AI",
            color = if (isAi) Color(0xFF17120A) else FlowTextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Round "+" entry at the end of the channel row; opens the manager dialog. */
@Composable
private fun ChannelManageButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.tag_channels_manage),
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Plain white icon with a cheap drop shadow (TikTok style, no circle background). */
@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier
                .size(21.dp)
                .offset(x = 1.dp, y = 1.dp),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}
