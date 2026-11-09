package iwarok.app.data

import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraException
import iwarok.app.core.IwaraVideo
import iwarok.app.core.PlaybackInfo
import iwarok.app.core.PlaybackSource
import iwarok.app.core.QualityPreference
import iwarok.app.core.VideoSource
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/* ------------------------------------------------------------------------- *
 * Pure playback logic: everything here is deterministic and JVM-unit-testable.
 * ------------------------------------------------------------------------- */

internal object IwaraPlayback {

    /** A signed manifest request: `.../file/<uuid>?expires=<ms>&hash=...`. */
    data class SignedFile(val uuid: String, val expires: String)

    /** AUTO preference: 720 -> 540 -> 480 -> 360 -> Source -> highest remaining. */
    private val AUTO_ORDER = listOf("720", "540", "480", "360", "Source")

    fun parseSignedFile(fileUrl: String?): SignedFile? {
        if (fileUrl.isNullOrBlank()) return null
        val url = fileUrl.trim().toHttpUrlOrNull() ?: return null
        val uuid = url.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val expires = url.queryParameter("expires")?.takeIf { it.isNotBlank() } ?: return null
        return SignedFile(uuid, expires)
    }

    /** `X-Version = SHA1("<uuid>_<expires>_<salt>")`, lowercase hex. */
    fun xVersion(uuid: String, expires: String, salt: String = IwaraConfig.DEFAULT_SALT): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest("${uuid}_${expires}_$salt".toByteArray(Charsets.UTF_8))
        return buildString(hash.size * 2) {
            hash.forEach { byte -> append(HEX[(byte.toInt() shr 4) and 0x0f]).append(HEX[byte.toInt() and 0x0f]) }
        }
    }

    /** Normalises protocol-relative (`//host/x`) and plain-http URLs to https. */
    fun normalizeUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substring(7)}"
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("://") -> trimmed
            else -> "https://${trimmed.trimStart('/')}"
        }
    }

    /** Human readable quality label: Source -> 原画, 720 -> 720P, ... */
    fun qualityLabel(name: String): String {
        val trimmed = name.trim()
        if (trimmed.equals("Source", ignoreCase = true)) return "原画"
        if (trimmed.equals("preview", ignoreCase = true)) return "预览"
        val numeric = trimmed.toIntOrNull()
            ?: trimmed.removeSuffix("p").removeSuffix("P").toIntOrNull()
        return if (numeric != null) "${numeric}P" else trimmed
    }

    /**
     * Converts the manifest into an ordered [PlaybackSource] list where the first
     * element is the source selected by [preference]. Preview entries are kept
     * out of the playable list (they are exposed as [PlaybackInfo.previewUrl]).
     */
    fun selectSources(
        sources: List<VideoSource>,
        preference: QualityPreference,
    ): List<PlaybackSource> {
        val usable = sources.mapNotNull { source ->
            val name = source.name.trim()
            if (name.isEmpty() || name.equals("preview", ignoreCase = true)) return@mapNotNull null
            val url = normalizeUrl(source.src?.view) ?: return@mapNotNull null
            PlaybackSource(
                name = name,
                label = qualityLabel(name),
                url = url,
                mime = source.type?.takeIf { it.isNotBlank() } ?: "video/mp4",
            )
        }
        if (usable.isEmpty()) return emptyList()
        val primary = when (preference) {
            QualityPreference.AUTO -> pickAuto(usable)
            else -> pickExplicit(usable, preference)
        }
        // Fallback order: qualities below the selected one first (closest
        // lower first), then higher qualities, with Source last. Underrun
        // recovery therefore moves down one step instead of jumping around.
        val primaryNumeric = numericQuality(primary.name)
        val rest = usable.filter { it !== primary }
            .sortedWith(
                compareBy<PlaybackSource> {
                    val numeric = numericQuality(it.name)
                    when {
                        it.name.equals("Source", ignoreCase = true) -> 3
                        primaryNumeric == null -> 0
                        numeric == null -> 2
                        numeric <= primaryNumeric -> 0
                        else -> 2
                    }
                }.thenByDescending { numericQuality(it.name) ?: Int.MIN_VALUE },
            )
        return listOfNotNull(primary) + rest
    }

    /** Preview (low resolution teaser) URL from the manifest, if present. */
    fun previewUrl(sources: List<VideoSource>): String? =
        sources.firstOrNull { it.name.equals("preview", ignoreCase = true) }
            ?.src?.view
            ?.let(::normalizeUrl)

    private fun pickAuto(sources: List<PlaybackSource>): PlaybackSource {
        AUTO_ORDER.forEach { target ->
            sources.firstOrNull { it.name.equals(target, ignoreCase = true) }?.let { return it }
        }
        return sources.maxByOrNull { numericQuality(it.name) ?: Int.MIN_VALUE } ?: sources.first()
    }

    private fun pickExplicit(
        sources: List<PlaybackSource>,
        preference: QualityPreference,
    ): PlaybackSource {
        val target = when (preference) {
            QualityPreference.AUTO -> "720"
            QualityPreference.SOURCE -> "Source"
            QualityPreference.P1080 -> "1080"
            QualityPreference.P720 -> "720"
            QualityPreference.P540 -> "540"
            QualityPreference.P360 -> "360"
        }
        return sources.firstOrNull { it.name.equals(target, ignoreCase = true) }
            ?: sources.firstOrNull { it.name.equals("Source", ignoreCase = true) }
            ?: sources.first()
    }

    private fun numericQuality(name: String): Int? =
        name.toIntOrNull() ?: name.removeSuffix("p").removeSuffix("P").toIntOrNull()

    private fun fallbackRank(name: String): Int = when {
        name.equals("Source", ignoreCase = true) -> Int.MAX_VALUE
        else -> numericQuality(name) ?: Int.MIN_VALUE
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

/* ------------------------------------------------------------------------- *
 * Network playback resolution with a short in-memory cache.
 * ------------------------------------------------------------------------- */

internal class PlaybackResolver(
    private val http: IwaraHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class CachedManifest(
        val video: IwaraVideo,
        val sources: List<VideoSource>,
        val fetchedAt: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedManifest>()

    suspend fun resolve(
        videoId: String,
        quality: QualityPreference,
        refresh: Boolean,
        accessTokenProvider: suspend () -> String?,
    ): PlaybackInfo {
        val token = accessTokenProvider()
        val cached = if (refresh) {
            null
        } else {
            cache[videoId]?.takeIf { clock() - it.fetchedAt < CACHE_TTL_MS }
        }
        val manifest = cached ?: fetchManifest(videoId, token).also {
            cache[videoId] = it
            trimCache()
        }
        val ordered = IwaraPlayback.selectSources(manifest.sources, quality)
        if (ordered.isEmpty()) {
            throw IwaraException("无法解析播放地址，请稍后再试")
        }
        val file = manifest.video.file
        return PlaybackInfo(
            sources = ordered,
            previewUrl = IwaraPlayback.previewUrl(manifest.sources),
            durationSeconds = file?.duration?.coerceAtLeast(0) ?: 0,
            width = file?.width ?: 0,
            height = file?.height ?: 0,
            fileId = file?.id.orEmpty(),
        )
    }

    fun clearCache() = cache.clear()

    /**
     * Resolves the quality manifest. When the primary API host fails or returns a
     * manifest without an original-quality ("Source") entry, the fallback API host
     * is tried once more (it can return a differently signed file URL).
     */
    private suspend fun fetchManifest(videoId: String, token: String?): CachedManifest {
        var firstError: IwaraException? = null
        val first = try {
            fetchManifestOnce(videoId, token, baseUrlOverride = null)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IwaraException) {
            firstError = e
            null
        }
        if (first != null && first.sources.any { it.name.equals("Source", ignoreCase = true) }) {
            return first
        }
        val second = try {
            fetchManifestOnce(videoId, token, baseUrlOverride = IwaraConfig.API_FALLBACK)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IwaraException) {
            firstError = firstError ?: e
            null
        }
        return second ?: first ?: throw (
            firstError ?: IwaraException("无法获取播放地址，请稍后再试")
            )
    }

    private suspend fun fetchManifestOnce(
        videoId: String,
        token: String?,
        baseUrlOverride: String?,
    ): CachedManifest {
        val video = fetchVideoDetail(videoId, token, baseUrlOverride)
        val fileUrl = video.fileUrl?.takeIf { it.isNotBlank() }
            ?: throw IwaraException(
                if (!video.embedUrl.isNullOrBlank()) {
                    "该作品是外部嵌入视频（如 YouTube），App 内无法播放，请在浏览器打开"
                } else {
                    "该作品没有可播放的视频（可能是图片作品），请在浏览器打开"
                },
                404,
            )
        val signedFile = IwaraPlayback.parseSignedFile(fileUrl)
            ?: throw IwaraException("播放地址格式异常，请稍后再试")
        val version = IwaraPlayback.xVersion(signedFile.uuid, signedFile.expires)
        val raw = http.requestAbsolute(
            url = fileUrl,
            bearerToken = token,
            extraHeaders = mapOf("X-Version" to version),
        )
        val sources = try {
            iwaraJson.decodeFromString<List<VideoSource>>(raw)
        } catch (e: Exception) {
            throw IwaraException("视频源解析失败，请稍后再试", 0, e)
        }
        if (sources.isEmpty()) throw IwaraException("视频源为空，请稍后再试")
        return CachedManifest(video, sources, clock())
    }

    private suspend fun fetchVideoDetail(
        videoId: String,
        token: String?,
        baseUrlOverride: String?,
    ): IwaraVideo {
        val raw = http.requestJson(
            method = "GET",
            path = "/video/$videoId",
            bearerToken = token,
            baseUrlOverride = baseUrlOverride,
        )
        return try {
            iwaraJson.decodeFromString<IwaraVideo>(raw)
        } catch (e: Exception) {
            throw IwaraException("视频信息解析失败，请稍后再试", 0, e)
        }
    }

    private fun trimCache() {
        val now = clock()
        cache.entries.removeIf { now - it.value.fetchedAt > CACHE_TTL_MS }
        if (cache.size > MAX_CACHE_ENTRIES) {
            cache.entries
                .sortedBy { it.value.fetchedAt }
                .take(cache.size - MAX_CACHE_ENTRIES)
                .forEach { cache.remove(it.key) }
        }
    }

    internal companion object {
        const val CACHE_TTL_MS: Long = 5 * 60 * 1000L
        private const val MAX_CACHE_ENTRIES = 32
    }
}
