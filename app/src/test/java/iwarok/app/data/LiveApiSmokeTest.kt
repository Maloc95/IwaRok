package iwarok.app.data

import iwarok.app.core.ContentSite
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraUserPage
import iwarok.app.core.IwaraVideoPage
import iwarok.app.core.PlaybackInfo
import iwarok.app.core.QualityPreference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Opt-in LIVE smoke test against the real Iwara API.
 *
 * Skipped by default so offline/CI builds never touch the network or depend on a
 * proxy. Enable it explicitly, e.g. from PowerShell:
 *
 *   $env:IWARA_LIVE_TEST = '1'
 *   $env:IWARA_PROXY = '127.0.0.1:7890'   # optional, this is the default; 'direct' disables it
 *   gradle :app:testDebugUnitTest --tests "*LiveApiSmokeTest*" --no-daemon
 *
 * It verifies the two riskiest real-world paths:
 *  1. `GET /videos?page=0&limit=5&sort=date&rating=all` decodes with our models;
 *  2. detail -> fileUrl -> X-Version -> quality manifest -> playable https source.
 */
class LiveApiSmokeTest {

    private fun liveTestEnabled(): Boolean =
        System.getenv("IWARA_LIVE_TEST") == "1" || System.getProperty("iwara.live") == "true"

    /** OkHttp client for the live run: explicit HTTP proxy by default (CN networks). */
    private fun buildClient(): OkHttpClient {
        val proxySpec = (System.getenv("IWARA_PROXY") ?: System.getProperty("iwara.proxy") ?: "127.0.0.1:7890")
            .trim()
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (proxySpec.isNotBlank() && !proxySpec.equals("direct", ignoreCase = true)) {
            val host = proxySpec.substringBefore(':', "127.0.0.1").ifBlank { "127.0.0.1" }
            val port = proxySpec.substringAfter(':', "7890").toIntOrNull() ?: 7890
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
        }
        return builder.build()
    }

    @Test
    fun `live api feed and playback resolve`() {
        assumeTrue("Live API test disabled; set IWARA_LIVE_TEST=1 to run", liveTestEnabled())
        runBlocking {
            val http = IwaraHttp(buildClient())
            val feedBody = http.requestJson(
                method = "GET",
                path = "/videos",
                query = mapOf(
                    "page" to "0",
                    "limit" to "5",
                    "sort" to "date",
                    "rating" to "all",
                ),
            )
            val page = iwaraJson.decodeFromString<IwaraVideoPage>(feedBody)
            assertTrue("feed count should be > 0", page.count > 0)
            assertEquals("feed should return 5 videos", 5, page.results.size)
            val first = page.results.first()
            assertTrue("first video id should not be blank", first.id.isNotBlank())
            println(
                "[live] feed ok: count=${page.count}, limit=${page.limit}, " +
                    "first=${first.id}, title=${first.title}, file=${first.file?.id}"
            )

            // Playback: try the first few videos, a single dud should not fail the run.
            val resolver = PlaybackResolver(http)
            var resolved: Pair<String, PlaybackInfo>? = null
            var lastError: Throwable? = null
            for (video in page.results.take(3)) {
                try {
                    val info = resolver.resolve(
                        videoId = video.id,
                        quality = QualityPreference.AUTO,
                        refresh = true,
                        accessTokenProvider = { null },
                    )
                    resolved = video.id to info
                    break
                } catch (e: Exception) {
                    lastError = e
                    println("[live] playback failed for ${video.id}: ${e.message}")
                }
            }
            assertNotNull("no playable source resolved (last error: ${lastError?.message})", resolved)
            val (videoId, info) = resolved!!
            assertTrue("playback should expose at least one source", info.sources.isNotEmpty())
            val primary = info.primary
            assertNotNull("playback primary source should not be null", primary)
            assertTrue("primary source url must be https: ${primary!!.url}", primary.url.startsWith("https://"))
            println(
                "[live] playback ok: video=$videoId, primary=${primary.name} (${primary.label}) " +
                    "url=${primary.url.take(90)}, sources=${info.sources.map { it.name }}, " +
                    "duration=${info.durationSeconds}s, ${info.width}x${info.height}, " +
                    "preview=${info.previewUrl?.take(60)}"
            )

            // 3) Probe the selected media URL: ExoPlayer must be able to pull bytes.
            //    A Range request keeps the download tiny while proving it is real media
            //    (not an HTML player page) and that redirects are followed.
            probeMediaBytes(primary.url, ContentSite.MAIN)
        }
    }

    /**
     * Fetches the first 1 KiB of a resolved media URL with the headers the player
     * will send. Asserts a real (non-HTML) partial-content response.
     */
    private fun probeMediaBytes(url: String, site: ContentSite) {
        val mediaRequest = Request.Builder()
            .url(url)
            .header("User-Agent", IwaraConfig.USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", "${site.origin}/")
            .header("Origin", site.origin)
            .header("x-site", site.xSite)
            .header("Range", "bytes=0-1023")
            .get()
            .build()
        buildClient().newCall(mediaRequest).execute().use { media ->
            val contentType = media.header("Content-Type").orEmpty()
            val finalUrl = media.request.url.toString().substringBefore('?')
            println(
                "[live] media probe: status=${media.code}, contentType=$contentType, " +
                    "contentLength=${media.body?.contentLength() ?: -1}, finalUrl=$finalUrl, " +
                    "site=${site.xSite}"
            )
            assertTrue(
                "media url should serve bytes (status ${media.code}): $finalUrl",
                media.code == 200 || media.code == 206,
            )
            assertTrue(
                "media url should not return an HTML page (contentType=$contentType)",
                !contentType.contains("text/html", ignoreCase = true),
            )
        }
    }

    /**
     * Live checks for the 2.0 features: AI catalogue (`x-site: www.iwara.ai`),
     * playback of AI content and creator (`type=users`) search.
     */
    @Test
    fun `live api ai site playback and user search`() {
        assumeTrue("Live API test disabled; set IWARA_LIVE_TEST=1 to run", liveTestEnabled())
        runBlocking {
            val http = IwaraHttp(buildClient())

            // 1) AI catalogue: same hosts, x-site = www.iwara.ai.
            http.site = ContentSite.AI
            val aiBody = http.requestJson(
                method = "GET",
                path = "/videos",
                query = mapOf(
                    "page" to "0",
                    "limit" to "5",
                    "sort" to "date",
                    "rating" to "all",
                ),
            )
            val aiPage = iwaraJson.decodeFromString<IwaraVideoPage>(aiBody)
            assertTrue("AI feed should return videos", aiPage.results.isNotEmpty())
            val aiSiteIds = aiPage.results.map { it.siteId }
            println(
                "[live] ai feed ok: count=${aiPage.count}, results=${aiPage.results.size}, " +
                    "siteIds=$aiSiteIds, first=${aiPage.results.first().id}, " +
                    "title=${aiPage.results.first().title}"
            )
            assertTrue(
                "AI feed should contain AI-site entries (siteId=iwara_ai), got $aiSiteIds",
                aiSiteIds.any { it == "iwara_ai" },
            )

            // 2) AI playback: detail -> fileUrl -> X-Version -> manifest must still work.
            val aiResolver = PlaybackResolver(http)
            var aiPlayback: Pair<String, PlaybackInfo>? = null
            var aiPlaybackError: Throwable? = null
            for (video in aiPage.results.take(3)) {
                try {
                    aiPlayback = video.id to aiResolver.resolve(
                        videoId = video.id,
                        quality = QualityPreference.AUTO,
                        refresh = true,
                        accessTokenProvider = { null },
                    )
                    break
                } catch (e: Exception) {
                    aiPlaybackError = e
                    println("[live] AI playback failed for ${video.id}: ${e.message}")
                }
            }
            assertNotNull("no playable AI source resolved (last error: ${aiPlaybackError?.message})", aiPlayback)
            val (aiVideoId, aiInfo) = aiPlayback!!
            assertTrue("AI playback should expose sources", aiInfo.sources.isNotEmpty())
            assertTrue(
                "AI primary source must be https: ${aiInfo.primary?.url}",
                aiInfo.primary?.url?.startsWith("https://") == true,
            )
            println(
                "[live] ai playback ok: video=$aiVideoId, sources=${aiInfo.sources.map { it.name }}, " +
                    "duration=${aiInfo.durationSeconds}s"
            )
            // The AI-signed source URL must actually serve video bytes too.
            probeMediaBytes(aiInfo.primary!!.url, ContentSite.AI)

            // 3) Creator search (guest): /search?type=users.
            http.site = ContentSite.MAIN
            val mainBody = http.requestJson(
                method = "GET",
                path = "/videos",
                query = mapOf("page" to "0", "limit" to "5", "sort" to "date", "rating" to "all"),
            )
            val mainPage = iwaraJson.decodeFromString<IwaraVideoPage>(mainBody)
            val author = mainPage.results.firstOrNull()?.user
            val query = author?.username?.takeIf { it.isNotBlank() } ?: "iwara"
            val usersBody = http.requestJson(
                method = "GET",
                path = "/search",
                query = mapOf(
                    "query" to query,
                    "type" to "users",
                    "page" to "0",
                    "limit" to "5",
                ),
            )
            val userPage = decodeIwaraUserPage(usersBody, requestedPage = 0)
            assertTrue("user search should return creators for '$query'", userPage.results.isNotEmpty())
            assertTrue(
                "user search results must contain usernames",
                userPage.results.all { it.username.isNotBlank() },
            )
            val matched = author != null && userPage.results.any { it.id == author.id }
            println(
                "[live] user search ok: query=$query, count=${userPage.count}, " +
                    "type=${userPage.type}, results=${userPage.results.map { it.username }}, " +
                    "exactAuthorMatch=$matched"
            )
        }
    }
}
