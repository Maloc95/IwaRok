package iwarok.app.ui.util

import android.content.Context
import iwarok.app.core.IwaraTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Lazily loaded catalogue of every Iwara tag shipped with the app.
 *
 * The bundled `assets/iwara_tags.json` holds ~3000 tags as a JSON array of
 * `{id, type, sensitive}` objects. It is parsed once with kotlinx.serialization
 * (no network request, no extra dependency) and cached for the whole process.
 */
object TagCatalog {

    private const val ASSET_NAME = "iwara_tags.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cached: List<IwaraTag>? = null

    /** Loads (and caches) the tag catalogue off the main thread. */
    suspend fun load(context: Context): Result<List<IwaraTag>> {
        cached?.let { return Result.success(it) }
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.assets
                    .open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                val tags = json.decodeFromString<List<IwaraTag>>(raw)
                    .filter { it.id.isNotBlank() }
                    .sortedBy { it.id }
                cached = tags
                tags
            }
        }
    }
}
