package iwarok.app.data

import android.content.Context
import iwarok.app.core.IwaraVideo

/**
 * Very small, fully local interest profile used only to bias the "发现" feed.
 *
 * It does not identify the user or leave the device. Each watched video bumps
 * its tags and creator; the resulting weights add a modest preference bonus on
 * top of the random/trending pool. Keeping the profile deliberately coarse
 * avoids turning the feed into a filter bubble from a handful of views.
 */
internal data class InterestProfile(
    val tagWeights: Map<String, Float> = emptyMap(),
    val userWeights: Map<String, Float> = emptyMap(),
) {
    val isEmpty: Boolean get() = tagWeights.isEmpty() && userWeights.isEmpty()
}

internal class RecommendationProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var lastRecordedId: String? = null

    /** Called on the main thread after a video becomes the current item. */
    fun record(video: IwaraVideo) {
        if (video.id.isBlank()) return
        synchronized(lock) {
            if (video.id == lastRecordedId) return
            lastRecordedId = video.id
            val tags = parse(prefs.getString(KEY_TAGS, null)).toMutableMap()
            val users = parse(prefs.getString(KEY_USERS, null)).toMutableMap()

            video.tags
                .asSequence()
                .map { it.id.trim() }
                .filter { it.isNotEmpty() }
                .forEach { id -> tags[id] = (tags[id] ?: 0f) + 1f }
            video.user?.id
                ?.takeIf { it.isNotBlank() }
                ?.let { id -> users[id] = (users[id] ?: 0f) + 1f }

            prefs.edit()
                .putString(KEY_TAGS, serialize(trim(tags, MAX_TAGS)))
                .putString(KEY_USERS, serialize(trim(users, MAX_USERS)))
                .apply()
        }
    }

    fun snapshot(): InterestProfile = synchronized(lock) {
        InterestProfile(
            tagWeights = parse(prefs.getString(KEY_TAGS, null)),
            userWeights = parse(prefs.getString(KEY_USERS, null)),
        )
    }

    private fun trim(weights: Map<String, Float>, max: Int): Map<String, Float> {
        if (weights.size <= max) return weights
        return weights.entries
            .sortedByDescending { it.value }
            .take(max)
            .associate { it.key to it.value }
    }

    private fun parse(raw: String?): Map<String, Float> {
        if (raw.isNullOrBlank()) return emptyMap()
        return buildMap {
            raw.lineSequence().forEach { line ->
                val cut = line.lastIndexOf('=')
                if (cut <= 0) return@forEach
                val id = line.substring(0, cut).trim()
                val weight = line.substring(cut + 1).toFloatOrNull() ?: return@forEach
                if (id.isNotEmpty() && weight > 0f) put(id, weight)
            }
        }
    }

    private fun serialize(weights: Map<String, Float>): String =
        weights.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private companion object {
        const val PREFS_NAME = "iwarok_interest_profile"
        const val KEY_TAGS = "tag_weights"
        const val KEY_USERS = "user_weights"
        const val MAX_TAGS = 240
        const val MAX_USERS = 120
    }
}
