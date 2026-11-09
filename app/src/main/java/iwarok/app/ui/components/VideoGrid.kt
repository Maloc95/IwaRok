package iwarok.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import iwarok.app.R
import iwarok.app.core.IwaraVideo
import iwarok.app.core.formatDuration
import iwarok.app.core.videoThumbnailUrl
import iwarok.app.ui.feed.FeedStore
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.util.localizedCount
import iwarok.app.ui.util.localizedVideoTitle
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Two-column "TikTok style" video grid backed by a [FeedStore].
 *
 * Used by the search results, the user profile and the tag results screens.
 * Supports an optional full-width [header] (e.g. a profile banner), pagination
 * with automatic "load more" near the end, retry and empty states.
 */
@Composable
fun VideoGrid(
    store: FeedStore,
    onVideoClick: (IwaraVideo) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
    emptyTitle: String = stringResource(R.string.feed_empty_title),
    emptySubtitle: String? = null,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    LaunchedEffect(store) { store.loadFirst() }

    // Automatic pagination when the user approaches the last rows.
    LaunchedEffect(gridState, store.items.size, store.endReached, store.loadMoreError) {
        if (store.items.isEmpty()) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= store.items.size - 4) {
                    store.loadMore(scope)
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        when {
            store.isInitialLoading && store.items.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GridCardSkeletons()
                }
            }

            store.error != null && store.items.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorState(
                        message = store.error ?: stringResource(R.string.feed_load_failed),
                        title = stringResource(R.string.feed_load_failed),
                        onRetry = { scope.launch { store.loadFirst() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    )
                }
            }

            store.loadedOnce && store.items.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    )
                }
            }

            else -> {
                items(store.items, key = { it.id }) { video ->
                    VideoGridCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                    )
                }
                item(key = "grid-footer", span = { GridItemSpan(maxLineSpan) }) {
                    GridFooter(
                        store = store,
                        onLoadMore = { store.loadMore(scope) },
                        onRetry = { store.retryLoadMore(scope) },
                    )
                }
            }
        }
    }
}

/** A single video card: cover + duration badge, 2-line title, author, views. */
@Composable
fun VideoGridCard(
    video: IwaraVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlowSurface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF161618)),
        ) {
            VideoCoverImage(
                url = videoThumbnailUrl(video),
                contentDescription = localizedVideoTitle(video),
                modifier = Modifier.fillMaxSize(),
            )
            val duration = video.file?.duration ?: 0
            VideoStatusOverlay(video = video, modifier = Modifier.fillMaxSize())
            if (duration > 0) {
                Text(
                    text = formatDuration(duration),
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(
                text = localizedVideoTitle(video),
                color = FlowTextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = video.user?.displayName ?: "Iwara",
                    color = FlowTextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = FlowTextTertiary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = localizedCount(video.numViews),
                    color = FlowTextTertiary,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Cover image for grids. A null thumbnail URL, or an Iwara/broken image that
 * fails to load, falls back to a neutral dark card with a small play glyph.
 */
@Composable
fun VideoCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        CoverPlaceholder(modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { CoverPlaceholder(Modifier.fillMaxSize()) },
        error = { CoverPlaceholder(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF161618)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Covers a grid thumbnail with a status mask for private / unavailable videos.
 * The duration badge is drawn after this overlay so it stays readable.
 */
@Composable
fun VideoStatusOverlay(
    video: IwaraVideo,
    modifier: Modifier = Modifier,
) {
    val isPrivate = video.isPrivate
    // Private => Private; unlisted (playable) => Unlisted; other non-active => 不可用.
    val isUnlisted = !isPrivate && video.unlisted
    val isUnavailable = !isPrivate && !video.unlisted &&
        (video.status != null && !video.status.equals("active", ignoreCase = true))
    if (!isPrivate && !isUnlisted && !isUnavailable) return
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.56f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(
                    when {
                        isPrivate -> R.string.video_private_badge
                        isUnlisted -> R.string.video_unlisted_badge
                        else -> R.string.video_unavailable_badge
                    },
                ),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GridFooter(
    store: FeedStore,
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
            store.isLoadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = FlowPink,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.feed_loading_more),
                    color = FlowTextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            store.loadMoreError != null -> Text(
                text = stringResource(R.string.feed_load_more_failed_retry),
                color = FlowPink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            store.endReached && store.items.isNotEmpty() -> Text(
                text = stringResource(R.string.feed_end),
                color = FlowTextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )

            store.items.isNotEmpty() -> Text(
                text = stringResource(R.string.grid_load_more),
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

/** Skeleton placeholder shown while the first grid page loads. */
@Composable
private fun GridCardSkeletons() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) {
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(12.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(12.dp),
                        )
                    }
                }
            }
        }
    }
}
