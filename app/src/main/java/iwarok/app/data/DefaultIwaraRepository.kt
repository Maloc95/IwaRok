package iwarok.app.data

import android.content.Context
import iwarok.app.core.AppSettings
import iwarok.app.core.AuthState
import iwarok.app.core.CommentsPage
import iwarok.app.core.ContentSite
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraComment
import iwarok.app.core.IwaraCommentPage
import iwarok.app.core.IwaraException
import iwarok.app.core.IwaraProfile
import iwarok.app.core.IwaraRepository
import iwarok.app.core.IwaraTag
import iwarok.app.core.IwaraUser
import iwarok.app.core.IwaraUserPage
import iwarok.app.core.IwaraVideo
import iwarok.app.core.IwaraVideoPage
import iwarok.app.core.IwaraConfig
import iwarok.app.core.PlaybackInfo
import iwarok.app.core.QualityPreference
import iwarok.app.core.SearchVideoSort
import iwarok.app.core.RatingFilter
import iwarok.app.core.VideoPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Production implementation of [IwaraRepository].
 *
 * Wires the shared OkHttp client into [IwaraHttp], keeps the session alive via
 * [AuthManager], restores [SettingsStore] preferences and resolves playback
 * through [PlaybackResolver]. Every public method returns a [Result] whose
 * failure is an [IwaraException] carrying a user-facing Chinese message.
 */
class DefaultIwaraRepository(
    context: Context,
    httpClient: OkHttpClient,
) : IwaraRepository {

    private val appContext: Context = context.applicationContext

    private val http = IwaraHttp(httpClient)
    private val auth = AuthManager(IwaraAuthApi(http), AndroidSessionStore(appContext))
    private val settingsStore = SettingsStore(appContext)
    private val playbackResolver = PlaybackResolver(http)

    /** Videos already shown in the local recommendation feed. */
    private val seenVideos = SeenVideoStore(appContext)
    private val recommendationProfile = RecommendationProfileStore(appContext)

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false

    override val authState: StateFlow<AuthState> = auth.authState
    override val settings: StateFlow<AppSettings> = settingsStore.settings

    override suspend fun init() {
        initMutex.withLock {
            if (initialized) return
            var loadedSite = ContentSite.MAIN
            try {
                loadedSite = settingsStore.load().contentSite
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Unreadable preferences: keep defaults.
            }
            // The site must be applied before any other request (session restore
            // itself talks to the API and needs the right headers).
            http.site = loadedSite
            try {
                auth.init()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Defensive: never leave the auth state stuck on Loading.
            }
            initialized = true
        }
    }

    /* ------------------------------ auth ------------------------------ */

    override suspend fun login(email: String, password: String): Result<IwaraUser> = auth.login(email, password)

    override suspend fun loginWithToken(rawToken: String): Result<IwaraUser> = auth.loginWithToken(rawToken)

    override suspend fun logout() = auth.logout()

    /* ---------------------------- settings ---------------------------- */

    override suspend fun setSort(sort: FeedSort) = settingsStore.setSort(sort)

    override suspend fun setRating(rating: RatingFilter) = settingsStore.setRating(rating)

    override suspend fun setQuality(quality: QualityPreference) = settingsStore.setQuality(quality)

    override suspend fun setContentSite(site: ContentSite) {
        // Apply to the transport first so requests started right after the switch
        // already carry the new x-site / Origin / Referer headers.
        http.site = site
        settingsStore.setContentSite(site)
    }

    /* ----------------------------- videos ----------------------------- */

    override suspend fun fetchFeed(sort: FeedSort, rating: RatingFilter, page: Int): Result<VideoPage> = resultOf {
        val requestedPage = page.coerceAtLeast(0)
        if (sort == FeedSort.RECOMMENDED) {
            return@resultOf fetchRecommended(rating = rating, page = requestedPage, tagId = null)
        }
        val body = guestRequest(
            path = "/videos",
            query = mapOf(
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
                "sort" to sort.apiValue,
                "rating" to rating.apiValue,
            ),
        )
        decodeVideoPage(body, requestedPage).onlyPublicVideos()
    }

    override suspend fun fetchSearch(query: String, sort: SearchVideoSort, page: Int): Result<VideoPage> = resultOf {
        val requestedPage = page.coerceAtLeast(0)
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            throw IwaraException("请输入搜索关键词")
        }
        // Iwara keyword search supports relevance/date/views/likes only;
        // trending and popularity return HTTP 400.
        val body = guestRequest(
            path = "/search",
            query = mapOf(
                "query" to trimmedQuery,
                "type" to "videos",
                "page" to requestedPage.toString(),
                "sort" to sort.apiValue,
            ),
        )
        decodeVideoPage(body, requestedPage)
    }

    override suspend fun searchUsers(query: String, page: Int): Result<IwaraUserPage> = resultOf {
        val requestedPage = page.coerceAtLeast(0)
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            throw IwaraException("请输入要搜索的作者名")
        }
        // Guest access works for user search (no token required).
        val body = guestRequest(
            path = "/search",
            query = mapOf(
                "query" to trimmedQuery,
                "type" to "users",
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
            ),
        )
        decodeIwaraUserPage(body, requestedPage)
    }

    override suspend fun fetchVideo(videoId: String): Result<IwaraVideo> = resultOf {
        requireVideoId(videoId)
        decodeVideo(guestRequest(path = "/video/$videoId"))
    }

    override suspend fun fetchRelated(videoId: String, page: Int): Result<VideoPage> = resultOf {
        requireVideoId(videoId)
        val requestedPage = page.coerceAtLeast(0)
        val body = guestRequest(
            path = "/video/$videoId/related",
            query = mapOf(
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
            ),
        )
        decodeVideoPage(body, requestedPage)
    }

    override suspend fun fetchProfile(username: String): Result<IwaraProfile> = resultOf {
        val name = username.trim()
        if (name.isEmpty()) throw IwaraException("用户名无效")
        val body = guestRequest(path = "/profile/${java.net.URLEncoder.encode(name, "UTF-8")}")
        val profile = try {
            iwaraJson.decodeFromString<IwaraProfile>(body)
        } catch (e: Exception) {
            throw IwaraException("用户信息解析失败，请稍后再试", 0, e)
        }
        if (profile.user?.id.isNullOrBlank()) {
            throw IwaraException("用户不存在或已被删除", 404)
        }
        profile
    }

    override suspend fun fetchUserVideos(
        userId: String,
        sort: FeedSort,
        page: Int,
    ): Result<VideoPage> = resultOf {
        if (userId.isBlank()) throw IwaraException("用户 ID 无效")
        val requestedPage = page.coerceAtLeast(0)
        // A single creator cannot be "recommended", so fall back to the default
        // list order when the global preference is the local recommendation mode.
        val apiSort = if (sort == FeedSort.RECOMMENDED) FeedSort.DATE else sort
        val body = guestRequest(
            path = "/videos",
            query = mapOf(
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
                "sort" to apiSort.apiValue,
                "rating" to RatingFilter.ALL.apiValue,
                "user" to userId,
            ),
        )
        decodeVideoPage(body, requestedPage).onlyPublicVideos()
    }

    override suspend fun fetchTagVideos(
        tagId: String,
        sort: FeedSort,
        page: Int,
        rating: RatingFilter,
    ): Result<VideoPage> = resultOf {
        if (tagId.isBlank()) throw IwaraException("标签无效")
        val requestedPage = page.coerceAtLeast(0)
        if (sort == FeedSort.RECOMMENDED) {
            return@resultOf fetchRecommended(rating = rating, page = requestedPage, tagId = tagId)
        }
        val body = guestRequest(
            path = "/videos",
            query = mapOf(
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
                "sort" to sort.apiValue,
                "rating" to rating.apiValue,
                "tags" to tagId,
            ),
        )
        decodeVideoPage(body, requestedPage).onlyPublicVideos()
    }

    override suspend fun fetchComments(videoId: String, page: Int): Result<CommentsPage> = resultOf {
        requireVideoId(videoId)
        val requestedPage = page.coerceAtLeast(0)
        val body = guestRequest(
            path = "/video/$videoId/comments",
            query = mapOf(
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
            ),
        )
        decodeCommentsPage(body, requestedPage)
    }

    override suspend fun fetchCommentsNewest(videoId: String, page: Int): Result<CommentsPage> = resultOf {
        requireVideoId(videoId)
        val requestedPage = page.coerceAtLeast(0)
        val limit = IwaraConfig.PAGE_SIZE
        // The public API only returns ascending (oldest-first) pages. Read the
        // count from page 0, then map our virtual newest-first page to the
        // matching real page from the end and reverse it.
        val countBody = guestRequest(
            path = "/video/$videoId/comments",
            query = mapOf("page" to "0", "limit" to limit.toString()),
        )
        val countPage = decodeCommentsPage(countBody, 0)
        val total = countPage.totalCount
        val lastRealPage = if (total <= 0) 0 else (total - 1) / limit
        val realPage = lastRealPage - requestedPage
        if (realPage < 0) {
            return@resultOf CommentsPage(
                items = emptyList(),
                pageIndex = requestedPage,
                limit = limit,
                totalCount = total,
            )
        }
        val body = guestRequest(
            path = "/video/$videoId/comments",
            query = mapOf("page" to realPage.toString(), "limit" to limit.toString()),
        )
        val decoded = decodeCommentsPage(body, realPage)
        CommentsPage(
            items = decoded.items.reversed(),
            pageIndex = requestedPage,
            limit = limit,
            totalCount = total,
        )
    }

    override suspend fun fetchRelatedVideos(videoId: String): Result<List<IwaraVideo>> = resultOf {
        requireVideoId(videoId)
        val body = guestRequest(path = "/video/$videoId/related")
        val page = try {
            iwaraJson.decodeFromString<IwaraVideoPage>(body)
        } catch (e: Exception) {
            throw IwaraException("相似视频加载失败", 0, e)
        }
        page.results
    }

    override suspend fun fetchCommentReplies(
        videoId: String,
        commentId: String,
        page: Int,
    ): Result<CommentsPage> = resultOf {
        requireVideoId(videoId)
        if (commentId.isBlank()) throw IwaraException("评论 ID 无效")
        val requestedPage = page.coerceAtLeast(0)
        val body = guestRequest(
            path = "/video/$videoId/comments",
            query = mapOf(
                "parent" to commentId,
                "page" to requestedPage.toString(),
                "limit" to IwaraConfig.PAGE_SIZE.toString(),
            ),
        )
        decodeCommentsPage(body, requestedPage)
    }

    override suspend fun postComment(videoId: String, body: String): Result<Unit> = resultOf {        requireVideoId(videoId)
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw IwaraException("评论内容不能为空")
        if (trimmed.length > 2000) throw IwaraException("评论内容过长")
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("body", kotlinx.serialization.json.JsonPrimitive(trimmed))
        }
        val bodyJson = iwaraJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            payload,
        )
        withLogin("请先登录后再评论") { token ->
            http.requestJson(
                method = "POST",
                path = "/video/$videoId/comments",
                bearerToken = token,
                jsonBody = bodyJson,
            )
        }
        Unit
    }

    override suspend fun setLiked(videoId: String, liked: Boolean): Result<Unit> = resultOf {
        requireVideoId(videoId)
        withLogin("请先登录后再点赞") { token ->
            http.requestJson(
                method = if (liked) "POST" else "DELETE",
                path = "/video/$videoId/like",
                bearerToken = token,
            )
        }
        Unit
    }

    override suspend fun setFollowing(userId: String, follow: Boolean): Result<Unit> = resultOf {
        if (userId.isBlank()) throw IwaraException("用户 ID 无效")
        withLogin("请先登录后再关注") { token ->
            http.requestJson(
                method = if (follow) "POST" else "DELETE",
                path = "/user/$userId/followers",
                bearerToken = token,
            )
        }
        Unit
    }

    /* ---------------------------- playback ---------------------------- */

    override suspend fun fetchTag(tagId: String): Result<IwaraTag> = resultOf {
        val id = tagId.trim()
        if (id.isEmpty()) throw IwaraException("标签无效")
        val body = guestRequest(path = "/tags/${java.net.URLEncoder.encode(id, "UTF-8")}")
        val tag = try {
            iwaraJson.decodeFromString<IwaraTag>(body)
        } catch (e: Exception) {
            throw IwaraException("标签不存在或无法读取", 0, e)
        }
        if (tag.id.isBlank()) throw IwaraException("标签不存在", 404)
        tag
    }

    override suspend fun fetchPlayback(
        videoId: String,
        quality: QualityPreference,
        refresh: Boolean,
    ): Result<PlaybackInfo> = resultOf {
        requireVideoId(videoId)
        playbackResolver.resolve(videoId, quality, refresh) { auth.optionalAccessToken() }
    }

    /* ----------------------------- helpers ---------------------------- */

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: IwaraException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(IwaraException("操作失败，请检查网络后重试", 0, e))
    }

    /** GET for guest-capable endpoints; retries token-less when the token is stale. */
    private suspend fun guestRequest(
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): String {
        val token = auth.optionalAccessToken()
        if (token == null) {
            return http.requestJson(method = "GET", path = path, query = query)
        }
        return try {
            http.requestJson(method = "GET", path = path, query = query, bearerToken = token)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IwaraException) {
            if (e.statusCode == 401) {
                http.requestJson(method = "GET", path = path, query = query)
            } else {
                throw e
            }
        }
    }

    /** Runs [block] with a valid access token, renewing once after a 401. */
    private suspend fun <T> withLogin(
        loginMessage: String,
        block: suspend (String) -> T,
    ): T {
        val token = auth.optionalAccessToken() ?: throw IwaraException(loginMessage, 401)
        return try {
            block(token)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IwaraException) {
            if (e.statusCode != 401) throw e
            val renewed = auth.refreshAccessToken(force = true)
                ?: throw IwaraException("登录已失效，请重新登录", 401)
            block(renewed)
        }
    }

    /**
     * Builds one random "for you" page by sampling trending / popularity / date
     * pools concurrently, merging + deduplicating them, suppressing recently seen
     * videos and shuffling. Each requested page is a fresh sample; [VideoPage.totalCount]
     * is a large sentinel so the UI can page indefinitely.
     */
    private suspend fun fetchRecommended(
        rating: RatingFilter,
        page: Int,
        tagId: String?,
    ): VideoPage {
        val random = Random(System.nanoTime())
        val seen = seenVideos.seen()
        val extraDepth = RecommendedMixer.extraDepthFor(seen.size)
        // Pages are drawn before launching the async jobs: kotlin.random.Random is
        // not thread-safe, and each pool must keep a stable page while in flight.
        val plans = RecommendedMixer.SOURCES.map { source ->
            source to RecommendedMixer.randomPage(source, random, extraDepth)
        }
        val lastError = AtomicReference<Throwable?>(null)
        val pools: List<List<IwaraVideo>> = coroutineScope {
            plans.map { (source, poolPage) ->
                async {
                    try {
                        val query = buildMap<String, String?> {
                            put("page", poolPage.toString())
                            put("limit", IwaraConfig.PAGE_SIZE.toString())
                            put("sort", source.apiValue)
                            put("rating", rating.apiValue)
                            if (tagId != null) put("tags", tagId)
                        }
                        decodeVideoPage(
                            body = guestRequest(path = "/videos", query = query),
                            requestedPage = poolPage,
                        ).items
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        // One slow/failing pool must not kill the whole page.
                        lastError.compareAndSet(null, e)
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        val candidates = RecommendedMixer.mergeAndShuffle(pools, random)
        val profile = recommendationProfile.snapshot()
        val ranked = RecommendedMixer.applyProfile(candidates, profile, random)
        if (ranked.isEmpty()) {
            val cause = lastError.get()
            throw (cause as? IwaraException)
                ?: IwaraException("发现内容加载失败，请检查网络后重试", 0, cause)
        }
        val (builtPage, shownIds) = RecommendedMixer.buildPage(
            candidates = ranked,
            seen = seen,
            pageIndex = page,
        )
        if (builtPage.items.isEmpty()) throw IwaraException("发现内容为空，请稍后再试")
        seenVideos.markSeen(shownIds)
        return builtPage
    }

    override fun recordInterest(video: IwaraVideo) {
        recommendationProfile.record(video)
    }

    private fun requireVideoId(videoId: String) {
        if (videoId.isBlank()) throw IwaraException("视频 ID 无效")
    }

    private fun decodeVideo(body: String): IwaraVideo {
        val video = try {
            iwaraJson.decodeFromString<IwaraVideo>(body)
        } catch (e: Exception) {
            throw IwaraException("视频信息解析失败，请稍后再试", 0, e)
        }
        if (video.id.isBlank()) throw IwaraException("视频不存在或已被删除", 404)
        return video
    }

    /** Feed/user lists must not show private, unlisted or non-active works. */
    private fun VideoPage.onlyPublicVideos(): VideoPage = copy(
        items = items.filter { video ->
            !video.isPrivate &&
                (video.status == null || video.status == "active") &&
                !video.file?.id.isNullOrBlank()
        },
    )

    private fun decodeVideoPage(body: String, requestedPage: Int): VideoPage {
        // Normal shape: {"count":..,"limit":..,"page":..,"results":[..]}.
        runCatching { iwaraJson.decodeFromString<IwaraVideoPage>(body) }.getOrNull()?.let { dto ->
            return VideoPage(
                items = dto.results,
                pageIndex = requestedPage,
                limit = dto.limit.takeIf { it > 0 } ?: IwaraConfig.PAGE_SIZE,
                totalCount = if (dto.count > 0) dto.count else dto.results.size,
            )
        }
        // Some endpoints (e.g. /related) may return a bare JSON array.
        runCatching { iwaraJson.decodeFromString<List<IwaraVideo>>(body) }.getOrNull()?.let { items ->
            return VideoPage(
                items = items,
                pageIndex = requestedPage,
                limit = IwaraConfig.PAGE_SIZE,
                totalCount = items.size,
            )
        }
        throw IwaraException("视频列表解析失败，请稍后再试")
    }

    private fun decodeCommentsPage(body: String, requestedPage: Int): CommentsPage {
        runCatching { iwaraJson.decodeFromString<IwaraCommentPage>(body) }.getOrNull()?.let { dto ->
            return CommentsPage(
                items = dto.results,
                pageIndex = requestedPage,
                limit = dto.limit.takeIf { it > 0 } ?: IwaraConfig.PAGE_SIZE,
                totalCount = if (dto.count > 0) dto.count else dto.results.size,
            )
        }
        runCatching { iwaraJson.decodeFromString<List<IwaraComment>>(body) }.getOrNull()?.let { items ->
            return CommentsPage(
                items = items,
                pageIndex = requestedPage,
                limit = IwaraConfig.PAGE_SIZE,
                totalCount = items.size,
            )
        }
        throw IwaraException("评论解析失败，请稍后再试")
    }
}

/**
 * Decodes `GET /search?query=&type=users`.
 *
 * The documented shape is `{page,count,limit,results,type}` with plain
 * [IwaraUser] objects, but some mirrors return a bare JSON array; both are
 * accepted. Exposed at file scope so the decoding rules are unit-testable.
 */
internal fun decodeIwaraUserPage(body: String, requestedPage: Int): IwaraUserPage {
    runCatching { iwaraJson.decodeFromString<IwaraUserPage>(body) }.getOrNull()?.let { dto ->
        return dto
    }
    runCatching { iwaraJson.decodeFromString<List<IwaraUser>>(body) }.getOrNull()?.let { users ->
        return IwaraUserPage(
            count = users.size,
            limit = users.size,
            page = requestedPage,
            results = users,
            type = "users",
        )
    }
    throw IwaraException("用户搜索解析失败，请稍后再试")
}
