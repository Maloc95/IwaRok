package iwarok.app.data

import android.content.Context
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraVideo
import iwarok.app.core.VideoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Pure helpers behind the local "发现" (for you) feed.
 *
 * The upstream API only offers fixed sorts, so every page is built by sampling
 * several sorts on random pages, merging/deduplicating, shuffling and suppressing
 * recently shown videos. Keeping the logic here (instead of inline in the
 * repository) makes dedupe/shuffle/seen behaviour unit-testable on the JVM.
 */
internal object RecommendedMixer {

    /**
     * Pagination sentinel: large enough that the UI can keep requesting pages
     * forever (every page is a fresh random sample, so there is no real end).
     */
    const val TOTAL_COUNT = 1_000_000
    const val MAX_EXTRA_DEPTH = 10

    /** One upstream sort sampled on a random page range. */
    data class Source(val apiValue: String, val pageRange: IntRange)

    /**
     * Wider candidate reservoir: the first N pages of five upstream sorts.
     * Views/likes are API-supported but not exposed as feed tabs; they are only
     * used here to enlarge and diversify the discovery pool.
     */
    val SOURCES: List<Source> = listOf(
        Source("trending", 0..9),
        Source("popularity", 0..9),
        Source("date", 0..7),
        Source("views", 0..7),
        Source("likes", 0..7),
    )

    /** Page depth grows with the number of already-seen videos, up to a cap. */
    fun extraDepthFor(seenCount: Int): Int =
        (seenCount / 300).coerceIn(0, MAX_EXTRA_DEPTH)

    fun effectivePageRange(source: Source, seenCount: Int): IntRange {
        val last = (source.pageRange.last + extraDepthFor(seenCount))
            .coerceAtMost(source.pageRange.last + MAX_EXTRA_DEPTH)
        return source.pageRange.first..last
    }

    fun randomPage(source: Source, random: Random, extraDepth: Int = 0): Int {
        val range = source.pageRange
        val last = (range.last + extraDepth)
            .coerceAtMost(range.last + MAX_EXTRA_DEPTH)
        val span = last - range.first + 1
        return range.first + random.nextInt(span)
    }

    /** Merges pools, drops duplicate/blank ids and shuffles the result. */
    fun mergeAndShuffle(pools: List<List<IwaraVideo>>, random: Random): List<IwaraVideo> {
        val unique = LinkedHashMap<String, IwaraVideo>()
        pools.forEach { items ->
            items.forEach { video ->
                if (
                    video.id.isNotBlank() &&
                    !video.isPrivate &&
                    (video.status == null || video.status == "active") &&
                    !video.file?.id.isNullOrBlank()
                ) {
                    unique.putIfAbsent(video.id, video)
                }
            }
        }
        return unique.values.shuffled(random)
    }

    /**
     * Picks up to [limit] videos, preferring ids not yet in [seen].
     *
     * When there are not enough unseen items, already-seen candidates are allowed
     * back in (instead of returning a short page) so the feed always has content.
     */
    fun selectPage(
        candidates: List<IwaraVideo>,
        seen: Set<String>,
        limit: Int,
    ): Pair<List<IwaraVideo>, List<String>> {
        if (candidates.isEmpty() || limit <= 0) return emptyList<IwaraVideo>() to emptyList()
        val fresh = candidates.filter { it.id !in seen }
        val selected = if (fresh.size >= limit) {
            fresh.take(limit)
        } else {
            val fallback = candidates.filter { it.id in seen }
            (fresh + fallback).distinctBy { it.id }.take(limit)
        }
        return selected to selected.map { it.id }
    }

    /**
     * Applies a light local interest bonus while keeping a large random share.
     * Candidates matching watched tags/creators move up, but the feed is still
     * 65% random, so it does not collapse into a narrow bubble.
     */
    fun applyProfile(
        candidates: List<IwaraVideo>,
        profile: InterestProfile,
        random: Random,
    ): List<IwaraVideo> {
        if (profile.isEmpty || candidates.size <= 1) return candidates
        val maxTag = profile.tagWeights.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val maxUser = profile.userWeights.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        return candidates.sortedByDescending { video ->
            val tagAffinity = video.tags
                .sumOf { tag -> profile.tagWeights[tag.id]?.toDouble() ?: 0.0 }
                .div(maxTag.toDouble())
            val userAffinity = profile.userWeights[video.user?.id]
                ?.div(maxUser.toDouble())
                ?: 0.0
            val affinity = (tagAffinity * 0.6 + userAffinity * 0.4)
                .coerceIn(0.0, 1.0)
            random.nextDouble() * 0.65 + affinity * 0.35
        }
    }

    /** Builds one paginated recommendation page plus the ids that must be marked seen. */
    fun buildPage(
        candidates: List<IwaraVideo>,
        seen: Set<String>,
        pageIndex: Int,
        limit: Int = IwaraConfig.PAGE_SIZE,
    ): Pair<VideoPage, List<String>> {
        val (items, newIds) = selectPage(candidates, seen, limit)
        return VideoPage(
            items = items,
            pageIndex = pageIndex,
            limit = limit,
            totalCount = TOTAL_COUNT,
        ) to newIds
    }
}

/**
 * Bounded, insertion-ordered list of recently shown video ids.
 *
 * Serialized as newline separated ids in a small plain SharedPreferences file
 * (a `StringSet` would lose ordering, which is what the trim-to-[MAX_SEEN] uses).
 */
internal object SeenIds {

    const val MAX_SEEN = 3000

    fun parse(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun merge(
        existing: List<String>,
        added: Collection<String>,
        max: Int = MAX_SEEN,
    ): List<String> {
        val merged = LinkedHashSet<String>(existing.size + added.size)
        existing.forEach { id -> if (id.isNotBlank()) merged.add(id.trim()) }
        added.forEach { id -> if (id.isNotBlank()) merged.add(id.trim()) }
        val list = merged.toList()
        return if (list.size <= max) list else list.takeLast(max)
    }

    fun serialize(ids: List<String>): String = ids.joinToString("\n")
}

/**
 * Persists the ids of videos already shown in the recommendation feed so the
 * next page avoids them. Reads/writes happen on [Dispatchers.IO] because callers
 * are UI coroutines.
 */
internal class SeenVideoStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    suspend fun seen(): Set<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            SeenIds.parse(prefs.getString(KEY_SEEN, null)).toSet()
        }
    }

    suspend fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val merged = SeenIds.merge(SeenIds.parse(prefs.getString(KEY_SEEN, null)), ids)
                prefs.edit().putString(KEY_SEEN, SeenIds.serialize(merged)).apply()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            prefs.edit().remove(KEY_SEEN).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "iwarok_recommended"
        const val KEY_SEEN = "seen_video_ids"
    }
}
