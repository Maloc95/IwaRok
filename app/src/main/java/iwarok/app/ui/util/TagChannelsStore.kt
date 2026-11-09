package iwarok.app.ui.util

import android.content.Context
import iwarok.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One channel in the home feed's horizontal tag row. */
@Serializable
data class TagChannel(
    val id: String,
    val label: String,
)

/**
 * Persistent home-feed tag channels (SharedPreferences + kotlinx.serialization).
 *
 * The defaults are the built-in Douyin-style channels; users can add any valid
 * Iwara tag slug (validated through `repository.fetchTag`) or remove channels.
 * Changes are persisted immediately and survive an app restart.
 */
class TagChannelsStore private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _channels = MutableStateFlow(load() ?: defaultChannels())
    val channels: StateFlow<List<TagChannel>> = _channels.asStateFlow()

    fun contains(tagId: String): Boolean = _channels.value.any { it.id == tagId }

    /** Adds a channel. Returns false when the slug is blank or already present. */
    fun add(tagId: String, label: String): Boolean {
        val id = tagId.trim().lowercase()
        if (id.isBlank()) return false
        val current = _channels.value
        if (current.any { it.id == id }) return false
        val updated = current + TagChannel(id = id, label = label.trim().ifBlank { id })
        _channels.value = updated
        persist(updated)
        return true
    }

    fun remove(tagId: String) {
        val updated = _channels.value.filterNot { it.id == tagId }
        if (updated.size == _channels.value.size) return
        _channels.value = updated
        persist(updated)
    }

    /** Restores the built-in channel list. */
    fun reset() {
        val defaults = defaultChannels()
        _channels.value = defaults
        persist(defaults)
    }

    private fun defaultChannels(): List<TagChannel> = listOf(
        TagChannel("dance", appContext.getString(R.string.feed_tag_dance)),
        TagChannel("mikumikudance", appContext.getString(R.string.feed_tag_mmd)),
        TagChannel("blender", appContext.getString(R.string.feed_tag_blender)),
        TagChannel("koikatsu", appContext.getString(R.string.feed_tag_koikatsu)),
        TagChannel("genshin_impact", appContext.getString(R.string.feed_tag_genshin)),
        TagChannel("honkai_star_rail", appContext.getString(R.string.feed_tag_star_rail)),
        TagChannel("vtuber", appContext.getString(R.string.feed_tag_vtuber)),
        TagChannel("hmv", appContext.getString(R.string.feed_tag_hmv)),
    )

    private fun load(): List<TagChannel>? = try {
        val raw = prefs.getString(KEY_CHANNELS, null)
        if (raw.isNullOrBlank()) null else json.decodeFromString<List<TagChannel>>(raw)
    } catch (_: Exception) {
        null
    }

    private fun persist(channels: List<TagChannel>) {
        try {
            prefs.edit().putString(KEY_CHANNELS, json.encodeToString(channels)).apply()
        } catch (_: Exception) {
            // Best effort: the in-memory state already reflects the change.
        }
    }

    companion object {
        private const val PREFS_NAME = "iwarok_tag_channels"
        private const val KEY_CHANNELS = "channels_v1"

        @Volatile
        private var instance: TagChannelsStore? = null

        fun get(context: Context): TagChannelsStore =
            instance ?: synchronized(this) {
                instance ?: TagChannelsStore(context).also { instance = it }
            }
    }
}
