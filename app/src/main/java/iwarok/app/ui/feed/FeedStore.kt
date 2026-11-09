package iwarok.app.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import iwarok.app.core.IwaraVideo
import iwarok.app.core.VideoPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Paged feed state holder shared by the home feed and the search results feed.
 *
 * The data source is injected as a suspend lambda so the same class can wrap
 * either [iwarok.app.core.IwaraRepository.fetchFeed] or
 * [iwarok.app.core.IwaraRepository.fetchSearch].
 */
class FeedStore(
    private val defaultErrorMessage: String,
    private val fetchPage: suspend (page: Int) -> Result<VideoPage>,
) {

    var items by mutableStateOf<List<IwaraVideo>>(emptyList())
        private set

    var isInitialLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    /** User-facing message for a failed first page load. */
    var error by mutableStateOf<String?>(null)
        private set

    /** User-facing message for a failed "load more" request. */
    var loadMoreError by mutableStateOf<String?>(null)
        private set

    var endReached by mutableStateOf(false)
        private set

    /** True once the first load attempt (success or failure) has finished. */
    var loadedOnce by mutableStateOf(false)
        private set

    /** Upstream total item count when known (0 when unknown). */
    var totalCount by mutableStateOf(0)
        private set

    private var nextPageIndex = 0
    private var generation = 0
    private val seenIds = LinkedHashSet<String>()
    private var loadMoreJob: Job? = null

    /** Loads (or reloads) the first page. Cancellation-safe. */
    suspend fun loadFirst() {
        val gen = ++generation
        loadMoreJob?.cancel()
        loadMoreJob = null
        isInitialLoading = true
        error = null
        loadMoreError = null
        endReached = false
        nextPageIndex = 0
        totalCount = 0
        seenIds.clear()
        items = emptyList()
        try {
            val result = fetchPage(0)
            if (gen != generation) return
            val page = result.getOrNull()
            if (page != null) {
                applyFirstPage(page)
            } else {
                error = result.exceptionOrNull()?.message ?: defaultErrorMessage
            }
        } finally {
            if (gen == generation) {
                isInitialLoading = false
                loadedOnce = true
            }
        }
    }

    /**
     * Pull-to-refresh: fetches page 0 again but keeps the current list visible
     * (no skeleton flash) until the fresh page arrives.
     */
    suspend fun refresh() {
        val gen = ++generation
        loadMoreJob?.cancel()
        loadMoreJob = null
        error = null
        loadMoreError = null
        try {
            val result = fetchPage(0)
            if (gen != generation) return
            val page = result.getOrNull()
            if (page != null) {
                applyFirstPage(page)
                loadedOnce = true
            } else {
                error = result.exceptionOrNull()?.message ?: defaultErrorMessage
            }
        } finally {
            if (gen == generation) isInitialLoading = false
        }
    }

    /** Appends the next page. No-op while another request is running. */
    fun loadMore(scope: CoroutineScope) {
        if (isInitialLoading || isLoadingMore || endReached) return
        if (loadMoreError != null) return
        if (loadMoreJob?.isActive == true) return
        val gen = generation
        loadMoreJob = scope.launch {
            isLoadingMore = true
            loadMoreError = null
            try {
                val pageNumber = nextPageIndex
                val result = fetchPage(pageNumber)
                if (gen != generation) return@launch
                val page = result.getOrNull()
                if (page != null) {
                    applyNextPage(page)
                } else {
                    loadMoreError = result.exceptionOrNull()?.message ?: defaultErrorMessage
                }
            } finally {
                if (gen == generation) isLoadingMore = false
            }
        }
    }

    /** Retries the failed "load more" request (used by the floating retry chip). */
    fun retryLoadMore(scope: CoroutineScope) {
        loadMoreError = null
        loadMore(scope)
    }

    private fun applyFirstPage(page: VideoPage) {
        seenIds.clear()
        val deduped = page.items.filter { it.id.isNotBlank() && seenIds.add(it.id) }
        items = deduped
        totalCount = page.totalCount
        nextPageIndex = page.pageIndex + 1
        endReached = !page.hasMore || deduped.isEmpty()
    }

    private fun applyNextPage(page: VideoPage) {
        val deduped = page.items.filter { it.id.isNotBlank() && seenIds.add(it.id) }
        items = items + deduped
        if (page.totalCount > totalCount) totalCount = page.totalCount
        nextPageIndex = page.pageIndex + 1
        endReached = !page.hasMore || deduped.isEmpty()
    }
}
