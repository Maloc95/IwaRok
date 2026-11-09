package iwarok.app.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Walks the ContextWrapper chain to find the host [Activity]. */
fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * Landscape "full screen" mode for landscape videos.
 *
 * Enter: rotate to sensor landscape, hide the system bars.
 * Exit: restore portrait + show the system bars again. The Activity declares
 * `configChanges` for orientation/screenSize so playback continues without a
 * recreation.
 *
 * The hide/show calls are re-applied shortly after the rotation and on
 * ON_RESUME because some devices re-show the bars during / after a
 * configuration change.
 */
class FullscreenController(private val activity: Activity?) {

    private val handler = Handler(Looper.getMainLooper())

    var isFullscreen by mutableStateOf(false)
        private set

    fun enter() {
        val host = activity ?: return
        if (isFullscreen) return
        isFullscreen = true
        try {
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Exception) {
            // Orientation requests can be rejected while the activity is not resumed.
        }
        hideSystemBars(host)
        // Re-apply after the rotation settles (bar visibility can be reset by
        // the system during the configuration change).
        schedule(150L) { if (isFullscreen) hideSystemBars(host) }
        schedule(400L) { if (isFullscreen) hideSystemBars(host) }
        schedule(800L) { if (isFullscreen) hideSystemBars(host) }
    }

    fun exit() {
        val host = activity
        if (!isFullscreen) return
        isFullscreen = false
        if (host == null) return
        try {
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (_: Exception) {
            // Ignore: the activity is finishing.
        }
        showSystemBars(host)
        schedule(150L) { if (!isFullscreen) showSystemBars(host) }
        schedule(450L) { if (!isFullscreen) showSystemBars(host) }
    }

    fun toggle() {
        if (isFullscreen) exit() else enter()
    }

    /** Re-applies the current system-bar state (used on ON_RESUME). */
    fun syncSystemBars() {
        val host = activity ?: return
        if (isFullscreen) hideSystemBars(host) else showSystemBars(host)
    }

    private fun schedule(delayMs: Long, block: () -> Unit) {
        handler.postDelayed(block, delayMs)
    }

    private fun hideSystemBars(host: Activity) {
        val controller = WindowCompat.getInsetsController(host.window, host.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars(host: Activity) {
        WindowCompat.getInsetsController(host.window, host.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Remembers a [FullscreenController] for the current screen and guarantees that
 * portrait + system bars are restored when the screen leaves composition (for
 * example when navigating to a profile or pressing the system back).
 */
@Composable
fun rememberFullscreenController(): FullscreenController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findHostActivity() }
    val controller = remember(activity) { FullscreenController(activity) }

    // Keep the system bars hidden while fullscreen across pause / resume.
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.syncSystemBars()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.exit()
        }
    }
    return controller
}
