package iwarok.app.data

import iwarok.app.core.IwaraConfig
import iwarok.app.core.QualityPreference
import iwarok.app.core.VideoSource
import iwarok.app.core.SourceUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure playback logic: X-Version signing, URL normalisation and quality selection. */
class PlaybackLogicTest {

    /* ----------------------------- X-Version ----------------------------- */

    @Test
    fun `xVersion matches known SHA1 vectors`() {
        // Vectors computed independently with System.Security.Cryptography.SHA1.
        assertEquals(
            "9e0fc135bd1eb1cb93a74377eaa0bf55d2085368",
            IwaraPlayback.xVersion("abc", "123", "salt"),
        )
        assertEquals(
            "1e853645757fd3cad8480b294127d8201895752c",
            IwaraPlayback.xVersion(
                "6f4a1b2c-3d4e-5f60-7182-93a4b5c6d7e8",
                "1762500000000",
                IwaraConfig.DEFAULT_SALT,
            ),
        )
        assertEquals(
            "52a3d977e1e54430adac05ea416eed154dadd92a",
            IwaraPlayback.xVersion(
                "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0",
                "1700000000123",
                IwaraConfig.DEFAULT_SALT,
            ),
        )
    }

    @Test
    fun `xVersion defaults to the bundled salt`() {
        assertEquals(
            IwaraPlayback.xVersion("uuid", "1", IwaraConfig.DEFAULT_SALT),
            IwaraPlayback.xVersion("uuid", "1"),
        )
    }

    @Test
    fun `parseSignedFile extracts uuid and expires`() {
        val signed = IwaraPlayback.parseSignedFile(
            "https://filesq.iwara.tv/file/6f4a1b2c-3d4e-5f60-7182-93a4b5c6d7e8?expires=1762500000000&hash=deadbeef"
        )
        assertEquals("6f4a1b2c-3d4e-5f60-7182-93a4b5c6d7e8", signed?.uuid)
        assertEquals("1762500000000", signed?.expires)
    }

    @Test
    fun `parseSignedFile rejects urls without expires or uuid`() {
        assertNull(IwaraPlayback.parseSignedFile(null))
        assertNull(IwaraPlayback.parseSignedFile(""))
        assertNull(IwaraPlayback.parseSignedFile("not a url"))
        assertNull(IwaraPlayback.parseSignedFile("https://filesq.iwara.tv/file/abc"))
        assertNull(
            IwaraPlayback.parseSignedFile("https://filesq.iwara.tv/file/?expires=1")
        )
    }

    /* --------------------------- URL normalising --------------------------- */

    @Test
    fun `normalizeUrl upgrades protocol relative and http urls`() {
        assertEquals(
            "https://kafka.iwara.tv/video.mp4",
            IwaraPlayback.normalizeUrl("//kafka.iwara.tv/video.mp4"),
        )
        assertEquals(
            "https://kafka.iwara.tv/video.mp4",
            IwaraPlayback.normalizeUrl("http://kafka.iwara.tv/video.mp4"),
        )
        assertEquals(
            "https://kafka.iwara.tv/video.mp4",
            IwaraPlayback.normalizeUrl("https://kafka.iwara.tv/video.mp4"),
        )
        assertEquals(
            "https://kafka.iwara.tv/video.mp4",
            IwaraPlayback.normalizeUrl("  //kafka.iwara.tv/video.mp4  "),
        )
    }

    @Test
    fun `normalizeUrl rejects blanks`() {
        assertNull(IwaraPlayback.normalizeUrl(null))
        assertNull(IwaraPlayback.normalizeUrl(""))
        assertNull(IwaraPlayback.normalizeUrl("   "))
    }

    /* ------------------------- source selection ------------------------- */

    private fun manifest(vararg specs: Pair<String, String>): List<VideoSource> =
        specs.map { (name, view) ->
            VideoSource(name = name, type = "video/mp4", src = SourceUrls(view = view, download = view))
        }

    @Test
    fun `AUTO prefers 720 over Source and 1080`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "preview" to "//kafka.iwara.tv/preview.mp4",
                "1080" to "//kafka.iwara.tv/1080.mp4",
                "720" to "//kafka.iwara.tv/720.mp4",
                "Source" to "//kafka.iwara.tv/source.mp4",
                "360" to "//kafka.iwara.tv/360.mp4",
            ),
            QualityPreference.AUTO,
        )
        assertEquals("720", selected.first().name)
        assertEquals("720P", selected.first().label)
        assertEquals("https://kafka.iwara.tv/720.mp4", selected.first().url)
        // Preview is never playable.
        assertTrue(selected.none { it.name == "preview" })
    }

    @Test
    fun `AUTO falls back through the preferred ladder`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "1080" to "//kafka.iwara.tv/1080.mp4",
                "480" to "//kafka.iwara.tv/480.mp4",
                "Source" to "//kafka.iwara.tv/source.mp4",
            ),
            QualityPreference.AUTO,
        )
        assertEquals("480", selected.first().name)
    }

    @Test
    fun `AUTO falls back to Source when no numbered source exists`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "1080" to "//kafka.iwara.tv/1080.mp4",
                "Source" to "//kafka.iwara.tv/source.mp4",
            ),
            QualityPreference.AUTO,
        )
        assertEquals("Source", selected.first().name)
        assertEquals("原画", selected.first().label)
    }

    @Test
    fun `AUTO uses the highest remaining quality as last resort`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "360" to "//kafka.iwara.tv/360.mp4",
                "1080" to "//kafka.iwara.tv/1080.mp4",
            ),
            QualityPreference.AUTO,
        )
        // 360 is on the preferred ladder and wins.
        assertEquals("360", selected.first().name)
    }

    @Test
    fun `AUTO picks the highest numeric quality when ladder is absent`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "240" to "//kafka.iwara.tv/240.mp4",
                "1080" to "//kafka.iwara.tv/1080.mp4",
            ),
            QualityPreference.AUTO,
        )
        assertEquals("1080", selected.first().name)
    }

    @Test
    fun `explicit preference matches by name`() {
        val sources = manifest(
            "720" to "//kafka.iwara.tv/720.mp4",
            "540" to "//kafka.iwara.tv/540.mp4",
        )
        assertEquals("540", IwaraPlayback.selectSources(sources, QualityPreference.P540).first().name)
        assertEquals("720", IwaraPlayback.selectSources(sources, QualityPreference.P720).first().name)
    }

    @Test
    fun `explicit preference falls back to Source then first entry`() {
        val withSource = manifest(
            "720" to "//kafka.iwara.tv/720.mp4",
            "Source" to "//kafka.iwara.tv/source.mp4",
        )
        assertEquals(
            "Source",
            IwaraPlayback.selectSources(withSource, QualityPreference.P1080).first().name,
        )
        val withoutSource = manifest(
            "720" to "//kafka.iwara.tv/720.mp4",
            "540" to "//kafka.iwara.tv/540.mp4",
        )
        assertEquals(
            "720",
            IwaraPlayback.selectSources(withoutSource, QualityPreference.P360).first().name,
        )
    }

    @Test
    fun `fallback order moves down from the selected source`() {
        val selected = IwaraPlayback.selectSources(
            manifest(
                "Source" to "//kafka.iwara.tv/source.mp4",
                "540" to "//kafka.iwara.tv/540.mp4",
                "360" to "//kafka.iwara.tv/360.mp4",
            ),
            QualityPreference.SOURCE,
        )
        assertEquals(listOf("Source", "540", "360"), selected.map { it.name })
    }

    @Test
    fun `selectSources skips entries without a view url`() {
        val sources = listOf(
            VideoSource(name = "Source", type = "video/mp4", src = null),
            VideoSource(
                name = "720",
                type = "video/mp4",
                src = SourceUrls(view = "//kafka.iwara.tv/720.mp4"),
            ),
        )
        val selected = IwaraPlayback.selectSources(sources, QualityPreference.SOURCE)
        assertEquals(1, selected.size)
        assertEquals("720", selected.first().name)
    }

    @Test
    fun `selectSources returns empty list for an empty manifest`() {
        assertTrue(IwaraPlayback.selectSources(emptyList(), QualityPreference.AUTO).isEmpty())
    }

    @Test
    fun `quality labels are human readable`() {
        assertEquals("原画", IwaraPlayback.qualityLabel("Source"))
        assertEquals("720P", IwaraPlayback.qualityLabel("720"))
        assertEquals("1080P", IwaraPlayback.qualityLabel("1080"))
        assertEquals("预览", IwaraPlayback.qualityLabel("preview"))
        assertEquals("HD", IwaraPlayback.qualityLabel("HD"))
    }

    @Test
    fun `previewUrl is normalised and nullable`() {
        val sources = manifest(
            "preview" to "//kafka.iwara.tv/preview.mp4",
            "720" to "//kafka.iwara.tv/720.mp4",
        )
        assertEquals("https://kafka.iwara.tv/preview.mp4", IwaraPlayback.previewUrl(sources))
        assertNull(IwaraPlayback.previewUrl(emptyList()))
    }
}
