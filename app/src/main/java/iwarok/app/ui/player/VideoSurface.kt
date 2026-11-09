package iwarok.app.ui.player

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Renders the single shared ExoPlayer surface.
 *
 * The whole feed uses exactly one instance of this composable, kept behind the
 * pager content. That avoids the classic SurfaceView ownership race you get when
 * every pager page renders its own PlayerView and pages are recycled while the
 * user swipes quickly.
 *
 * The resize mode is always [AspectRatioFrameLayout.RESIZE_MODE_FIT]: every
 * video is shown completely (letterbox / pillarbox black bars are fine) so no
 * content is ever cropped, in the feed, the comment-shrunk top area and the
 * detail / fullscreen players alike.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
) {
    // Read in composition so the view recomposes and toggles keep-screen-on
    // when playback starts / stops.
    val isPlaying = playerManager.isPlaying

    val resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                this.resizeMode = resizeMode
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(false)
                setBackgroundColor(AndroidColor.BLACK)
                keepScreenOn = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            val target = playerManager.playerOrNull
            if (view.player !== target) {
                view.player = target
            }
            view.resizeMode = resizeMode
            view.keepScreenOn = target != null && isPlaying
        },
        onRelease = { view ->
            view.keepScreenOn = false
            view.player = null
        },
    )
}
