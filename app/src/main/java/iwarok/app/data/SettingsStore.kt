package iwarok.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import iwarok.app.core.AppSettings
import iwarok.app.core.ContentSite
import iwarok.app.core.FeedSort
import iwarok.app.core.QualityPreference
import iwarok.app.core.RatingFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Single DataStore instance per process (the delegate enforces the singleton). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "iwarok_settings"
)

/**
 * Persists the user's feed quality preferences in DataStore Preferences and
 * mirrors them into a [StateFlow] so the UI updates synchronously.
 */
internal class SettingsStore(private val context: Context) {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Restores persisted settings. Idempotent; unreadable storage keeps defaults. */
    suspend fun load(): AppSettings {
        val prefs = try {
            context.settingsDataStore.data.first()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
        if (prefs != null) {
            // Defaults live in AppSettings so old persisted values keep working
            // while new keys (e.g. content_site) fall back to their defaults.
            val defaults = AppSettings()
            _settings.value = AppSettings(
                sort = prefs[KEY_SORT]?.toEnumOrNull(FeedSort.entries) ?: defaults.sort,
                rating = prefs[KEY_RATING]?.toEnumOrNull(RatingFilter.entries) ?: defaults.rating,
                quality = prefs[KEY_QUALITY]?.toEnumOrNull(QualityPreference.entries) ?: defaults.quality,
                contentSite = prefs[KEY_CONTENT_SITE]?.toEnumOrNull(ContentSite.entries) ?: defaults.contentSite,
            )
        }
        return _settings.value
    }

    suspend fun setSort(sort: FeedSort) {
        _settings.value = _settings.value.copy(sort = sort)
        persist(KEY_SORT, sort.name)
    }

    suspend fun setRating(rating: RatingFilter) {
        _settings.value = _settings.value.copy(rating = rating)
        persist(KEY_RATING, rating.name)
    }

    suspend fun setQuality(quality: QualityPreference) {
        _settings.value = _settings.value.copy(quality = quality)
        persist(KEY_QUALITY, quality.name)
    }

    suspend fun setContentSite(site: ContentSite) {
        _settings.value = _settings.value.copy(contentSite = site)
        persist(KEY_CONTENT_SITE, site.name)
    }

    private suspend fun persist(key: Preferences.Key<String>, value: String) {
        try {
            context.settingsDataStore.edit { prefs -> prefs[key] = value }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Best effort: the in-memory StateFlow already reflects the change.
        }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(entries: List<T>): T? =
        entries.firstOrNull { it.name == this }

    private companion object {
        val KEY_SORT = stringPreferencesKey("feed_sort")
        val KEY_RATING = stringPreferencesKey("feed_rating")
        val KEY_QUALITY = stringPreferencesKey("playback_quality")
        val KEY_CONTENT_SITE = stringPreferencesKey("content_site")
    }
}
