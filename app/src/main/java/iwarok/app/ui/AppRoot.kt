package iwarok.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.LocalImageLoader
import iwarok.app.AppGraph
import iwarok.app.core.AppSettings
import iwarok.app.core.AuthState
import iwarok.app.core.IwaraConfig
import iwarok.app.ui.player.PlayerManager
import iwarok.app.ui.screens.FavoritesScreen
import iwarok.app.ui.screens.HistoryScreen
import iwarok.app.ui.screens.LoginScreen
import iwarok.app.ui.screens.ProfileScreen
import iwarok.app.ui.screens.SearchScreen
import iwarok.app.ui.screens.SearchScreenState
import iwarok.app.ui.screens.SettingsScreen
import iwarok.app.ui.screens.SplashScreen
import iwarok.app.ui.screens.TagResultsScreen
import iwarok.app.ui.screens.VideoDetailScreen
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.util.FavoritesStore
import iwarok.app.ui.util.HistoryStore
import iwarok.app.ui.util.TagChannelsStore
import iwarok.app.ui.feed.HomeFeedScreen
import iwarok.app.ui.feed.HomeFeedState

/**
 * UI entry point for the whole app.
 *
 * Responsibilities:
 *  - 18+ age gate (persisted, first launch),
 *  - branded splash while the session is restored,
 *  - in-app navigation (home feed / search / settings / login),
 *  - shared ExoPlayer + Coil [ImageLoader] ownership,
 *  - Activity lifecycle pause/resume of playback.
 */
@Composable
fun AppRoot(graph: AppGraph) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val authState by graph.repository.authState.collectAsStateWithLifecycle()
    val settings by graph.repository.settings.collectAsStateWithLifecycle()

    // One ExoPlayer for the whole app, shared by every feed page.
    val playerManager = remember { PlayerManager(context, graph.mediaHttpClient) }

    // Local favorites (persisted snapshots), shared by feed / detail / favorites.
    val favorites = remember { FavoritesStore.get(context) }

    // Local watch history (persisted snapshots), recorded from feed / detail.
    val history = remember { HistoryStore.get(context) }

    // User-configurable home-feed tag channels (persisted; add / remove / reset).
    val tagChannels = remember { TagChannelsStore.get(context) }

    // Coil uses the same OkHttp connection pool, with the browser-like headers
    // Iwara's image host expects.
    val imageLoader = remember {
        val client = graph.httpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", IwaraConfig.USER_AGENT)
                    .header("Referer", "${IwaraConfig.SITE_ORIGIN}/")
                    .build()
                chain.proceed(request)
            }
            .build()
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
            imageLoader.shutdown()
        }
    }

    // Activity onPause / onResume -> pause / resume playback.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerManager.onAppPaused()
                Lifecycle.Event.ON_RESUME -> playerManager.onAppResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        Surface(modifier = Modifier.fillMaxSize(), color = FlowBackground) {
            if (authState is AuthState.Loading) {
                SplashScreen()
            } else {
                MainShell(
                    graph = graph,
                    authState = authState,
                    settings = settings,
                    playerManager = playerManager,
                    favorites = favorites,
                    tagChannels = tagChannels,
                    history = history,
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    graph: AppGraph,
    authState: AuthState,
    settings: AppSettings,
    playerManager: PlayerManager,
    favorites: FavoritesStore,
    tagChannels: TagChannelsStore,
    history: HistoryStore,
) {
    val navigator = remember { AppNavigator() }
    val searchState = remember { SearchScreenState() }
    // Hoisted so Settings / Search / Profile / Detail / Favorites / Login and
    // back keep the same feed store, sort/channel and pager page.
    val homeFeedState = remember { HomeFeedState() }
    val isLoggedIn = authState is AuthState.LoggedIn

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }

    val openProfile: (String) -> Unit = { username ->
        if (username.isNotBlank()) navigator.push(AppRoute.Profile(username))
    }
    val openTag: (String) -> Unit = { tagId ->
        if (tagId.isNotBlank()) navigator.push(AppRoute.TagResults(tagId))
    }
    val openVideo: (String) -> Unit = { videoId ->
        if (videoId.isNotBlank()) navigator.push(AppRoute.VideoDetail(videoId))
    }
    val openLogin: () -> Unit = { navigator.push(AppRoute.Login) }

    when (val route = navigator.current) {
        is AppRoute.Feed -> HomeFeedScreen(
            graph = graph,
            settings = settings,
            playerManager = playerManager,
            favorites = favorites,
            tagChannels = tagChannels,
            feedState = homeFeedState,
            isLoggedIn = isLoggedIn,
            currentUser = (authState as? AuthState.LoggedIn)?.user,
            onOpenSearch = { navigator.push(AppRoute.Search) },
            onOpenSettings = { navigator.push(AppRoute.Settings) },
            onOpenFavorites = { navigator.push(AppRoute.Favorites) },
            onOpenHistory = { navigator.push(AppRoute.History) },
            history = history,
            onRequestLogin = openLogin,
            onOpenProfile = openProfile,
            onOpenTag = openTag,
            onOpenVideo = openVideo,
        )

        is AppRoute.Favorites -> FavoritesScreen(
            favorites = favorites,
            onBack = { navigator.pop() },
            onOpenVideo = openVideo,
        )

        is AppRoute.History -> HistoryScreen(
            history = history,
            onBack = { navigator.pop() },
            onOpenVideo = openVideo,
        )

        is AppRoute.Search -> SearchScreen(
            graph = graph,
            settings = settings,
            state = searchState,
            onBack = { navigator.pop() },
            onOpenVideo = openVideo,
            onOpenProfile = openProfile,
        )

        is AppRoute.Profile -> ProfileScreen(
            graph = graph,
            username = route.username,
            sort = settings.sort,
            onBack = { navigator.pop() },
            onOpenVideo = openVideo,
        )

        is AppRoute.TagResults -> TagResultsScreen(
            graph = graph,
            tagId = route.tagId,
            sort = settings.sort,
            rating = settings.rating,
            onBack = { navigator.pop() },
            onOpenVideo = openVideo,
        )

        is AppRoute.VideoDetail -> VideoDetailScreen(
            graph = graph,
            videoId = route.videoId,
            quality = settings.quality,
            playerManager = playerManager,
            favorites = favorites,
            history = history,
            isLoggedIn = isLoggedIn,
            onBack = { navigator.pop() },
            onOpenProfile = openProfile,
            onOpenTag = openTag,
            onOpenVideo = openVideo,
            onRequestLogin = openLogin,
        )

        is AppRoute.Settings -> SettingsScreen(
            graph = graph,
            settings = settings,
            authState = authState,
            onBack = { navigator.pop() },
            onRequestLogin = openLogin,
        )

        is AppRoute.Login -> LoginScreen(
            graph = graph,
            onBack = { navigator.pop() },
            onGuestContinue = { navigator.pop() },
            onLoggedIn = {
                if (navigator.current is AppRoute.Login) navigator.pop()
            },
        )
    }
}
