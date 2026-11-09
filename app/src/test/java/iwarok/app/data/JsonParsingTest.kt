package iwarok.app.data

import iwarok.app.core.IwaraCommentPage
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraUser
import iwarok.app.core.IwaraVideo
import iwarok.app.core.IwaraVideoPage
import iwarok.app.core.QualityPreference
import iwarok.app.core.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON fixture parsing with inline payloads that mirror the real Iwara API.
 * Everything is decoded through the same [iwaraJson] instance the repository uses.
 */
class JsonParsingTest {

    /* ------------------------------ videos ------------------------------ */

    private val videosPageJson = """
        {
          "count": 123,
          "limit": 20,
          "page": 0,
          "results": [
            {
              "id": "vid123",
              "slug": "sample-video",
              "title": "Sample Video",
              "body": "A description",
              "status": "public",
              "rating": "ecchi",
              "private": false,
              "unlisted": false,
              "thumbnail": 3,
              "liked": false,
              "numLikes": 42,
              "numViews": 1000,
              "numComments": 7,
              "file": {
                "id": "file123",
                "type": "video",
                "name": "sample.mp4",
                "mime": "video/mp4",
                "size": 123456,
                "duration": 125,
                "width": 1920,
                "height": 1080,
                "numThumbnails": 10
              },
              "user": {"id": "user123", "username": "author", "name": "Author Name"},
              "tags": [{"id": "tag_1", "type": "new"}],
              "createdAt": "2025-01-02T03:04:05.000Z",
              "updatedAt": "2025-01-03T03:04:05.000Z"
            },
            {
              "id": "vid456",
              "title": "Second Video",
              "rating": "general",
              "tags": [],
              "user": {"id": "user456", "username": "author2"}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `videos page payload decodes`() {
        val page = iwaraJson.decodeFromString<IwaraVideoPage>(videosPageJson)
        assertEquals(123, page.count)
        assertEquals(20, page.limit)
        assertEquals(0, page.page)
        assertEquals(2, page.results.size)

        val first = page.results[0]
        assertEquals("vid123", first.id)
        assertEquals("Sample Video", first.title)
        assertEquals("ecchi", first.rating)
        assertEquals(false, first.liked)
        assertEquals(42, first.numLikes)
        assertEquals(125, first.file?.duration)
        assertEquals(1920, first.file?.width)
        assertEquals(1080, first.file?.height)
        assertEquals("Author Name", first.user?.displayName)
        assertEquals("tag_1", first.tags.first().id)

        val second = page.results[1]
        assertEquals("author2", second.user?.displayName)
        assertNull(second.file)
        assertTrue(second.tags.isEmpty())
    }

    /* ---------------------------- video detail ---------------------------- */

    private val videoDetailJson = """
        {
          "id": "vid123",
          "title": "Sample Video",
          "rating": "ecchi",
          "fileUrl": "https://filesq.iwara.tv/file/6f4a1b2c-3d4e-5f60-7182-93a4b5c6d7e8?expires=1762500000000&hash=abc123",
          "file": {"id": "file123", "duration": 125, "width": 1920, "height": 1080},
          "user": {"id": "user123", "username": "author", "name": "Author Name"}
        }
    """.trimIndent()

    @Test
    fun `video detail payload decodes and yields a signed manifest request`() {
        val video = iwaraJson.decodeFromString<IwaraVideo>(videoDetailJson)
        assertEquals("vid123", video.id)
        assertEquals("file123", video.file?.id)
        assertNotNull(video.fileUrl)

        val signed = IwaraPlayback.parseSignedFile(video.fileUrl)
        assertNotNull(signed)
        assertEquals("6f4a1b2c-3d4e-5f60-7182-93a4b5c6d7e8", signed!!.uuid)
        assertEquals("1762500000000", signed.expires)
        assertEquals(
            "1e853645757fd3cad8480b294127d8201895752c",
            IwaraPlayback.xVersion(signed.uuid, signed.expires),
        )
    }

    /* ------------------------------ comments ------------------------------ */

    private val commentsPageJson = """
        {
          "count": 5,
          "limit": 20,
          "page": 0,
          "pendingCount": 1,
          "results": [
            {
              "id": "comment1",
              "body": "Nice video!",
              "numReplies": 2,
              "approved": true,
              "createdAt": "2025-01-02T03:04:05.000Z",
              "user": {"id": "user1", "username": "commenter"},
              "videoId": "vid123"
            },
            {
              "id": "comment2",
              "body": "Second comment",
              "user": {"id": "user2", "username": "commenter2"},
              "videoId": "vid123"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `comments page payload decodes`() {
        val page = iwaraJson.decodeFromString<IwaraCommentPage>(commentsPageJson)
        assertEquals(5, page.count)
        assertEquals(1, page.pendingCount)
        assertEquals(2, page.results.size)
        assertEquals("Nice video!", page.results[0].body)
        assertEquals(2, page.results[0].numReplies)
        assertEquals("commenter", page.results[0].user?.displayName)
        assertEquals("vid123", page.results[0].videoId)
    }

    /* ---------------------------- quality manifest ---------------------------- */

    private val manifestJson = """
        [
          {
            "name": "Source",
            "type": "video/mp4",
            "src": {
              "view": "//kafka.iwara.tv/source.mp4",
              "download": "//kafka.iwara.tv/source.mp4"
            }
          },
          {
            "name": "720",
            "type": "video/mp4",
            "src": {"view": "//kafka.iwara.tv/720.mp4"}
          },
          {
            "name": "preview",
            "type": "video/mp4",
            "src": {"view": "//kafka.iwara.tv/preview.mp4"}
          }
        ]
    """.trimIndent()

    @Test
    fun `quality manifest decodes and selects a source`() {
        val sources = iwaraJson.decodeFromString<List<VideoSource>>(manifestJson)
        assertEquals(3, sources.size)
        assertEquals("Source", sources[0].name)
        assertEquals("//kafka.iwara.tv/source.mp4", sources[0].src?.view)

        val selected = IwaraPlayback.selectSources(sources, QualityPreference.AUTO)
        assertEquals("720", selected.first().name)
        assertEquals("https://kafka.iwara.tv/720.mp4", selected.first().url)
        assertEquals("video/mp4", selected.first().mime)
    }

    @Test
    fun `manifest entries with null src are tolerated`() {
        val sources = iwaraJson.decodeFromString<List<VideoSource>>(
            """[{"name":"360","type":"video/mp4","src":null}]"""
        )
        assertEquals(1, sources.size)
        assertNull(sources[0].src)
        assertTrue(IwaraPlayback.selectSources(sources, QualityPreference.AUTO).isEmpty())
    }

    /* ------------------------------- auth -------------------------------- */

    @Test
    fun `login and token payloads decode`() {
        val login = iwaraJson.decodeFromString<LoginResponse>(
            """{"token":"refresh-token-value","unknown":1}"""
        )
        assertEquals("refresh-token-value", login.token)

        val token = iwaraJson.decodeFromString<TokenResponse>(
            """{"accessToken":"access-token-value","expiresIn":300}"""
        )
        assertEquals("access-token-value", token.accessToken)
        assertEquals(300L, token.expiresIn)

        val minimalToken = iwaraJson.decodeFromString<TokenResponse>("""{"accessToken":"x"}""")
        assertEquals("x", minimalToken.accessToken)
        assertNull(minimalToken.expiresIn)
    }

    @Test
    fun `user envelope decodes`() {
        val envelope = iwaraJson.decodeFromString<UserEnvelope>(
            """
            {"user":{"id":"user123","username":"tester","name":"Tester",
             "avatar":{"id":"avatar1","name":"avatar.jpg"},"premium":true}}
            """.trimIndent()
        )
        val user: IwaraUser = envelope.user
        assertEquals("user123", user.id)
        assertEquals("Tester", user.displayName)
        assertEquals("avatar1", user.avatar?.id)
        assertEquals(true, user.premium)
    }

    @Test
    fun `json config ignores unknown keys and coerces bad nulls`() {
        // Unknown keys are ignored and nulls in non-nullable fields with a default
        // fall back to that default thanks to coerceInputValues.
        val video = iwaraJson.decodeFromString<IwaraVideo>(
            """{"id":"v1","title":null,"private":null,"tags":null,"neverSeenBefore":{"x":1}}"""
        )
        assertEquals("v1", video.id)
        assertNull(video.title)
        assertEquals(false, video.isPrivate)
        assertTrue(video.tags.isEmpty())
    }

    @Test
    fun `xVersion salt constant matches the shipped value`() {
        assertEquals("mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN", IwaraConfig.DEFAULT_SALT)
    }
}
