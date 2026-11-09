package iwarok.app.ui.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import iwarok.app.R
import iwarok.app.core.IwaraVideo
import iwarok.app.core.videoTitle
import iwarok.app.core.videoWebUrl

/* ------------------------------------------------------------------------- *
 * Small Android-side helpers used by the UI layer.
 * ------------------------------------------------------------------------- */

private const val UI_PREFS_NAME = "iwarok_ui_prefs"
private const val KEY_AGE_CONFIRMED = "age_confirmed"

/** Local-only UI preferences (age gate acknowledgement, first-launch state). */
class UiPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)

    fun isAgeConfirmed(): Boolean = prefs.getBoolean(KEY_AGE_CONFIRMED, false)

    fun setAgeConfirmed() {
        prefs.edit().putBoolean(KEY_AGE_CONFIRMED, true).apply()
    }
}

/** Opens an http(s) URL, showing a toast when no browser is installed. */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_no_browser), Toast.LENGTH_SHORT).show()
    }
}

/** ACTION_SEND share sheet for an Iwara video link. */
fun shareVideo(context: Context, video: IwaraVideo) {
    val url = videoWebUrl(video.id)
    val title = videoTitle(video, context.getString(R.string.video_untitled))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text_template, title, url))
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_no_share_app), Toast.LENGTH_SHORT).show()
    }
}

/** Reads the app versionName, falling back to a static value. */
fun appVersionName(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName ?: "4.2.3"
} catch (_: Exception) {
    "4.2.3"
}

/**
 * Restarts the process.
 *
 * The AppGraph builds its HTTP clients in Application.onCreate, so proxy changes
 * only apply after a full process restart. An AlarmManager PendingIntent relaunches
 * the launcher activity after the current process is killed.
 */
fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    if (alarmManager != null) {
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 250, pendingIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
    } else {
        context.startActivity(launchIntent)
    }
}
