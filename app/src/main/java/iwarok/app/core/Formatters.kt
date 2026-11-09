package iwarok.app.core

import java.time.Duration
import java.time.Instant

/* ------------------------------------------------------------------------- *
 * Presentation helpers shared by all screens.
 * Kept in :core so both the data layer (tests) and UI can use them.
 * ------------------------------------------------------------------------- */

private const val TEN_THOUSAND = 10_000.0
private const val HUNDRED_MILLION = 100_000_000.0

fun formatCount(value: Int?): String {
    val number = value ?: 0
    return when {
        number < TEN_THOUSAND -> number.toString()
        number < HUNDRED_MILLION -> {
            val wan = number / TEN_THOUSAND
            if (wan >= 100) "${wan.toInt()}万" else "${trimZero(wan)}万"
        }
        else -> "${trimZero(number / HUNDRED_MILLION)}亿"
    }
}

private fun trimZero(value: Double): String {
    val text = (Math.round(value * 10) / 10.0).toString()
    return text.removeSuffix(".0")
}

fun formatDuration(seconds: Int?): String {
    val total = (seconds ?: 0).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

fun formatRelativeDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        val diff = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
        when {
            diff < 60 -> "刚刚"
            diff < 3_600 -> "${diff / 60} 分钟前"
            diff < 86_400 -> "${diff / 3_600} 小时前"
            diff < 30L * 86_400 -> "${diff / 86_400} 天前"
            else -> iso.substring(0, 10)
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}

/**
 * Iwara's tag API only exposes slugs (`honkai_star_rail`), not localised display
 * names. Present them the way the website does: underscores become spaces and
 * Latin words are title-cased.
 */
fun tagLabel(id: String?): String {
    val raw = (id ?: "").replace('_', ' ').trim()
    if (raw.isEmpty()) return ""
    return raw.split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
}

fun videoTitle(video: IwaraVideo?, fallback: String = "无标题"): String =
    video?.title?.takeIf { it.isNotBlank() } ?: fallback

fun videoWebUrl(videoId: String): String = IwaraConfig.webVideoUrl(videoId)

/** Original image object -> i.iwara.tv URL. [kind] is e.g. "avatar", "profileHeader", "thumbnail", "large". */
fun imageUrl(file: IwaraFile?, kind: String = "thumbnail"): String? {
    if (file == null || file.id.isBlank()) return null
    val name = file.name?.takeIf { it.isNotBlank() } ?: return null
    val fixedName = if (kind == "avatar" && !name.substringAfterLast('.', "").matches(Regex("[a-zA-Z0-9]{2,5}"))) "$name.jpg" else name
    return "${IwaraConfig.IMAGE_HOST}/image/$kind/${file.id}/$fixedName"
}

/** Cover frame for a video. Uses custom thumbnail when the author uploaded one. */
fun videoThumbnailUrl(video: IwaraVideo?): String? {
    if (video == null) return null
    video.customThumbnail?.let { custom ->
        imageUrl(custom, "thumbnail")?.let { return it }
    }
    val fileId = video.file?.id?.takeIf { it.isNotBlank() } ?: return null
    val frame = (video.thumbnail ?: 0).coerceAtLeast(0).toString().padStart(2, '0')
    return "${IwaraConfig.IMAGE_HOST}/image/thumbnail/$fileId/thumbnail-$frame.jpg"
}

fun avatarUrl(user: IwaraUser?): String? = imageUrl(user?.avatar, "avatar")

fun profileHeaderUrl(user: IwaraUser?): String? = imageUrl(user?.header, "profileHeader")

fun ratingLabel(rating: String?): String = when (rating?.lowercase()) {
    "general" -> "全年龄"
    "ecchi" -> "Ecchi"
    "all" -> "全部"
    null, "" -> ""
    else -> rating ?: ""
}
