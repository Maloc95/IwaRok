package iwarok.app.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point the UI uses for all Iwara data.
 *
 * Implemented by iwarok.app.data.DefaultIwaraRepository. AppGraph constructs
 * it with (applicationContext, shared OkHttpClient).
 */
interface IwaraRepository {

    /** Starts as Loading, then Guest or LoggedIn after the persisted session is restored. */
    val authState: StateFlow<AuthState>

    /** Persisted user preferences (sort / rating / quality). */
    val settings: StateFlow<AppSettings>

    /** Restores the persisted session and settings. Idempotent, safe to call once at startup. */
    suspend fun init()

    /** Email + password login. Throws [IwaraException] with a user-facing Chinese message. */
    suspend fun login(email: String, password: String): Result<IwaraUser>

    /** Login by pasting an Iwara refresh token or access token (advanced fallback). */
    suspend fun loginWithToken(rawToken: String): Result<IwaraUser>

    suspend fun logout()

    suspend fun setSort(sort: FeedSort)

    suspend fun setRating(rating: RatingFilter)

    suspend fun setQuality(quality: QualityPreference)

    /**
     * Switches the catalogue the API requests target (main site vs AI site).
     * Persisted in [settings] and applied to every subsequent request header.
     */
    suspend fun setContentSite(site: ContentSite)

    /** GET /videos. Guest access works; auth only changes liked/followed flags. */
    suspend fun fetchFeed(sort: FeedSort, rating: RatingFilter, page: Int): Result<VideoPage>

    /** Records a lightweight local tag/creator interest signal for the 发现 feed. */
    fun recordInterest(video: IwaraVideo)

    /**
     * GET /search?query=&type=videos. Guest search is currently supported by
     * the upstream API; a session token is used automatically when available.
     */
    suspend fun fetchSearch(query: String, sort: SearchVideoSort, page: Int): Result<VideoPage>

    /**
     * GET /search?query=&type=users. Guest access works; results are creators,
     * not videos. Paging uses [IwaraConfig.PAGE_SIZE] rows per page.
     */
    suspend fun searchUsers(query: String, page: Int): Result<IwaraUserPage>

    /** GET /video/{id}. Detail response contains fileUrl needed by playback resolution. */
    suspend fun fetchVideo(videoId: String): Result<IwaraVideo>

    /** GET /video/{id}/related. */
    suspend fun fetchRelated(videoId: String, page: Int): Result<VideoPage>

    /** GET /profile/{username}. Returns bio, profile header and the user object. */
    suspend fun fetchProfile(username: String): Result<IwaraProfile>

    /** GET /videos?user={userUuid}. Public video list of one creator. */
    suspend fun fetchUserVideos(userId: String, sort: FeedSort, page: Int): Result<VideoPage>

    /** GET /videos?tags={tagId}. Public video list filtered by one tag. */
    suspend fun fetchTagVideos(
        tagId: String,
        sort: FeedSort,
        page: Int,
        rating: RatingFilter = RatingFilter.ALL,
    ): Result<VideoPage>

    /** GET /tags/{tagId}. Validates a tag slug before adding a custom channel. */
    suspend fun fetchTag(tagId: String): Result<IwaraTag>

    /**
     * Resolves a playable source URL:
     *  1. GET /video/{id} -> fileUrl
     *  2. X-Version = SHA1("<fileUuid>_<expires>_<salt>"), GET fileUrl -> source manifest
     *  3. pick a quality according to [quality], normalize "//host/path" to "https://host/path"
     *
     * Results may be cached briefly; pass refresh = true to bypass the cache.
     */
    suspend fun fetchPlayback(
        videoId: String,
        quality: QualityPreference,
        refresh: Boolean = false,
    ): Result<PlaybackInfo>

    /** GET /video/{id}/comments. */
    suspend fun fetchComments(videoId: String, page: Int): Result<CommentsPage>

    /**
     * Comments in newest-first order. The upstream endpoint is chronological
     * ascending, so page 0 means the newest page (loaded backwards).
     */
    suspend fun fetchCommentsNewest(videoId: String, page: Int): Result<CommentsPage>

    /** GET /video/{id}/related — the web player's "related videos" list. */
    suspend fun fetchRelatedVideos(videoId: String): Result<List<IwaraVideo>>

    /** GET /video/{id}/comments?parent={commentId} — replies to one comment. */
    suspend fun fetchCommentReplies(
        videoId: String,
        commentId: String,
        page: Int,
    ): Result<CommentsPage>

    /** POST /video/{id}/comments with a plain-text body. Requires login. */
    suspend fun postComment(videoId: String, body: String): Result<Unit>

    /** POST /video/{id}/like or DELETE /video/{id}/like. Requires login. */
    suspend fun setLiked(videoId: String, liked: Boolean): Result<Unit>

    /** POST /user/{id}/followers (follow) or DELETE (unfollow). Requires login. */
    suspend fun setFollowing(userId: String, follow: Boolean): Result<Unit>

    fun isLoggedIn(): Boolean = authState.value is AuthState.LoggedIn
}

/** A user-facing Iwara failure. [message] is ready to be shown in a Snackbar/dialog. */
class IwaraException(
    message: String,
    val statusCode: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)
