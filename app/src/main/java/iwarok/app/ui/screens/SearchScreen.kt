package iwarok.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.AppSettings
import iwarok.app.core.IwaraUser
import iwarok.app.core.SearchVideoSort
import iwarok.app.core.IwaraUserPage
import iwarok.app.ui.components.BrandWordmark
import iwarok.app.ui.components.EmptyState
import iwarok.app.ui.components.ErrorState
import iwarok.app.ui.components.LoadingState
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.components.VideoGrid
import iwarok.app.ui.feed.FeedStore
import iwarok.app.ui.util.localizedCount
import iwarok.app.ui.util.localizedLabel
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Search result mode: the existing video grid or the user list. */
enum class SearchTab { VIDEO, USER }

/**
 * State holder for the search screen, remembered by the navigation shell so the
 * query, selected tab and results survive a round-trip to the video detail screen.
 */
class SearchScreenState {
    var query by mutableStateOf("")
    var activeQuery by mutableStateOf("")
    var tab by mutableStateOf(SearchTab.VIDEO)
    var videoSort by mutableStateOf(SearchVideoSort.RELEVANCE)
}

/** Paginated user-search results (`searchUsers`; guest search is supported). */
private class UserSearchState(
    private val errorMessage: String,
    private val fetchPage: suspend (page: Int) -> Result<IwaraUserPage>,
) {
    var items by mutableStateOf<List<IwaraUser>>(emptyList())
        private set
    var totalCount by mutableStateOf(0)
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
                items = page.results
                totalCount = page.count
                nextPageIndex = page.page + 1
                endReached = page.results.isEmpty() ||
                    page.results.size < page.limit ||
                    items.size >= page.count
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
                    items = items + page.results.filter { user -> known.add(user.id) }
                    totalCount = page.count
                    nextPageIndex = page.page + 1
                    endReached = page.results.isEmpty() ||
                        page.results.size < page.limit ||
                        items.size >= page.count
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
 * Search screen: a compact 42dp search field (guest search is supported) and a
 * two-column video grid for the results. Cards open the in-app video detail
 * screen.
 */
@Composable
fun SearchScreen(
    graph: AppGraph,
    settings: AppSettings,
    state: SearchScreenState,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val landing = state.activeQuery.isBlank()

    fun submitSearch() {
        val trimmed = state.query.trim()
        if (trimmed.isNotBlank()) {
            state.activeQuery = trimmed
            focusManager.clearFocus()
        }
    }

    // Both result stores are kept alive across tab switches for the same query.
    val videoStore = remember(state.activeQuery, state.videoSort) {
        FeedStore(defaultErrorMessage = context.getString(R.string.error_network_generic)) { page ->
            graph.repository.fetchSearch(state.activeQuery, state.videoSort, page)
        }
    }
    val userStore = remember(state.activeQuery, settings.contentSite) {
        UserSearchState(errorMessage = context.getString(R.string.error_user_search_failed)) { page ->
            graph.repository.searchUsers(state.activeQuery, page)
        }
    }

    LaunchedEffect(landing) {
        if (landing) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground),
    ) {
        SearchTopBar(
            query = state.query,
            onQueryChange = { newValue ->
                state.query = newValue
                // Clearing the field returns to the search landing state.
                if (newValue.isBlank()) state.activeQuery = ""
            },
            onSearch = { submitSearch() },
            onBack = onBack,
            focusRequester = if (landing) focusRequester else null,
        )

        // Videos / users switch; both modes keep the current query.
        SearchModeTabs(
            selected = state.tab,
            onSelect = { state.tab = it },
        )

        // Video-only sort chips (relevance/date/views/likes) below the tabs.
        if (!landing && state.tab == SearchTab.VIDEO) {
            SearchVideoSortChips(
                selected = state.videoSort,
                onSelect = { sort -> state.videoSort = sort },
            )
        }

        if (landing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = FlowPink,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                )
            }
        } else if (state.tab == SearchTab.VIDEO) {
            // Keyed by the store: changing the sort recreates the grid at the top.
            key(videoStore) {
                VideoGrid(
                    store = videoStore,
                    onVideoClick = { video -> onOpenVideo(video.id) },
                    modifier = Modifier.weight(1f),
                    header = {
                        SearchResultsHeader(
                            query = state.activeQuery,
                            totalCount = videoStore.totalCount,
                            loaded = videoStore.loadedOnce,
                        )
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = 24.dp,
                    ),
                    emptyTitle = stringResource(R.string.search_no_result),
                    emptySubtitle = stringResource(R.string.search_no_result_subtitle),
                )
            }
        } else {
            UserSearchResults(
                store = userStore,
                query = state.activeQuery,
                onUserClick = { user ->
                    user.username?.takeIf { it.isNotBlank() }?.let(onOpenProfile)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Video sort chips for search results: 相关 / 最新 / 播放 / 点赞. Only shown for
 * the video tab; the users tab has no sort.
 */
@Composable
private fun SearchVideoSortChips(
    selected: SearchVideoSort,
    onSelect: (SearchVideoSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchVideoSort.entries.forEach { sort ->
            val active = sort == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (active) FlowPink.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) FlowPink.copy(alpha = 0.55f) else FlowOutline,
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clickable { if (!active) onSelect(sort) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = sort.localizedLabel(),
                    color = if (active) FlowPink else FlowTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Segmented "视频 / 用户" switch under the search bar (query is preserved). */@Composable
private fun SearchModeTabs(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SearchTabItem(
            label = stringResource(R.string.search_tab_videos),
            selected = selected == SearchTab.VIDEO,
            onClick = { onSelect(SearchTab.VIDEO) },
        )
        SearchTabItem(
            label = stringResource(R.string.search_tab_users),
            selected = selected == SearchTab.USER,
            onClick = { onSelect(SearchTab.USER) },
        )
    }
}

@Composable
private fun SearchTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (selected) FlowTextPrimary else FlowTextTertiary,
        animationSpec = tween(durationMillis = 160),
        label = "search_tab_color",
    )
    val underlineWidth by animateDpAsState(
        targetValue = if (selected) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "search_tab_underline",
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
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(if (selected) FlowPink else Color.Transparent),
        )
    }
}

/** Paginated user-search result list with loading / error / empty / footer states. */
@Composable
private fun UserSearchResults(
    store: UserSearchState,
    query: String,
    onUserClick: (IwaraUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Loads once per query; switching tabs back and forth keeps the results.
    LaunchedEffect(store) { if (!store.loadedOnce) store.loadFirst() }
    LaunchedEffect(listState, store) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= store.items.size - 3) store.loadMore(scope)
            }
    }
    when {
        store.isLoading && store.items.isEmpty() -> {
            LoadingState(
                text = stringResource(R.string.search_users_loading),
                modifier = modifier,
            )
        }

        store.error != null && store.items.isEmpty() -> {
            ErrorState(
                message = store.error ?: "",
                title = stringResource(R.string.search_users_failed),
                onRetry = { store.retry(scope) },
                modifier = modifier,
            )
        }

        store.loadedOnce && store.items.isEmpty() -> {
            EmptyState(
                title = stringResource(R.string.search_users_empty_title),
                subtitle = stringResource(R.string.search_users_empty_subtitle),
                modifier = modifier,
            )
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
            ) {
                item(key = "header") {
                    SearchResultsHeader(
                        query = query,
                        totalCount = store.totalCount,
                        loaded = store.loadedOnce,
                        titleRes = R.string.search_users_results_title,
                        countRes = R.string.search_users_results_count,
                    )
                }
                items(
                    store.items,
                    key = { user -> user.id.ifBlank { user.username ?: user.hashCode().toString() } },
                ) { user ->
                    UserResultRow(user = user, onClick = { onUserClick(user) })
                }
                item(key = "footer") {
                    UserResultsFooter(
                        store = store,
                        onLoadMore = { store.loadMore(scope) },
                        onRetry = { store.retry(scope) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserResultRow(
    user: IwaraUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = user, size = 46.dp, borderWidth = 1.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.username?.takeIf { it.isNotBlank() }
                    ?: "",
                color = FlowTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!user.username.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@${user.username}",
                    color = FlowTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UserResultsFooter(
    store: UserSearchState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
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
                    text = stringResource(R.string.search_users_loading_more),
                    color = FlowTextTertiary,
                    fontSize = 12.sp,
                )
            }

            store.error != null -> Text(
                text = stringResource(R.string.search_users_retry),
                color = FlowPink,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            store.endReached && store.items.isNotEmpty() -> Text(
                text = stringResource(R.string.search_users_end),
                color = FlowTextTertiary,
                fontSize = 12.sp,
            )

            store.items.isNotEmpty() -> Text(
                text = stringResource(R.string.search_users_load_more),
                color = FlowTextSecondary,
                fontSize = 12.sp,
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

@Composable
private fun SearchResultsHeader(
    query: String,
    totalCount: Int,
    loaded: Boolean,
    titleRes: Int = R.string.search_results_title,
    countRes: Int = R.string.search_results_count,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes, query),
            color = FlowTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (loaded && totalCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(countRes, localizedCount(totalCount)),
                color = FlowTextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

/** Compact 42dp search bar: back button + one magnifier inside the field. */
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FlowBackground)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = FlowTextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.09f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = FlowTextTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = FlowTextTertiary,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = FlowTextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(FlowPink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = FlowTextTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
