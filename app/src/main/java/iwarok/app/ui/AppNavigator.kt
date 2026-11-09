package iwarok.app.ui

import androidx.compose.runtime.mutableStateListOf

/* ------------------------------------------------------------------------- *
 * Minimal in-app navigation: a simple back stack of immutable routes.
 * navigation-compose is deliberately not a dependency of this project.
 * ------------------------------------------------------------------------- */

sealed interface AppRoute {
    data object Feed : AppRoute
    data object Search : AppRoute
    data object Settings : AppRoute
    data object Login : AppRoute

    /** Locally saved videos ("我的收藏"). */
    data object Favorites : AppRoute

    /** Locally recorded watch history. */
    data object History : AppRoute

    /** In-app user profile page (`fetchProfile`). */
    data class Profile(val username: String) : AppRoute

    /** In-app tag results page (`fetchTagVideos`). */
    data class TagResults(val tagId: String) : AppRoute

    /** In-app video detail / playback page. */
    data class VideoDetail(val videoId: String) : AppRoute
}

class AppNavigator(initial: AppRoute = AppRoute.Feed) {

    private val entries = mutableStateListOf(initial)

    val current: AppRoute
        get() = entries.last()

    val canGoBack: Boolean
        get() = entries.size > 1

    fun push(route: AppRoute) {
        if (entries.last() == route) return
        entries.add(route)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}
