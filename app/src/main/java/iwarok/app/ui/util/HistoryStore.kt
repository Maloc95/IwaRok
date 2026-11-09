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
 * Local, persistent "watch history" store.
 *
 * Mirrors [FavoritesStore]: a JSON snapshot of [IwaraVideo] kept in
 * SharedPreferences so the history survives an app restart. Most recently
 * watched first, deduplicated, capped at [MAX_ITEMS].
 */
class HistoryStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<IwaraVideo>> = _items.asStateFlow()

    /** Records [video] as most recently watched (dedupe + move to front). */
    fun record(video: IwaraVideo) {
        if (video.id.isBlank()) return
        val current = _items.value
        if (current.firstOrNull()?.id == video.id) return
        val updated = (listOf(video) + current.filterNot { it.id == video.id }).take(MAX_ITEMS)
        _items.value = updated
        persist(updated)
    }

    fun clear() {
        if (_items.value.isEmpty()) return
        _items.value = emptyList()
        persist(emptyList())
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
        private const val PREFS_NAME = "iwarok_history"
        private const val KEY_ITEMS = "history_v1"
        private const val MAX_ITEMS = 200

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: HistoryStore(context).also { instance = it }
            }
    }
}
