package iwarok.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import iwarok.app.R
import iwarok.app.core.VideoPage
import iwarok.app.ui.components.ScreenTopBar
import iwarok.app.ui.components.VideoGrid
import iwarok.app.ui.feed.FeedStore
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.util.HistoryStore

/**
 * Locally recorded watch history, rendered with the same two-column grid as
 * search / profile / favorites. Cards open the in-app video detail screen.
 */
@Composable
fun HistoryScreen(
    history: HistoryStore,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items by history.items.collectAsState()

    // Reuse the paged grid by exposing the local snapshot as a single page list.
    val store = remember(items) {
        FeedStore(defaultErrorMessage = context.getString(R.string.error_network_generic)) { page ->
            val pageSize = 20
            val from = page * pageSize
            val slice = if (from < items.size) {
                items.subList(from, minOf(from + pageSize, items.size)).toList()
            } else {
                emptyList()
            }
            Result.success(
                VideoPage(
                    items = slice,
                    pageIndex = page,
                    limit = pageSize,
                    totalCount = items.size,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground)
            .statusBarsPadding(),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            trailing = if (items.isNotEmpty()) {
                {
                    TextButton(onClick = { history.clear() }) {
                        Text(
                            text = stringResource(R.string.history_clear),
                            color = FlowPink,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                null
            },
        )
        VideoGrid(
            store = store,
            onVideoClick = { video -> onOpenVideo(video.id) },
            modifier = Modifier.weight(1f),
            emptyTitle = stringResource(R.string.history_empty),
            emptySubtitle = stringResource(R.string.history_empty_subtitle),
        )
    }
}
