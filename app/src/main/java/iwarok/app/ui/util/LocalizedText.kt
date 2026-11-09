package iwarok.app.ui.util

import android.content.Context

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import iwarok.app.R
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraVideo
import iwarok.app.core.QualityPreference
import iwarok.app.core.RatingFilter
import iwarok.app.core.SearchVideoSort
import iwarok.app.core.formatCount
import iwarok.app.core.videoTitle
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

/* ------------------------------------------------------------------------- *
 * Localised labels for core enums. The enum `label` fields stay Chinese for
 * unit tests / data layer; UI code must use these helpers instead.
 * ------------------------------------------------------------------------- */

@Composable
fun FeedSort.localizedLabel(): String = stringResource(
    when (this) {
        FeedSort.RECOMMENDED -> R.string.sort_recommended
        FeedSort.DATE -> R.string.sort_date
        FeedSort.TRENDING -> R.string.sort_trending
        FeedSort.POPULARITY -> R.string.sort_popularity
    },
)

@Composable
fun SearchVideoSort.localizedLabel(): String = stringResource(
    when (this) {
        SearchVideoSort.RELEVANCE -> R.string.search_sort_relevance
        SearchVideoSort.DATE -> R.string.search_sort_date
        SearchVideoSort.VIEWS -> R.string.search_sort_views
        SearchVideoSort.LIKES -> R.string.search_sort_likes
    },
)

@Composable
fun RatingFilter.localizedLabel(): String = stringResource(
    when (this) {
        RatingFilter.ALL -> R.string.rating_all
        RatingFilter.GENERAL -> R.string.rating_general
        RatingFilter.ECCHI -> R.string.rating_ecchi
    },
)

@Composable
fun QualityPreference.localizedLabel(): String = stringResource(
    when (this) {
        QualityPreference.AUTO -> R.string.quality_auto
        QualityPreference.SOURCE -> R.string.quality_source
        QualityPreference.P1080 -> R.string.quality_1080p
        QualityPreference.P720 -> R.string.quality_720p
        QualityPreference.P540 -> R.string.quality_540p
        QualityPreference.P360 -> R.string.quality_360p
    },
)

/** Built-in tag channels localize by stable id; user-added channels keep their label. */
@Composable
fun TagChannel.localizedLabel(): String = when (id) {
    "dance" -> stringResource(R.string.feed_tag_dance)
    "mikumikudance" -> stringResource(R.string.feed_tag_mmd)
    "blender" -> stringResource(R.string.feed_tag_blender)
    "koikatsu" -> stringResource(R.string.feed_tag_koikatsu)
    "genshin_impact" -> stringResource(R.string.feed_tag_genshin)
    "honkai_star_rail" -> stringResource(R.string.feed_tag_star_rail)
    "vtuber" -> stringResource(R.string.feed_tag_vtuber)
    "hmv" -> stringResource(R.string.feed_tag_hmv)
    else -> label
}

/**
 * Data/repository layers still carry Chinese fallback exceptions. Map the
 * known cases to local resources at the UI boundary; unknown Chinese text in a
 * non-Chinese locale falls back to the generic localized error.
 */
fun localizedErrorMessage(context: Context, raw: String?): String {
    if (raw.isNullOrBlank()) return context.getString(R.string.error_generic)
    val lower = raw.lowercase()
    return when {
        raw.contains("外部嵌入") || lower.contains("youtube") ->
            context.getString(R.string.error_external_embed)
        raw.contains("没有可播放") ->
            context.getString(R.string.error_no_playable)
        raw.contains("私人") || raw.contains("权限访问") ||
            raw.contains("403") -> context.getString(R.string.error_private_content)
        raw.contains("404") || raw.contains("不存在") ->
            context.getString(R.string.error_not_found)
        raw.contains("人机校验") -> context.getString(R.string.error_captcha)
        raw.contains("发现内容加载失败") ->
            context.getString(R.string.feed_load_failed)
        raw.contains("操作失败") || raw.contains("网络连接失败") ||
            raw.contains("网络请求失败") || raw.contains("无法解析播放地址") ->
            context.getString(R.string.error_network_generic)
        else -> {
            val locale = context.resources.configuration.locales[0]
            val hasHan = raw.any { it.code in 0x4E00..0x9FFF }
            if (locale.language != "zh" && hasHan) {
                context.getString(R.string.error_generic)
            } else {
                raw
            }
        }
    }
}

/** Rating badge label for a raw `video.rating` value. */
@Composable
fun localizedRatingLabel(rating: String?): String = when (rating?.lowercase()) {
    "general" -> stringResource(R.string.rating_general)
    "ecchi" -> stringResource(R.string.rating_ecchi)
    "all" -> stringResource(R.string.rating_all)
    null, "" -> ""
    else -> rating ?: ""
}

/** Video title with a localised fallback for untitled works. */
@Composable
fun localizedVideoTitle(video: IwaraVideo?): String =
    videoTitle(video, stringResource(R.string.video_untitled))

/** Playback quality label ("原画" / "预览" come from the data layer). */
@Composable
fun localizedPlaybackQualityLabel(label: String?): String = when (label?.trim()) {
    "原画" -> stringResource(R.string.quality_source)
    "预览" -> stringResource(R.string.quality_preview)
    else -> label?.trim().orEmpty()
}

/* ------------------------------------------------------------------------- *
 * Localised formatting: relative dates + compact counts.
 * ------------------------------------------------------------------------- */

fun relativeDateLabel(context: Context, iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } catch (_: Exception) {
        iso.take(10)
    }
}

@Composable
fun localizedRelativeDate(iso: String?): String {
    val context = LocalContext.current
    return relativeDateLabel(context, iso)
}

/**
 * Compact count for like / comment / view numbers. Chinese keeps the familiar
 * 万 / 亿 notation; other locales use a localised K/M compact form.
 */
@Composable
fun localizedCount(value: Int?): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    if (locale.language == "zh") return formatCount(value)
    val number = value ?: 0
    return when {
        number < 10_000 -> NumberFormat.getIntegerInstance(locale).format(number)
        number < 1_000_000 -> trimCount(number / 1_000.0) + "K"
        else -> trimCount(number / 1_000_000.0) + "M"
    }
}

private fun trimCount(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return String.format(Locale.US, "%.1f", rounded).removeSuffix(".0")
}
