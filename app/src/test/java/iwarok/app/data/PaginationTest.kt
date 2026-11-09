package iwarok.app.data

import iwarok.app.core.CommentsPage
import iwarok.app.core.IwaraComment
import iwarok.app.core.IwaraVideo
import iwarok.app.core.VideoPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pagination math shared by the feed, search, related and comment lists. */
class PaginationTest {

    private fun videos(count: Int): List<IwaraVideo> = (1..count).map { IwaraVideo(id = "v$it") }

    private fun comments(count: Int): List<IwaraComment> = (1..count).map { IwaraComment(id = "c$it") }

    @Test
    fun `video page with a full window and remaining rows has more`() {
        val page = VideoPage(items = videos(20), pageIndex = 0, limit = 20, totalCount = 45)
        assertTrue(page.hasMore)
    }

    @Test
    fun `video page that reaches the total does not have more`() {
        val page = VideoPage(items = videos(5), pageIndex = 2, limit = 20, totalCount = 45)
        assertFalse(page.hasMore)
    }

    @Test
    fun `exactly one full window means no more pages`() {
        val page = VideoPage(items = videos(20), pageIndex = 0, limit = 20, totalCount = 20)
        assertFalse(page.hasMore)
    }

    @Test
    fun `empty video page never has more`() {
        val page = VideoPage(items = emptyList(), pageIndex = 0, limit = 20, totalCount = 100)
        assertFalse(page.hasMore)
    }

    @Test
    fun `comments page follows the same pagination math`() {
        assertTrue(
            CommentsPage(items = comments(20), pageIndex = 1, limit = 20, totalCount = 75).hasMore
        )
        assertFalse(
            CommentsPage(items = comments(3), pageIndex = 3, limit = 20, totalCount = 75).hasMore
        )
        assertFalse(
            CommentsPage(items = emptyList(), pageIndex = 0, limit = 20, totalCount = 0).hasMore
        )
    }
}
