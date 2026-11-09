package iwarok.app.data

import iwarok.app.core.AppSettings
import iwarok.app.core.ContentSite
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraException
import iwarok.app.core.IwaraUser
import iwarok.app.core.IwaraUserPage
import iwarok.app.core.QualityPreference
import iwarok.app.core.RatingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0 contract additions: content site switching, recommended sort and user search payloads. */
class ContentSiteAndUserSearchTest {

    @Test
    fun `content sites expose the correct x-site and origin headers`() {
        assertEquals("www.iwara.tv", ContentSite.MAIN.xSite)
        assertEquals("https://www.iwara.tv", ContentSite.MAIN.origin)
        assertEquals("www.iwara.ai", ContentSite.AI.xSite)
        assertEquals("https://www.iwara.ai", ContentSite.AI.origin)
        assertEquals(2, ContentSite.entries.size)
    }

    @Test
    fun `recommended sort is first and is the default`() {
        assertEquals("recommended", FeedSort.RECOMMENDED.apiValue)
        assertEquals("发现", FeedSort.RECOMMENDED.label)
        assertEquals(FeedSort.RECOMMENDED, FeedSort.entries.first())

        val defaults = AppSettings()
        assertEquals(FeedSort.RECOMMENDED, defaults.sort)
        assertEquals(RatingFilter.ALL, defaults.rating)
        assertEquals(QualityPreference.AUTO, defaults.quality)
        assertEquals(ContentSite.MAIN, defaults.contentSite)
    }

    @Test
    fun `user page json decodes the documented shape`() {
        val body = """
            {
              "page": 0,
              "count": 2,
              "limit": 20,
              "type": "users",
              "results": [
                {"id": "u1", "username": "alice", "name": "Alice",
                 "avatar": {"id": "a1", "name": "alice.png"}, "premium": true},
                {"id": "u2", "username": "bob", "name": null}
              ]
            }
        """.trimIndent()
        val page = iwaraJson.decodeFromString<IwaraUserPage>(body)
        assertEquals(2, page.count)
        assertEquals(20, page.limit)
        assertEquals(0, page.page)
        assertEquals("users", page.type)
        assertEquals(2, page.results.size)
        assertEquals("alice", page.results[0].username)
        assertEquals("Alice", page.results[0].displayName)
        assertEquals("a1", page.results[0].avatar?.id)
        // displayName falls back to username when "name" is null.
        assertEquals("bob", page.results[1].displayName)
    }

    @Test
    fun `decodeIwaraUserPage accepts the documented object`() {
        val page = decodeIwaraUserPage(
            """{"count":1,"limit":20,"page":2,"type":"users","results":[{"id":"u1","username":"carol"}]}""",
            requestedPage = 2,
        )
        assertEquals(1, page.count)
        assertEquals(2, page.page)
        assertEquals("users", page.type)
        assertEquals("carol", page.results.single().username)
    }

    @Test
    fun `decodeIwaraUserPage tolerates a bare array and maps the requested page`() {
        val page = decodeIwaraUserPage(
            """[{"id":"u1","username":"a"},{"id":"u2","username":"b"}]""",
            requestedPage = 3,
        )
        assertEquals(3, page.page)
        assertEquals(2, page.count)
        assertEquals(2, page.limit)
        assertEquals("users", page.type)
        assertEquals(2, page.results.size)
    }

    @Test
    fun `decodeIwaraUserPage fails with a chinese message on garbage`() {
        val error = try {
            decodeIwaraUserPage("not json", requestedPage = 0)
            null
        } catch (e: IwaraException) {
            e
        }
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("用户"))
    }

    @Test
    fun `user page serialization round-trips`() {
        val page = IwaraUserPage(
            count = 1,
            limit = 20,
            page = 0,
            results = listOf(IwaraUser(id = "u1", username = "tester", name = "Tester")),
            type = "users",
        )
        val json = iwaraJson.encodeToString(IwaraUserPage.serializer(), page)
        assertEquals(page, iwaraJson.decodeFromString<IwaraUserPage>(json))
    }
}
