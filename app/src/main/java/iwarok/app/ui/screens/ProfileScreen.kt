package iwarok.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraProfile
import iwarok.app.core.imageUrl
import iwarok.app.core.profileHeaderUrl
import iwarok.app.ui.components.EmptyState
import iwarok.app.ui.components.ErrorState
import iwarok.app.ui.components.LoadingState
import iwarok.app.ui.components.ScreenTopBar
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.components.VideoGrid
import iwarok.app.ui.feed.FeedStore
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowSurfaceElevated
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.util.openUrl

/**
 * In-app user profile: banner / avatar / bio from `fetchProfile`, followed by a
 * two-column grid of the user's videos (`fetchUserVideos`). Videos open the
 * in-app detail screen.
 */
@Composable
fun ProfileScreen(
    graph: AppGraph,
    username: String,
    sort: FeedSort,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var profile by remember(username) { mutableStateOf<IwaraProfile?>(null) }
    var isLoading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }
    var reloadTick by remember(username) { mutableIntStateOf(0) }

    LaunchedEffect(username, reloadTick) {
        isLoading = true
        error = null
        graph.repository.fetchProfile(username)
            .onSuccess { profile = it }
            .onFailure { error = it.message ?: context.getString(R.string.error_profile_load_failed) }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground)
            .statusBarsPadding(),
    ) {
        ScreenTopBar(
            title = stringResource(R.string.profile_title),
            onBack = onBack,
        )

        val currentProfile = profile
        val currentError = error
        when {
            currentProfile == null && isLoading -> {
                LoadingState(
                    text = stringResource(R.string.loading),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            currentProfile == null && currentError != null -> {
                ErrorState(
                    message = currentError,
                    title = stringResource(R.string.profile_load_failed),
                    onRetry = { reloadTick++ },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            currentProfile == null -> {
                EmptyState(
                    title = stringResource(R.string.profile_load_failed),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                val userId = currentProfile.user?.id.orEmpty()
                if (userId.isBlank()) {
                    EmptyState(
                        title = stringResource(R.string.profile_load_failed),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val store = remember(userId, sort) {
                        FeedStore(defaultErrorMessage = context.getString(R.string.error_network_generic)) { page ->
                            graph.repository.fetchUserVideos(userId, sort, page)
                        }
                    }
                    VideoGrid(
                        store = store,
                        onVideoClick = { video -> onOpenVideo(video.id) },
                        modifier = Modifier.weight(1f),
                        header = {
                            ProfileHeader(
                                profile = currentProfile,
                                fallbackUsername = username,
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 24.dp),
                        emptyTitle = stringResource(R.string.profile_videos_empty),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: IwaraProfile,
    fallbackUsername: String,
) {
    val context = LocalContext.current
    val user = profile.user
    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: fallbackUsername
    val handle = user?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername
    val headerUrl = imageUrl(profile.header, "profileHeader") ?: profileHeaderUrl(user)

    // Bio is clamped to 4 lines with an expand/collapse toggle when it overflows.
    var bioExpanded by remember(profile.user?.id) { mutableStateOf(false) }
    var bioOverflows by remember(profile.user?.id) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(FlowSurfaceElevated, FlowSurface))),
        ) {
            if (headerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(headerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to FlowBackground.copy(alpha = 0.92f),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user = user, size = 62.dp, borderWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "@$handle",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val bio = profile.body?.trim().orEmpty()
        if (bio.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            // Lightweight Markdown: links are clickable, `![alt](url)` images are
            // rendered below the (expandable) text body.
            val parsedBio = remember(bio) {
                parseBioMarkdown(bio) { url -> openUrl(context, url) }
            }
            if (parsedBio.text.isNotBlank()) {
                Text(
                    text = parsedBio.text,
                    color = FlowTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!bioExpanded) bioOverflows = result.hasVisualOverflow
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (bioOverflows) {
                    Text(
                        text = stringResource(
                            if (bioExpanded) R.string.profile_collapse else R.string.profile_expand,
                        ),
                        color = FlowPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { bioExpanded = !bioExpanded }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
            if (parsedBio.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                parsedBio.images.forEach { imageUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.profile_bio_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openUrl(context, imageUrl) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_tab_videos),
                color = FlowTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/* ------------------------------------------------------------------------- *
 * Lightweight Markdown parsing for the profile bio: Markdown links, bare
 * http(s) URLs and `![alt](url)` images. No external dependency is used.
 * ------------------------------------------------------------------------- */

private val BioImageRegex = Regex("""!\[[^\]]*]\((https?://[^)\s]+)\)""")
private val BioLinkRegex = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val BioBareUrlRegex = Regex("""https?://[^\s<>()\[\]]+""")

private data class ParsedBio(
    val text: AnnotatedString,
    val images: List<String>,
)

private fun parseBioMarkdown(raw: String, onOpenUrl: (String) -> Unit): ParsedBio {
    val images = mutableListOf<String>()
    val withoutImages = BioImageRegex.replace(raw) { match ->
        val url = match.groupValues[1]
        if (images.size < 3 && url !in images) images += url
        ""
    }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = FlowPink, textDecoration = TextDecoration.Underline),
    )
    val linkable: (String) -> LinkAnnotation.Clickable = { url ->
        LinkAnnotation.Clickable(
            tag = "url:$url",
            styles = linkStyles,
            linkInteractionListener = LinkInteractionListener { onOpenUrl(url) },
        )
    }
    val builder = AnnotatedString.Builder()
    var cursor = 0
    while (cursor < withoutImages.length) {
        val match = BioLinkRegex.find(withoutImages, cursor)
        if (match == null) {
            appendBareUrlText(builder, withoutImages.substring(cursor), linkable)
            break
        }
        appendBareUrlText(builder, withoutImages.substring(cursor, match.range.first), linkable)
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        val start = builder.length
        builder.append(label)
        builder.addLink(linkable(url), start, builder.length)
        cursor = match.range.last + 1
    }
    return ParsedBio(builder.toAnnotatedString(), images)
}

private fun appendBareUrlText(
    builder: AnnotatedString.Builder,
    chunk: String,
    linkable: (String) -> LinkAnnotation.Clickable,
) {
    var cursor = 0
    while (cursor < chunk.length) {
        val match = BioBareUrlRegex.find(chunk, cursor)
        if (match == null) {
            builder.append(chunk.substring(cursor))
            break
        }
        builder.append(chunk.substring(cursor, match.range.first))
        val url = match.value
        val start = builder.length
        builder.append(url)
        builder.addLink(linkable(url), start, builder.length)
        cursor = match.range.last + 1
    }
}
