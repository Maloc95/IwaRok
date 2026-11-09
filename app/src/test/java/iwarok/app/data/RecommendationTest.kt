package iwarok.app.data

import iwarok.app.core.IwaraFile
import iwarok.app.core.IwaraVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Pure logic of the local "鎺ㄨ崘" feed: pooling, dedupe, shuffle, seen suppression, paging. */
class RecommendationTest {

    private fun video(id: String, title: String = "title-$id"): IwaraVideo =
        IwaraVideo(id = id, title = title, status = "active", file = IwaraFile(id = "file-$id"))
    /* --------------------------- pool sampling --------------------------- */

    @Test
    fun `recommendation pools match the researched page ranges`() {
        assertEquals(
            listOf("trending", "popularity", "date", "views", "likes"),
            RecommendedMixer.SOURCES.map { it.apiValue },
        )
        assertEquals(
            listOf(0..9, 0..9, 0..7, 0..7, 0..7),
            RecommendedMixer.SOURCES.map { it.pageRange },
        )
    }

    @Test
    fun `randomPage stays inside its range and actually varies`() {
        val random = Random(1234)
        RecommendedMixer.SOURCES.forEach { source ->
            val pages = (1..200).map { RecommendedMixer.randomPage(source, random) }
            assertTrue("pages $pages out of ${source.pageRange}", pages.all { it in source.pageRange })
            assertTrue("random pages should vary for ${source.apiValue}", pages.toSet().size > 1)
        }
    }

    /* ----------------------------- merging ----------------------------- */

    @Test
    fun `candidate depth grows with seen history up to a cap`() {
        val source = RecommendedMixer.SOURCES.first()
        assertEquals(source.pageRange, RecommendedMixer.effectivePageRange(source, 0))
        assertEquals(0..10, RecommendedMixer.effectivePageRange(source, 300))
        assertEquals(
            0..12,
            RecommendedMixer.effectivePageRange(source, 900),
        )
        assertEquals(
            0..(source.pageRange.last + RecommendedMixer.MAX_EXTRA_DEPTH),
            RecommendedMixer.effectivePageRange(source, 100_000),
        )
    }

    @Test
    fun `merge deduplicates ids across pools and skips blank ids`() {
        val pools = listOf(
            listOf(video("a"), video("b"), IwaraVideo(id = "")),
            listOf(video("b"), video("c")),
            emptyList(),
        )
        val merged = RecommendedMixer.mergeAndShuffle(pools, Random(42))
        assertEquals(listOf("a", "b", "c").toSet(), merged.map { it.id }.toSet())
        assertEquals(3, merged.size)
    }

    @Test
    fun `merge shuffle is deterministic for the same seed`() {
        val pools = (1..20).map { listOf(video("v$it")) }
        val first = RecommendedMixer.mergeAndShuffle(pools, Random(7)).map { it.id }
        val second = RecommendedMixer.mergeAndShuffle(pools, Random(7)).map { it.id }
        assertEquals(first, second)
        // Merge really shuffles: at least one adjacent pair is out of source order.
        val outOfOrder = first.zipWithNext().any { (a, b) -> a.removePrefix("v").toInt() > b.removePrefix("v").toInt() }
        assertTrue("shuffled order should not be the insertion order: $first", outOfOrder)
    }

    /* --------------------------- seen filtering --------------------------- */

    @Test
    fun `selectPage prefers unseen ids when enough are available`() {
        val candidates = (1..30).map { video("v$it") }
        val seen = (1..5).map { "v$it" }.toSet()
        val (selected, newIds) = RecommendedMixer.selectPage(candidates, seen, limit = 20)
        assertEquals(20, selected.size)
        assertEquals(20, newIds.size)
        assertTrue("no seen id should be picked: ${selected.map { it.id }}", selected.none { it.id in seen })
        assertEquals(selected.map { it.id }, newIds)
    }

    @Test
    fun `selectPage falls back to seen ids to fill a short page`() {
        val candidates = (1..30).map { video("v$it") }
        val seen = (1..25).map { "v$it" }.toSet()
        val (selected, newIds) = RecommendedMixer.selectPage(candidates, seen, limit = 20)
        assertEquals(20, selected.size)
        assertEquals(20, selected.map { it.id }.toSet().size)
        // All five fresh videos are present, the remaining 15 slots are repeats.
        assertEquals(
            (26..30).map { "v$it" }.toSet(),
            selected.map { it.id }.filter { it !in seen }.toSet(),
        )
        assertEquals(15, selected.count { it.id in seen })
        assertEquals(selected.map { it.id }, newIds)
    }

    @Test
    fun `selectPage returns short pages only when pools are short`() {
        val candidates = (1..3).map { video("v$it") }
        val (selected, newIds) = RecommendedMixer.selectPage(candidates, emptySet(), limit = 20)
        assertEquals(3, selected.size)
        assertEquals(3, newIds.size)
    }

    @Test
    fun `selectPage handles empty candidates and non-positive limits`() {
        assertEquals(emptyList<IwaraVideo>(), RecommendedMixer.selectPage(emptyList(), emptySet(), 20).first)
        assertEquals(emptyList<IwaraVideo>(), RecommendedMixer.selectPage(listOf(video("a")), emptySet(), 0).first)
    }

    /* ------------------------------ paging ------------------------------ */

    @Test
    fun `buildPage echoes page index and uses the large pagination sentinel`() {
        val candidates = (1..30).map { video("v$it") }
        val (page, shownIds) = RecommendedMixer.buildPage(candidates, emptySet(), pageIndex = 2)
        assertEquals(20, page.items.size)
        assertEquals(2, page.pageIndex)
        assertEquals(20, page.limit)
        assertEquals(RecommendedMixer.TOTAL_COUNT, page.totalCount)
        assertTrue("recommended feed must allow unlimited paging", page.hasMore)
        assertEquals(20, shownIds.size)
        assertEquals(page.items.map { it.id }, shownIds)
    }

    @Test
    fun `buildPage with empty candidates produces an empty page`() {
        val (page, shownIds) = RecommendedMixer.buildPage(emptyList(), emptySet(), pageIndex = 0)
        assertTrue(page.items.isEmpty())
        assertTrue(shownIds.isEmpty())
        assertEquals(RecommendedMixer.TOTAL_COUNT, page.totalCount)
        assertFalse(page.hasMore)
    }

    /* ----------------------------- seen ids ----------------------------- */

    @Test
    fun `seen ids parse and serialize newline separated ids`() {
        assertEquals(emptyList<String>(), SeenIds.parse(null))
        assertEquals(emptyList<String>(), SeenIds.parse(""))
        assertEquals(listOf("a", "b"), SeenIds.parse("a\nb\n\n"))
        assertEquals("a\nb", SeenIds.serialize(listOf("a", "b")))
        assertEquals(listOf("a", "b"), SeenIds.parse(SeenIds.serialize(listOf("a", "b"))))
    }

    @Test
    fun `seen ids merge deduplicates and keeps insertion order`() {
        val merged = SeenIds.merge(listOf("v1", "v2", "v3"), listOf("v3", "v4"))
        assertEquals(listOf("v1", "v2", "v3", "v4"), merged)
    }

    @Test
    fun `seen ids are trimmed to the most recent maximum`() {
        val existing = (1..SeenIds.MAX_SEEN).map { "v$it" }
        val lastNew = "v${SeenIds.MAX_SEEN + 1}"
        val merged = SeenIds.merge(existing, listOf(lastNew))
        assertEquals(SeenIds.MAX_SEEN, merged.size)
        assertEquals(lastNew, merged.last())
        assertFalse(merged.contains("v1"))
        assertEquals("v2", merged.first())
    }
}
