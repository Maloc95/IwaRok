package iwarok.app.data

import iwarok.app.core.ContentSite
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/* ------------------------------------------------------------------------- *
 * User-facing error mapping.
 *
 * Kept as pure functions so the mapping is unit-testable on the JVM without
 * touching the network. Messages are already localised and ready to display.
 * ------------------------------------------------------------------------- */

internal object IwaraErrors {

    private val KNOWN_SERVER_MESSAGES: Map<String, String> = mapOf(
        "errors.invalidLogin" to "邮箱或密码错误，请检查后重试",
        "errors.userNotFound" to "账号不存在",
        "errors.invalidToken" to "登录凭证无效或已过期，请重新登录",
        "errors.unauthorized" to "需要登录 Iwara 账号",
        "errors.videoNotFound" to "作品不存在或已被删除",
        "errors.commentNotFound" to "评论不存在",
        "errors.forbidden" to "没有权限执行该操作",
    )

    fun mapStatus(status: Int, body: String? = null): IwaraException = when (status) {
        400 -> IwaraException(serverMessage(body) ?: "请求参数有误（400）", status)
        401 -> IwaraException("登录已失效，请重新登录", status)
        403 -> {
            val raw = body.orEmpty()
            when {
                // Cloudflare / human-verification HTML: this is the real risk-control case.
                looksLikeHtml(null, raw) -> IwaraException(
                    "请求被网页人机校验拦截，请稍后重试或更换网络",
                    status,
                )
                // The API's JSON forbidden response is used for private/restricted works.
                raw.contains("errors.forbidden") -> IwaraException(
                    "没有权限访问该内容（可能是私人或受限视频）",
                    status,
                )
                else -> IwaraException(
                    "访问被拒绝（403），可能是私人/受限视频，请稍后再试",
                    status,
                )
            }
        }
        404 -> IwaraException("内容不存在或已被删除（404）", status)
        408 -> IwaraException("请求超时，请稍后再试", status)
        429 -> IwaraException("请求过于频繁，请稍后再试（429）", status)
        in 500..599 -> IwaraException("服务器暂时不可用（$status），请稍后再试", status)
        else -> IwaraException("请求失败（$status）", status)
    }

    fun mapNetwork(error: Throwable): IwaraException =
        IwaraException(
            "网络连接失败，请检查网络或代理",
            0,
            error,
        )

    /** Cloudflare / human-verification pages arrive as HTML instead of JSON. */
    fun looksLikeHtml(contentType: String?, body: String): Boolean {
        if (contentType?.contains("text/html", ignoreCase = true) == true) return true
        val trimmed = body.trimStart()
        return trimmed.startsWith("<")
    }

    /**
     * Best-effort extraction of the upstream `{"message":"errors.x"}` error key.
     * Returns null for unknown keys so that we never leak raw English to the UI.
     */
    fun serverMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val key = try {
            val element = iwaraJson.parseToJsonElement(body)
            ((element as? JsonObject)?.get("message") as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: element.jsonPrimitive.takeIf { it.isString }?.content
        } catch (_: Exception) {
            null
        } ?: return null
        return KNOWN_SERVER_MESSAGES[key] ?: KNOWN_SERVER_MESSAGES[key.lowercase()]
    }
}

/* ------------------------------------------------------------------------- *
 * HTTP transport.
 *
 * One shared transport for every API call:
 *  - browser-like headers are mandatory or Cloudflare rejects the request,
 *  - apiq.iwara.tv is tried first and api.iwara.tv is the fallback,
 *  - fallback happens on network errors and 5xx only,
 *  - responses are validated and mapped to [IwaraException].
 * ------------------------------------------------------------------------- */

internal class IwaraHttp(private val client: OkHttpClient) {

    /**
     * Which Iwara site the browser-like headers impersonate. `x-site`, `Origin`
     * and `Referer` must all agree or the upstream may serve the wrong catalogue
     * (or reject the request). Changed by [DefaultIwaraRepository] on init and
     * on [iwarok.app.core.IwaraRepository.setContentSite].
     */
    @Volatile
    var site: ContentSite = ContentSite.MAIN

    /**
     * The two upstream API hosts behave identically, but their latency can
     * differ a lot depending on the user's network/proxy route (api.iwara.tv is
     * often faster than apiq.iwara.tv on some connections). Start with the
     * configured primary, and switch the preferred host for this session when
     * the first request is noticeably slow. Every few minutes the primary is
     * re-probed by the cooldown logic below.
     */
    private val hostLock = Any()

    @Volatile
    private var preferFallbackHost = false

    @Volatile
    private var hostPreferenceChangedAt = 0L

    private sealed interface Attempt {
        data class Success(val body: String) : Attempt
        data class Failure(val error: IwaraException, val retryable: Boolean) : Attempt
    }

    /** GET/POST/DELETE against the API host(s), returning the raw response body. */
    suspend fun requestJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        baseUrlOverride: String? = null,
    ): String {
        val bases = if (baseUrlOverride != null) {
            listOf(baseUrlOverride.trimEnd('/'))
        } else {
            apiBases()
        }
        var failure: Attempt.Failure? = null
        for ((index, base) in bases.withIndex()) {
            val url = buildApiUrl(base, path, query)
            val startedAt = System.currentTimeMillis()
            val attempt = execute(buildRequest(method, url, bearerToken, extraHeaders, jsonBody))
            when (attempt) {
                is Attempt.Success -> {
                    if (baseUrlOverride == null) {
                        noteHostLatency(index, System.currentTimeMillis() - startedAt)
                    }
                    return attempt.body
                }
                is Attempt.Failure -> {
                    failure = attempt
                    if (!attempt.retryable || index == bases.lastIndex) throw attempt.error
                }
            }
        }
        throw failure?.error ?: IwaraException("网络请求失败，请稍后重试")
    }

    private fun apiBases(): List<String> {
        val now = System.currentTimeMillis()
        if (preferFallbackHost && now - hostPreferenceChangedAt > HOST_RETRY_PRIMARY_MS) {
            synchronized(hostLock) {
                if (preferFallbackHost && now - hostPreferenceChangedAt > HOST_RETRY_PRIMARY_MS) {
                    preferFallbackHost = false
                    hostPreferenceChangedAt = now
                }
            }
        }
        return if (preferFallbackHost) {
            listOf(IwaraConfig.API_FALLBACK, IwaraConfig.API_PRIMARY)
        } else {
            listOf(IwaraConfig.API_PRIMARY, IwaraConfig.API_FALLBACK)
        }
    }

    private fun noteHostLatency(baseIndex: Int, elapsedMs: Long) {
        // index 1 means the primary failed or was skipped, so the fallback is
        // the host that actually works for this user.
        val shouldPreferFallback = baseIndex == 1 || elapsedMs >= SLOW_HOST_THRESHOLD_MS
        if (!shouldPreferFallback) return
        synchronized(hostLock) {
            if (!preferFallbackHost) {
                preferFallbackHost = true
                hostPreferenceChangedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * GET an arbitrary absolute URL (the signed quality-manifest host). No host
     * fallback happens here: the file URL is host specific and the caller decides
     * whether to re-resolve it through the fallback API host.
     */
    suspend fun requestAbsolute(
        url: String,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IwaraException("播放地址无效，请稍后再试")
        val attempt = execute(buildRequest("GET", httpUrl, bearerToken, extraHeaders, null))
        return when (attempt) {
            is Attempt.Success -> attempt.body
            is Attempt.Failure -> throw attempt.error
        }
    }

    private suspend fun execute(request: Request): Attempt = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(Attempt.Failure(IwaraErrors.mapNetwork(e), retryable = true))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val attempt = try {
                    response.use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val contentType = resp.header("Content-Type")
                        when {
                            resp.isSuccessful && IwaraErrors.looksLikeHtml(contentType, body) ->
                                Attempt.Failure(
                                    IwaraException("请求被网页人机校验拦截，请稍后重试或更换网络", resp.code),
                                    retryable = false,
                                )

                            resp.isSuccessful -> Attempt.Success(body)
                            else -> Attempt.Failure(
                                IwaraErrors.mapStatus(resp.code, body),
                                retryable = resp.code >= 500,
                            )
                        }
                    }
                } catch (e: IOException) {
                    Attempt.Failure(IwaraErrors.mapNetwork(e), retryable = true)
                }
                if (continuation.isActive) continuation.resume(attempt)
            }
        })
    }

    private fun buildRequest(
        method: String,
        url: HttpUrl,
        bearerToken: String?,
        extraHeaders: Map<String, String>,
        jsonBody: String?,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", IwaraConfig.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "${site.origin}/")
            .header("Origin", site.origin)
            .header("x-site", site.xSite)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (!bearerToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $bearerToken")
        }
        extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> builder.delete()
            else -> builder.method(method, (jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE))
        }
        return builder.build()
    }

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Requests slower than this make us try the other API host first next time. */
        private const val SLOW_HOST_THRESHOLD_MS = 2_500L

        /** How long to keep the fallback host preferred before re-probing the primary. */
        private const val HOST_RETRY_PRIMARY_MS = 5 * 60 * 1_000L

        fun buildApiUrl(baseUrl: String, path: String, query: Map<String, String?>): HttpUrl {
            val full = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
            val builder = full.toHttpUrlOrNull()?.newBuilder()
                ?: throw IwaraException("请求地址无效，请稍后再试")
            query.forEach { (key, value) ->
                if (value != null) builder.addQueryParameter(key, value)
            }
            return builder.build()
        }
    }
}
