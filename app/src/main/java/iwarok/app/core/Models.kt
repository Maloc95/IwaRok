package iwarok.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------------- *
 * Iwara wire / domain models.
 *
 * These types are serialized directly with kotlinx.serialization and shared by
 * the data and UI layers. Unknown JSON keys are ignored by the Json config.
 * All nullable fields are intentionally permissive because the upstream API
 * omits fields for different content kinds (video / image / gallery entries).
 * ------------------------------------------------------------------------- */

@Serializable
data class IwaraFile(
    val id: String = "",
    val type: String? = null,
    val path: String? = null,
    val name: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val numThumbnails: Int? = null,
    val animatedPreview: Boolean? = null,
)

@Serializable
data class IwaraUser(
    val id: String = "",
    val name: String? = null,
    val username: String = "",
    val status: String? = null,
    val role: String? = null,
    val avatar: IwaraFile? = null,
    val header: IwaraFile? = null,
    val premium: Boolean? = null,
    val creatorProgram: Boolean? = null,
    val followedBy: Boolean? = null,
    val following: Boolean? = null,
    val friend: Boolean? = null,
    val locale: String? = null,
    val seenAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class IwaraTag(
    val id: String = "",
    val type: String? = null,
    val sensitive: Boolean? = null,
)

@Serializable
data class IwaraVideo(
    val id: String = "",
    val slug: String? = null,
    val title: String? = null,
    val body: String? = null,
    val status: String? = null,
    val rating: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val unlisted: Boolean = false,
    val thumbnail: Int? = null,
    val embedUrl: String? = null,
    val liked: Boolean? = null,
    val numLikes: Int? = null,
    val numViews: Int? = null,
    val numComments: Int? = null,
    val file: IwaraFile? = null,
    val customThumbnail: IwaraFile? = null,
    val user: IwaraUser? = null,
    val tags: List<IwaraTag> = emptyList(),
    val siteId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Present on the /video/{id} detail response only. Signed quality-manifest URL. */
    val fileUrl: String? = null,
)

@Serializable
data class IwaraVideoPage(
    val count: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val results: List<IwaraVideo> = emptyList(),
)

/**
 * `GET /search?type=users` response. [results] are plain [IwaraUser] objects
 * (same shape as `video.user`). [type] echoes `"users"` from the upstream API.
 */
@Serializable
data class IwaraUserPage(
    val count: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val results: List<IwaraUser> = emptyList(),
    val type: String? = null,
)

@Serializable
data class IwaraProfile(
    val body: String? = null,
    val header: IwaraFile? = null,
    val user: IwaraUser? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class IwaraComment(
    val id: String = "",
    val body: String? = null,
    val numReplies: Int? = null,
    val approved: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val user: IwaraUser? = null,
    val videoId: String? = null,
)

@Serializable
data class IwaraCommentPage(
    val count: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val results: List<IwaraComment> = emptyList(),
    val pendingCount: Int? = null,
)

@Serializable
data class VideoSource(
    val name: String = "",
    val type: String? = null,
    val src: SourceUrls? = null,
)

@Serializable
data class SourceUrls(
    val view: String? = null,
    val download: String? = null,
)

/* ------------------------------------------------------------------------- *
 * App-level domain types (not serialized directly).
 * ------------------------------------------------------------------------- */

/**
 * Which Iwara frontend the API requests should impersonate.
 *
 * The upstream API hosts are shared between both sites; the `x-site` header
 * (plus `Origin`/`Referer`) selects the catalogue. Main site = regular content,
 * AI site = `www.iwara.ai` generated content (`siteId == "iwara_ai"`). Playback
 * resolution works identically for both (same fileUrl / X-Version scheme).
 */
enum class ContentSite(val xSite: String, val origin: String) {
    MAIN("www.iwara.tv", "https://www.iwara.tv"),
    AI("www.iwara.ai", "https://www.iwara.ai"),
}

enum class FeedSort(val apiValue: String, val label: String) {
    /**
     * Local-only "for you" mode: the data layer mixes trending / popularity /
     * date on random pages instead of calling one fixed upstream sort.
     * [apiValue] is never sent to the API for this entry.
     */
    RECOMMENDED("recommended", "发现"),
    DATE("date", "最新"),
    TRENDING("trending", "趋势"),
    POPULARITY("popularity", "热门"),
}

/** Sort modes accepted by Iwara keyword search for videos. */
enum class SearchVideoSort(val apiValue: String, val label: String) {
    RELEVANCE("relevance", "相关"),
    DATE("date", "最新"),
    VIEWS("views", "播放"),
    LIKES("likes", "点赞"),
}

enum class RatingFilter(val apiValue: String, val label: String) {
    ALL("all", "全部"),
    GENERAL("general", "全年龄"),
    ECCHI("ecchi", "Ecchi"),
}

enum class QualityPreference(val label: String) {
    AUTO("自动"),
    SOURCE("原画"),
    P1080("1080P"),
    P720("720P"),
    P540("540P"),
    P360("360P"),
}

sealed interface AuthState {
    /** Session restore still running; UI should show a splash. */
    data object Loading : AuthState
    data object Guest : AuthState
    data class LoggedIn(val user: IwaraUser) : AuthState
}

data class AppSettings(
    val sort: FeedSort = FeedSort.RECOMMENDED,
    val rating: RatingFilter = RatingFilter.ALL,
    val quality: QualityPreference = QualityPreference.AUTO,
    val contentSite: ContentSite = ContentSite.MAIN,
)

data class VideoPage(
    val items: List<IwaraVideo>,
    val pageIndex: Int,
    val limit: Int,
    val totalCount: Int,
) {
    val hasMore: Boolean
        get() = items.isNotEmpty() && (pageIndex + 1) * limit < totalCount
}

data class CommentsPage(
    val items: List<IwaraComment>,
    val pageIndex: Int,
    val limit: Int,
    val totalCount: Int,
) {
    val hasMore: Boolean
        get() = items.isNotEmpty() && (pageIndex + 1) * limit < totalCount
}

data class PlaybackSource(
    val name: String,
    val label: String,
    val url: String,
    val mime: String,
)

data class PlaybackInfo(
    val sources: List<PlaybackSource>,
    val previewUrl: String?,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val fileId: String,
) {
    /** Best source after the repository applied the user's quality preference. */
    val primary: PlaybackSource? get() = sources.firstOrNull()
}
