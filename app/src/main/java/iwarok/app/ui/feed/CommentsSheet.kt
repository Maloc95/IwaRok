package iwarok.app.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.CommentsPage
import iwarok.app.core.IwaraComment
import iwarok.app.core.IwaraVideo
import iwarok.app.core.formatDuration
import iwarok.app.core.videoThumbnailUrl
import iwarok.app.core.videoTitle
import iwarok.app.ui.components.ErrorState
import iwarok.app.ui.components.LoadingState
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.components.VideoCoverImage
import iwarok.app.ui.components.VideoStatusOverlay
import iwarok.app.ui.util.localizedCount
import iwarok.app.ui.util.localizedRelativeDate
import iwarok.app.ui.util.localizedVideoTitle
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowSurfaceElevated
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Paginated state for the comment panel. */
class CommentsState(
    private val errorMessage: String,
    private val fetchPage: suspend (page: Int) -> Result<CommentsPage>,
) {
    var items by mutableStateOf<List<IwaraComment>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    var totalCount by mutableStateOf(0)
        private set

    private var nextPageIndex = 0
    private var generation = 0
    private var loadMoreJob: Job? = null

    suspend fun loadFirst() {
        val gen = ++generation
        loadMoreJob?.cancel()
        loadMoreJob = null
        isLoading = true
        error = null
        endReached = false
        nextPageIndex = 0
        items = emptyList()
        try {
            val result = fetchPage(0)
            if (gen != generation) return
            val page = result.getOrNull()
            if (page != null) {
                items = page.items
                totalCount = page.totalCount
                nextPageIndex = page.pageIndex + 1
                endReached = !page.hasMore
            } else {
                error = result.exceptionOrNull()?.message ?: errorMessage
            }
        } finally {
            if (gen == generation) {
                isLoading = false
                loadedOnce = true
            }
        }
    }

    fun loadMore(scope: CoroutineScope) {
        if (isLoading || isLoadingMore || endReached || loadMoreJob?.isActive == true) return
        if (error != null) return
        val gen = generation
        loadMoreJob = scope.launch {
            isLoadingMore = true
            try {
                val pageNumber = nextPageIndex
                val result = fetchPage(pageNumber)
                if (gen != generation) return@launch
                val page = result.getOrNull()
                if (page != null) {
                    val known = items.mapTo(HashSet()) { it.id }
                    items = items + page.items.filter { comment -> comment.id.isBlank() || known.add(comment.id) }
                    totalCount = page.totalCount
                    nextPageIndex = page.pageIndex + 1
                    endReached = !page.hasMore
                } else {
                    error = result.exceptionOrNull()?.message ?: errorMessage
                }
            } finally {
                if (gen == generation) isLoadingMore = false
            }
        }
    }

    fun retry(scope: CoroutineScope) {
        if (items.isEmpty()) {
            scope.launch { loadFirst() }
        } else {
            error = null
            loadMore(scope)
        }
    }
}

/** Lazily loaded inline replies for a single parent comment. */
class CommentRepliesState(
    private val errorMessage: String,
    private val fetchPage: suspend (page: Int) -> Result<CommentsPage>,
) {
    var expanded by mutableStateOf(false)
        private set
    var items by mutableStateOf<List<IwaraComment>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadedOnce by mutableStateOf(false)
        private set

    private var nextPageIndex = 0
    private var generation = 0
    private var loadMoreJob: Job? = null

    /** Expands (loading the first page on first use) or collapses the replies. */
    fun toggle(scope: CoroutineScope) {
        expanded = !expanded
        if (expanded && !loadedOnce && !isLoading) {
            scope.launch { loadFirst() }
        }
    }

    private suspend fun loadFirst() {
        val gen = ++generation
        loadMoreJob?.cancel()
        loadMoreJob = null
        isLoading = true
        error = null
        endReached = false
        nextPageIndex = 0
        items = emptyList()
        try {
            val result = fetchPage(0)
            if (gen != generation) return
            val page = result.getOrNull()
            if (page != null) {
                items = page.items
                nextPageIndex = page.pageIndex + 1
                endReached = !page.hasMore
            } else {
                error = result.exceptionOrNull()?.message ?: errorMessage
            }
        } finally {
            if (gen == generation) {
                isLoading = false
                loadedOnce = true
            }
        }
    }

    fun loadMore(scope: CoroutineScope) {
        if (isLoading || isLoadingMore || endReached || loadMoreJob?.isActive == true) return
        if (error != null) return
        val gen = generation
        loadMoreJob = scope.launch {
            isLoadingMore = true
            try {
                val pageNumber = nextPageIndex
                val result = fetchPage(pageNumber)
                if (gen != generation) return@launch
                val page = result.getOrNull()
                if (page != null) {
                    val known = items.mapTo(HashSet()) { it.id }
                    items = items + page.items.filter { reply -> reply.id.isBlank() || known.add(reply.id) }
                    nextPageIndex = page.pageIndex + 1
                    endReached = !page.hasMore
                } else {
                    error = result.exceptionOrNull()?.message ?: errorMessage
                }
            } finally {
                if (gen == generation) isLoadingMore = false
            }
        }
    }

    fun retry(scope: CoroutineScope) {
        if (items.isEmpty()) {
            scope.launch { loadFirst() }
        } else {
            error = null
            loadMore(scope)
        }
    }
}

/**
 * Half-screen comment panel shown in place over the bottom of the feed.
 *
 * The video keeps playing above it (the feed shrinks the video to the top part).
 * Guests see a read-only list — no login banner and no input bar at all.
 * Logged-in users get a Douyin-style bottom input bar backed by
 * `repository.postComment`.
 */
@Composable
fun CommentsPanel(
    graph: AppGraph,
    video: IwaraVideo,
    isLoggedIn: Boolean,
    onOpenVideo: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(video.id) {
        CommentsState(errorMessage = context.getString(R.string.error_comments_load_failed)) { page -> graph.repository.fetchCommentsNewest(video.id, page) }
    }
    LaunchedEffect(state) { state.loadFirst() }

    // "相似视频" tab: loaded once per video and kept while switching tabs.
    var relatedVideos by remember(video.id) { mutableStateOf<List<IwaraVideo>>(emptyList()) }
    var relatedLoaded by remember(video.id) { mutableStateOf(false) }
    var relatedFailed by remember(video.id) { mutableStateOf(false) }
    var headerTab by remember(video.id) { mutableStateOf(CommentsHeaderTab.COMMENTS) }
    LaunchedEffect(video.id) {
        graph.repository.fetchRelatedVideos(video.id)
            .onSuccess {
                relatedVideos = it
                relatedLoaded = true
            }
            .onFailure {
                relatedVideos = emptyList()
                relatedFailed = true
                relatedLoaded = true
            }
    }

    val listState = rememberLazyListState()
    // Always show the newest comments from the very top when the panel opens /
    // finishes loading, and whenever the user switches back from "相似视频".
    LaunchedEffect(video.id, state.loadedOnce) {
        if (state.loadedOnce && state.items.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(headerTab) {
        if (headerTab == CommentsHeaderTab.COMMENTS && state.items.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    val repliesStates = remember(video.id) { mutableStateMapOf<String, CommentRepliesState>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val dragOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 110.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
    }

    var commentInput by remember(video.id) { mutableStateOf("") }
    var sendingComment by remember(video.id) { mutableStateOf(false) }

    fun submitComment() {
        val text = commentInput.trim()
        if (text.isEmpty() || sendingComment) return
        sendingComment = true
        scope.launch {
            graph.repository.postComment(video.id, text)
                .onSuccess {
                    commentInput = ""
                    state.loadFirst()
                    if (state.items.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                }
                .onFailure { error ->
                    snackbarHostState.showSnackbar(
                        iwarok.app.ui.util.localizedErrorMessage(context, error.message ?: context.getString(R.string.comments_send_failed)),
                    )
                }
            sendingComment = false
        }
    }

    // System back closes the panel instead of leaving the feed.
    BackHandler(enabled = true) { onDismiss() }

    LaunchedEffect(listState, state) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - 3) {
                    state.loadMore(scope)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .offset { IntOffset(0, dragOffset.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(FlowSurface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle + header. Swipe down on this area to close.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            if (dragOffset.value > dismissThresholdPx) {
                                onDismiss()
                            } else {
                                dragOffset.animateTo(0f)
                            }
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f)),
                    )
                }
                // Compact header (44~52dp incl. the drag handle): no close button,
                // swipe-down / system back close the panel. Two tabs like Douyin:
                // comments / similar videos.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CommentsHeaderTabItem(
                        label = stringResource(R.string.comments_tab_intro),
                        selected = headerTab == CommentsHeaderTab.INTRO,
                        onClick = {
                            headerTab = CommentsHeaderTab.INTRO
                        },
                    )
                    CommentsHeaderTabItem(
                        label = if (state.totalCount > 0) {
                            stringResource(R.string.comments_title_count, localizedCount(state.totalCount))
                        } else {
                            stringResource(R.string.comments_title)
                        },
                        selected = headerTab == CommentsHeaderTab.COMMENTS,
                        onClick = {
                            headerTab = CommentsHeaderTab.COMMENTS
                        },
                    )
                    CommentsHeaderTabItem(
                        label = stringResource(R.string.comments_tab_related),
                        selected = headerTab == CommentsHeaderTab.RELATED,
                        onClick = {
                            headerTab = CommentsHeaderTab.RELATED
                        },
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = FlowOutline.copy(alpha = 0.45f))

            // Comfortable reading width: edge-to-edge + padding on phones,
            // centred column capped at 560dp on tablets / foldables.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (headerTab) {
                    CommentsHeaderTab.INTRO -> Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = video.body?.trim()?.takeIf { it.isNotEmpty() }
                                ?: stringResource(R.string.comments_intro_empty),
                            color = FlowTextPrimary,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                        )
                    }

                    CommentsHeaderTab.COMMENTS -> Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        when {
                        state.isLoading && state.items.isEmpty() -> {
                            LoadingState(
                                text = stringResource(R.string.comments_loading),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }

                        state.error != null && state.items.isEmpty() -> {
                            ErrorState(
                                message = state.error ?: "",
                                title = stringResource(R.string.comments_failed),
                                onRetry = { state.retry(scope) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }

                        state.loadedOnce && state.items.isEmpty() -> {
                            CommentEmptyState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                items(state.items, key = { it.id.ifBlank { it.hashCode().toString() } }) { comment ->
                                    val repliesState = repliesStates[comment.id]
                                    CommentRow(
                                        comment = comment,
                                        repliesState = repliesState,
                                        onToggleReplies = {
                                            val target = repliesStates.getOrPut(comment.id) {
                                                CommentRepliesState(errorMessage = context.getString(R.string.error_replies_load_failed)) { page ->
                                                    graph.repository.fetchCommentReplies(video.id, comment.id, page)
                                                }
                                            }
                                            target.toggle(scope)
                                        },
                                        onLoadMoreReplies = { repliesStates[comment.id]?.loadMore(scope) },
                                        onRetryReplies = { repliesStates[comment.id]?.retry(scope) },
                                    )
                                    HorizontalDivider(
                                        color = FlowOutline.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                    )
                                }
                                item(key = "footer") {
                                    CommentFooter(
                                        state = state,
                                        onLoadMore = { state.loadMore(scope) },
                                        onRetry = { state.retry(scope) },
                                    )
                                }
                            }
                        }
                        }
                    }

                    CommentsHeaderTab.RELATED -> RelatedVideosGrid(
                        videos = relatedVideos,
                        loaded = relatedLoaded,
                        failed = relatedFailed,
                        onOpenVideo = onOpenVideo,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    )
                }
            }

            // Douyin-style input bar is only shown to logged-in users and only
            // on the comments tab.
            if (isLoggedIn && headerTab == CommentsHeaderTab.COMMENTS) {
                HorizontalDivider(color = FlowOutline.copy(alpha = 0.5f))
                CommentInputBar(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    sending = sendingComment,
                    onSend = { submitComment() },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (isLoggedIn) 68.dp else 8.dp,
                ),
        )
    }
}

/** Round input field + gradient send button shown for logged-in users only. */
@Composable
private fun CommentInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = value.isNotBlank() && !sending
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.comments_input_hint),
                    color = FlowTextTertiary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = FlowTextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(FlowPink),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (canSend) {
                        Modifier.background(BrandGradient)
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.10f))
                    },
                )
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.comments_send),
                    tint = if (canSend) Color.White else FlowTextTertiary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/** 2-column "相似视频" grid shown on the related tab. */
@Composable
private fun RelatedVideosGrid(
    videos: List<IwaraVideo>,
    loaded: Boolean,
    failed: Boolean,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !loaded -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = FlowPink,
                    strokeWidth = 2.dp,
                )
            }
        }

        failed || videos.isEmpty() -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.comments_related_empty),
                    color = FlowTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = modifier,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(videos, key = { it.id }) { related ->
                    RelatedVideoGridCard(
                        video = related,
                        onClick = { onOpenVideo(related.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoGridCard(
    video: IwaraVideo,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(FlowSurfaceElevated)
                .border(1.dp, FlowOutline, RoundedCornerShape(10.dp)),
        ) {
            VideoCoverImage(
                url = videoThumbnailUrl(video),
                contentDescription = localizedVideoTitle(video),
                modifier = Modifier.fillMaxSize(),
            )
            VideoStatusOverlay(video = video, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = localizedCount(video.numViews ?: 0),
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDuration(video.file?.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = localizedVideoTitle(video),
            color = FlowTextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Header tab item ("评论 N" / "相似视频"). */
@Composable
private fun CommentsHeaderTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) FlowTextPrimary else FlowTextSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 18.dp else 0.dp)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(if (selected) FlowPink else Color.Transparent),
        )
    }
}

/** Comment panel header tab. */
private enum class CommentsHeaderTab { INTRO, COMMENTS, RELATED }

@Composable
private fun CommentEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.comments_empty),
            color = FlowTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CommentRow(
    comment: IwaraComment,
    repliesState: CommentRepliesState?,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onRetryReplies: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(user = comment.user, size = 34.dp, borderWidth = 1.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.displayName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.comment_unknown_user),
                    color = FlowTextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = localizedRelativeDate(comment.createdAt),
                    color = FlowTextTertiary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = comment.body?.trim().orEmpty(),
                color = FlowTextPrimary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            val replies = comment.numReplies ?: 0
            if (replies > 0) {
                val expanded = repliesState?.expanded == true
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (expanded) {
                        stringResource(R.string.comments_collapse_replies)
                    } else {
                        stringResource(R.string.comments_replies, localizedCount(replies))
                    },
                    color = if (expanded) FlowTextTertiary else FlowPink.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleReplies)
                        .padding(vertical = 2.dp),
                )
                if (expanded) {
                    repliesState?.let { replyState ->
                        ReplySection(
                            state = replyState,
                            totalReplies = replies,
                            onLoadMore = onLoadMoreReplies,
                            onRetry = onRetryReplies,
                        )
                    }
                }
            }
        }
    }
}

/** Inline replies for one parent comment: loading / retry / load-more / collapse. */
@Composable
private fun ReplySection(
    state: CommentRepliesState,
    totalReplies: Int,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = FlowPink,
                        strokeWidth = 1.8.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.comments_replies_loading),
                        color = FlowTextTertiary,
                        fontSize = 12.sp,
                    )
                }
            }

            state.error != null && state.items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.comments_replies_failed),
                    color = FlowPink,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(vertical = 6.dp),
                )
            }

            else -> {
                // Newest replies first (the API returns them chronologically).
                state.items.sortedByDescending { it.createdAt.orEmpty() }.forEach { reply ->
                    ReplyRow(reply)
                }
                when {
                    state.isLoadingMore -> {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = FlowPink,
                                strokeWidth = 1.8.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.comments_replies_loading),
                                color = FlowTextTertiary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    state.error != null -> {
                        Text(
                            text = stringResource(R.string.comments_replies_failed),
                            color = FlowPink,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetry)
                                .padding(vertical = 6.dp),
                        )
                    }

                    !state.endReached && state.items.size < totalReplies -> {
                        Text(
                            text = stringResource(R.string.comments_expand_more_replies),
                            color = FlowTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onLoadMore)
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(reply: IwaraComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(user = reply.user, size = 26.dp, borderWidth = 1.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user?.displayName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.comment_unknown_user),
                    color = FlowTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = localizedRelativeDate(reply.createdAt),
                    color = FlowTextTertiary,
                    fontSize = 10.5.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = reply.body?.trim().orEmpty(),
                color = FlowTextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun CommentFooter(
    state: CommentsState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = FlowPink,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.comments_loading_more),
                    color = FlowTextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.error != null -> Text(
                text = stringResource(R.string.comments_load_failed),
                color = FlowPink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            state.endReached && state.items.isNotEmpty() -> Text(
                text = stringResource(R.string.comments_end),
                color = FlowTextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )

            state.items.isNotEmpty() -> Text(
                text = stringResource(R.string.comments_load_more),
                color = FlowTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onLoadMore)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )

            else -> Unit
        }
    }
}
