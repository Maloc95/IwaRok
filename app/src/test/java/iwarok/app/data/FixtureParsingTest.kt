package iwarok.app.data

import iwarok.app.core.CommentsPage
import iwarok.app.core.IwaraCommentPage
import iwarok.app.core.IwaraVideo
import iwarok.app.core.IwaraVideoPage
import iwarok.app.core.QualityPreference
import iwarok.app.core.VideoPage
import iwarok.app.core.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the sanitised LIVE-API fixtures delivered by api-researcher in
 * `app/src/test/resources/`. Keeps our kotlinx.serialization models and playback
 * logic honest against the real response shapes without any network access.
 */
class FixtureParsingTest {

    private fun readFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: throw AssertionError("missing test fixture: $name (expected in app/src/test/resources/)")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `videos_page fixture decodes into a paged feed`() {
        val dto = iwaraJson.decodeFromString<IwaraVideoPage>(readFixture("videos_page.json"))
        assertEquals(20, dto.limit)
        assertEquals(0, dto.page)
        assertTrue("fixture should contain videos", dto.results.isNotEmpty())

        val first = dto.results.first()
        assertTrue(first.id.isNotBlank())
        assertFalse(first.title.isNullOrBlank())
        assertTrue((first.file?.duration ?: 0) > 0)
        assertNotNull(first.user)

        // Mirrors DefaultIwaraRepository.decodeVideoPage().
        val page = VideoPage(
            items = dto.results,
            pageIndex = dto.page,
            limit = dto.limit,
            totalCount = dto.count,
        )
        assertEquals(0, page.pageIndex)
        assertEquals(dto.count, page.totalCount)
        assertTrue("count 21 with limit 20 must allow a second page", page.hasMore)
    }

    @Test
    fun `video_detail fixture decodes with a signed file url`() {
        val video = iwaraJson.decodeFromString<IwaraVideo>(readFixture("video_detail.json"))
        assertTrue(video.id.isNotBlank())
        assertFalse(video.title.isNullOrBlank())
        assertNotNull(video.file)
        assertEquals("video/mp4", video.file?.mime)

        val fileUrl = video.fileUrl
        assertNotNull("detail response must carry fileUrl", fileUrl)
        val signed = IwaraPlayback.parseSignedFile(fileUrl)
        assertNotNull("fileUrl must expose uuid + expires", signed)
        assertEquals("11111111-1111-4111-8111-111111111111", signed!!.uuid)
        assertEquals("1794078308898", signed.expires)
        assertEquals(
            // Independently computed SHA1(uuid_expires_DEFAULT_SALT).
            "c3bf3423dfa8d2548a5957ca283d611d9807f228",
            IwaraPlayback.xVersion(signed.uuid, signed.expires),
        )
    }

    @Test
    fun `video_sources fixture decodes and selects a playable source`() {
        val sources = iwaraJson.decodeFromString<List<VideoSource>>(readFixture("video_sources.json"))
        assertTrue("fixture should contain sources", sources.size >= 3)
        assertTrue(sources.any { it.name == "Source" })
        assertTrue(sources.any { it.name == "preview" })

        val selected = IwaraPlayback.selectSources(sources, QualityPreference.AUTO)
        assertTrue(selected.isNotEmpty())
        // AUTO ladder: 720 -> 540 -> 480 -> 360 -> Source; this fixture has 540.
        assertEquals("540", selected.first().name)
        assertEquals("540P", selected.first().label)
        assertTrue(selected.first().url.startsWith("https://"))
        assertEquals("video/mp4", selected.first().mime)
        assertTrue("preview must not be playable", selected.none { it.name == "preview" })
        assertTrue(selected.all { it.url.startsWith("https://") })

        val preview = IwaraPlayback.previewUrl(sources)
        assertNotNull(preview)
        assertTrue(preview!!.startsWith("https://"))

        // 1080P is absent from the fixture: fall back to Source.
        assertEquals("Source", IwaraPlayback.selectSources(sources, QualityPreference.P1080).first().name)
    }

    @Test
    fun `comments_page fixture decodes into a paged comment list`() {
        val dto = iwaraJson.decodeFromString<IwaraCommentPage>(readFixture("comments_page.json"))
        assertEquals(3, dto.count)
        assertEquals(20, dto.limit)
        assertEquals(3, dto.results.size)
        assertEquals(0, dto.pendingCount)

        val first = dto.results.first()
        assertTrue(first.id.isNotBlank())
        assertFalse(first.body.isNullOrBlank())
        assertNotNull(first.user)
        assertTrue(first.user?.displayName?.isNotBlank() == true)
        assertEquals("SampleVideo001", first.videoId)

        val page = CommentsPage(
            items = dto.results,
            pageIndex = dto.page,
            limit = dto.limit,
            totalCount = dto.count,
        )
        assertEquals(3, page.totalCount)
        assertFalse("all comments fit on one page", page.hasMore)
    }
}
