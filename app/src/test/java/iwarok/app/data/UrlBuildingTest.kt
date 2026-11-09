package iwarok.app.data

import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** URL construction for the API host(s). */
class UrlBuildingTest {

    @Test
    fun `buildApiUrl joins base, path and query`() {
        val url = IwaraHttp.buildApiUrl(
            IwaraConfig.API_PRIMARY,
            "/videos",
            mapOf(
                "page" to "0",
                "limit" to "20",
                "sort" to "date",
                "rating" to "all",
            ),
        )
        assertEquals("https", url.scheme)
        assertEquals("apiq.iwara.tv", url.host)
        assertEquals("/videos", url.encodedPath)
        assertEquals("0", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("date", url.queryParameter("sort"))
        assertEquals("all", url.queryParameter("rating"))
    }

    @Test
    fun `buildApiUrl uses the fallback host when asked`() {
        val url = IwaraHttp.buildApiUrl(IwaraConfig.API_FALLBACK, "video/abc", emptyMap())
        assertEquals("api.iwara.tv", url.host)
        assertEquals("/video/abc", url.encodedPath)
        assertNull(url.query)
    }

    @Test
    fun `buildApiUrl tolerates duplicate slashes`() {
        val url = IwaraHttp.buildApiUrl("https://apiq.iwara.tv/", "/video/x/", emptyMap())
        assertEquals("/video/x/", url.encodedPath)
        assertEquals("apiq.iwara.tv", url.host)
    }

    @Test
    fun `buildApiUrl skips null query values`() {
        val url = IwaraHttp.buildApiUrl(
            IwaraConfig.API_PRIMARY,
            "/videos",
            mapOf("page" to "0", "rating" to null),
        )
        assertEquals("page=0", url.encodedQuery)
        assertNull(url.queryParameter("rating"))
    }

    @Test
    fun `buildApiUrl percent-encodes search queries`() {
        val url = IwaraHttp.buildApiUrl(
            IwaraConfig.API_PRIMARY,
            "/search",
            mapOf("query" to "测试 video", "type" to "videos"),
        )
        // The value is encoded on the wire but round-trips through queryParameter.
        assertEquals("测试 video", url.queryParameter("query"))
        assertEquals("videos", url.queryParameter("type"))
    }

    @Test
    fun `buildApiUrl rejects invalid base urls`() {
        assertThrows(IwaraException::class.java) {
            IwaraHttp.buildApiUrl("not a url", "/videos", emptyMap())
        }
    }
}
