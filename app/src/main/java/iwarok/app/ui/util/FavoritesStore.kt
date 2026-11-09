package iwarok.app.ui.util

import android.content.Context
import iwarok.app.core.IwaraVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local, persistent "favorites" store.
 *
 * Favorites are kept as a JSON snapshot of [IwaraVideo] in SharedPreferences so
 * the list (and the filled bookmark state in the feed / detail screen) survives
 * an app restart. No account or network access is involved.
 */
class FavoritesStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<IwaraVideo>> = _items.asStateFlow()

    fun isFavorite(videoId: String): Boolean = _items.value.any { it.id == videoId }

    /** Toggles [video] in the favorites list and returns the new favorite state. */
    fun toggle(video: IwaraVideo): Boolean {
        val current = _items.value
        val existing = current.any { it.id == video.id }
        val updated = if (existing) {
            current.filterNot { it.id == video.id }
        } else {
            current + video
        }
        _items.value = updated
        persist(updated)
        return !existing
    }

    fun remove(videoId: String) {
        val updated = _items.value.filterNot { it.id == videoId }
        if (updated.size == _items.value.size) return
        _items.value = updated
        persist(updated)
    }

    private fun load(): List<IwaraVideo> = try {
        val raw = prefs.getString(KEY_ITEMS, null)
        if (raw.isNullOrBlank()) emptyList() else json.decodeFromString<List<IwaraVideo>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(items: List<IwaraVideo>) {
        try {
            prefs.edit().putString(KEY_ITEMS, json.encodeToString(items)).apply()
        } catch (_: Exception) {
            // Best effort: the in-memory state already reflects the change.
        }
    }

    companion object {
        private const val PREFS_NAME = "iwarok_favorites"
        private const val KEY_ITEMS = "favorites_v1"

        @Volatile
        private var instance: FavoritesStore? = null

        fun get(context: Context): FavoritesStore =
            instance ?: synchronized(this) {
                instance ?: FavoritesStore(context).also { instance = it }
            }
    }
}
