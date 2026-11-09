package iwarok.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.FeedSort
import iwarok.app.core.RatingFilter
import iwarok.app.core.tagLabel
import iwarok.app.ui.components.ScreenTopBar
import iwarok.app.ui.components.VideoGrid
import iwarok.app.ui.feed.FeedStore
import iwarok.app.ui.theme.FlowBackground

/**
 * In-app tag results screen: paginated videos for a single Iwara tag
 * (`fetchTagVideos`). Cards open the in-app video detail screen.
 */
@Composable
fun TagResultsScreen(
    graph: AppGraph,
    tagId: String,
    sort: FeedSort,
    rating: RatingFilter = RatingFilter.ALL,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(tagId, sort, rating) {
        FeedStore(defaultErrorMessage = context.getString(R.string.error_network_generic)) { page ->
            graph.repository.fetchTagVideos(tagId, sort, page, rating)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground)
            .statusBarsPadding(),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.tag_results_title, tagLabel(tagId)),
            onBack = onBack,
        )
        VideoGrid(
            store = store,
            onVideoClick = { video -> onOpenVideo(video.id) },
            modifier = Modifier.weight(1f),
            emptyTitle = stringResource(R.string.tag_results_empty),
        )
    }
}
