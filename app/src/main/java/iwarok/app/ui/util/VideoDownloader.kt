package iwarok.app.ui.util

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import iwarok.app.core.IwaraConfig
import iwarok.app.core.IwaraVideo
import iwarok.app.core.PlaybackSource
import iwarok.app.core.videoTitle

/**
 * Enqueues a video download through Android's DownloadManager.
 *
 * The CDN URL is signed and short-lived, so the request is submitted immediately
 * with the same browser headers the player uses. Files go to:
 *   Downloads/IwaRok/<title>-<quality>.mp4
 * On Android 10+ this uses scoped storage and needs no app storage permission.
 */
object VideoDownloader {

    fun enqueue(
        context: Context,
        video: IwaraVideo,
        source: PlaybackSource,
    ): Boolean {
        if (source.url.isBlank()) return false
        return try {
            val title = sanitize(videoTitle(video))
            val quality = sanitize(source.label.ifBlank { source.name }).ifBlank { "video" }
            val fileName = "$title-$quality.mp4"
            val request = DownloadManager.Request(Uri.parse(source.url))
                .setTitle(videoTitle(video))
                .setDescription("IwaRok · ${source.label}")
                .setMimeType(source.mime.ifBlank { "video/mp4" })
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .apply {
                    // API 26-28 need WRITE_EXTERNAL_STORAGE for public Downloads;
                    // without it, keep the file in the app's external Downloads dir.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        setDestinationInExternalFilesDir(
                            context,
                            Environment.DIRECTORY_DOWNLOADS,
                            "IwaRok/$fileName",
                        )
                    } else {
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "IwaRok/$fileName",
                        )
                    }
                }
                .addRequestHeader("User-Agent", IwaraConfig.USER_AGENT)
                .addRequestHeader("Referer", "${IwaraConfig.SITE_ORIGIN}/")
                .addRequestHeader("Accept-Encoding", "identity")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitize(value: String): String {
        val cleaned = value
            .replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
            .trim()
            .trim('.')
            .take(90)
        return cleaned.ifBlank { "IwaRok-video" }
    }
}
