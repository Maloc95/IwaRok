package iwarok.app.ui.player

import android.content.Context
import iwarok.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import iwarok.app.core.IwaraConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * Process-wide / app-level playback owner.
 *
 * One [ExoPlayer] instance is reused by every page of the feed. Pages only
 * *bind* to it while they are the active page; when the user swipes away the
 * page is detached and playback is paused, so we never leak decoders or start
 * background playback.
 *
 * All player callbacks arrive on the main thread, which is the same thread
 * Compose runs on, so plain [mutableStateOf] fields are safe to read in
 * composition.
 */
@OptIn(UnstableApi::class)
class PlayerManager(
    context: Context,
    httpClient: OkHttpClient,
) {

    private val appContext: Context = context.applicationContext
    /** Language-wrapped context for user-facing strings (appContext is not). */
    private val stringContext: Context = context

    /**
     * Dedicated media client. HTTP/1.1 is forced on purpose: through Clash/TUN
     * the OkHttp HTTP/2 media path was measured at ~0.2-2.3 Mbps while the same
     * signed CDN URL over HTTP/1.1 reached ~8-12 Mbps. MP4 range streaming does
     * not gain anything from HTTP/2 here, but the flow-control behaviour of the
     * proxied connection caused constant rebuffering.
     */
    private val mediaHttpClient: OkHttpClient = httpClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    private val dataSourceFactory: OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(mediaHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to IwaraConfig.USER_AGENT,
                    "Referer" to "${IwaraConfig.SITE_ORIGIN}/",
                    "Accept" to "*/*",
                    // Byte ranges for MP4 must not be compressed/transformed.
                    "Accept-Encoding" to "identity",
                ),
            )



    private var exoPlayer: ExoPlayer? = null

    // ---------------------------------------------------------------- state

    /** Id of the video currently bound to the player, or null when detached. */
    var boundVideoId by mutableStateOf<String?>(null)
        private set

    /** True when the player is actually playing (not paused/buffering). */
    var isPlaying by mutableStateOf(false)
        private set

    /** True while the current media item is buffering. */
    var isBuffering by mutableStateOf(false)
        private set

    /** Number of mid-playback rebuffer stalls for the bound item. */
    var stallCount by mutableStateOf(0)
        private set
    private var lastPlaybackState: Int = Player.STATE_IDLE

    /** True after the first video frame has been rendered for the bound video. */
    var firstFrameRendered by mutableStateOf(false)
        private set

    /** True when the user explicitly paused the current video with a tap. */
    var userPaused by mutableStateOf(false)
        private set

    /** Last playback failure as a user-facing Chinese message, if any. */
    var playbackError by mutableStateOf<String?>(null)
        private set

    /** True while audio is muted (starts unmuted). */
    var isMuted by mutableStateOf(false)
        private set

    /** Current player volume (0..1); shared by the fullscreen volume slider. */
    private var volumeState by mutableStateOf(1f)
    val volume: Float get() = volumeState

    /** Current playback speed (1f = normal). Used by the Douyin-style gestures. */
    private var playbackSpeedState by mutableStateOf(1f)
    val playbackSpeed: Float get() = playbackSpeedState

    /** Current media duration in milliseconds (0 when unknown). */
    var durationMs by mutableStateOf(0L)
        private set

    /** Decoded video width in pixels (0 until the first video size callback). */
    var videoWidth by mutableStateOf(0)
        private set

    /** Decoded video height in pixels (0 until the first video size callback). */
    var videoHeight by mutableStateOf(0)
        private set

    /** True when the current item is a landscape (width > height) video. */
    val isLandscapeVideo: Boolean
        get() = videoHeight > 0 && videoWidth > videoHeight

    /** True while the host screen is visible (feed / search feed). */
    private var screenActive: Boolean = false
    /**
     * More than one player screen can overlap for a frame during navigation.
     * Ref-counting prevents the outgoing screen's onDispose from pausing or
     * detaching the player that the incoming screen has just activated.
     */
    private var activeScreenCount: Int = 0

    /** True while the Activity is in the foreground. */
    var appForeground: Boolean = true
        private set

    /** Whether playback is requested for the bound page (not user-paused). */
    private var shouldPlay: Boolean = false

    private var currentMediaKey: String? = null

    // -------------------------------------------------------------- listeners

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            playbackError = friendlyPlaybackError(error)
            isPlaying = false
            isBuffering = false
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        }

        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING &&
                lastPlaybackState == Player.STATE_READY &&
                (exoPlayer?.currentPosition ?: 0L) > 0L
            ) {
                stallCount++
            }
            lastPlaybackState = playbackState
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                val value = exoPlayer?.duration ?: 0L
                if (value > 0L) durationMs = value
                // Some lifecycle/transition races can leave playWhenReady false
                // after the media is ready even though playback is still wanted.
                if (shouldPlay && screenActive && appForeground &&
                    exoPlayer?.playWhenReady == false
                ) {
                    exoPlayer?.playWhenReady = true
                }
            }
        }

        override fun onRenderedFirstFrame() {
            firstFrameRendered = true
            // A page can render its first frame while a transition/lifecycle
            // race has left shouldPlay false. If the user never paused, resume.
            if (!userPaused && boundVideoId != null) {
                shouldPlay = true
                syncPlayWhenReady()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
        }

        override fun onVolumeChanged(volume: Float) {
            isMuted = volume == 0f
        }
    }

    // ------------------------------------------------------------ public API

    /** The player if it has already been created, otherwise null. */
    val playerOrNull: ExoPlayer?
        get() = exoPlayer

    /** The shared player instance, created on first access. */
    val player: ExoPlayer
        get() = ensurePlayer()

    /**
     * Bind the player to [videoId] and start preparing / playing [url].
     * Re-binding the same video with the same URL is a no-op.
     */
    fun play(videoId: String, url: String, mimeType: String? = null) {
        val mediaKey = "$videoId|$url"
        val exo = ensurePlayer()
        userPaused = false
        shouldPlay = true
        if (currentMediaKey != mediaKey) {
            currentMediaKey = mediaKey
            boundVideoId = videoId
            playbackError = null
            firstFrameRendered = false
            isBuffering = true
            stallCount = 0
            lastPlaybackState = Player.STATE_IDLE
            durationMs = 0L
            videoWidth = 0
            videoHeight = 0
            val itemBuilder = MediaItem.Builder().setUri(url)
            if (!mimeType.isNullOrBlank()) itemBuilder.setMimeType(mimeType)
            exo.setMediaItem(itemBuilder.build())
            exo.prepare()
        } else if (playbackError != null) {
            // Re-binding the same URL after a transient failure: retry in place.
            playbackError = null
            firstFrameRendered = false
            isBuffering = true
            exo.prepare()
        }
        syncPlayWhenReady()
    }

    /** User tapped the video: pause/resume the current media item. */
    fun togglePlayPause() {
        val exo = exoPlayer ?: return
        if (shouldPlay) {
            // Taps while the first frame is still loading are almost always
            // attempts to start playback, not an intentional pause.
            if (isBuffering || !firstFrameRendered) {
                userPaused = false
                shouldPlay = true
                syncPlayWhenReady()
                return
            }
            shouldPlay = false
            userPaused = true
            exo.pause()
        } else {
            shouldPlay = true
            userPaused = false
            if (playbackError != null) {
                // Retry the same item after a transient decode/network error.
                playbackError = null
                firstFrameRendered = false
                exo.prepare()
            }
            syncPlayWhenReady()
        }
    }

    fun toggleMute() {
        setVolume(if (isMuted) 1f else 0f)
    }

    /** Sets the player volume (0..1); 0 also flags muted. */
    fun setVolume(value: Float) {
        volumeState = value.coerceIn(0f, 1f)
        isMuted = volumeState == 0f
        exoPlayer?.volume = volumeState
    }

    /** Set playback speed for long-press 2x gestures / the long-press menu. */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeedState = speed.coerceIn(0.5f, 3f)
        exoPlayer?.setPlaybackSpeed(playbackSpeedState)
    }

    fun clearPlaybackError() {
        playbackError = null
    }

    /** Re-prepare the currently bound media item (used by the retry button). */
    fun retryCurrent() {
        val exo = exoPlayer ?: return
        playbackError = null
        firstFrameRendered = false
        shouldPlay = true
        userPaused = false
        exo.prepare()
        syncPlayWhenReady()
    }

    /** UI entry/exit for a screen that owns the player (feed, search results). */
    fun setScreenActive(active: Boolean) {
        if (active) {
            activeScreenCount++
            screenActive = true
            shouldPlay = !userPaused && boundVideoId != null
            syncPlayWhenReady()
        } else {
            activeScreenCount = (activeScreenCount - 1).coerceAtLeast(0)
            if (activeScreenCount == 0) {
                screenActive = false
                shouldPlay = false
                exoPlayer?.pause()
            }
        }
    }

    /** Temporarily pauses the current item without forgetting the user intent. */
    fun pausePlayback() {
        shouldPlay = false
        exoPlayer?.pause()
    }

    /** Resumes the current item (e.g. after closing the comment sheet). */
    fun resumePlayback() {
        if (boundVideoId == null) return
        shouldPlay = true
        userPaused = false
        syncPlayWhenReady()
    }

    /** Leave one player screen; only the last one pauses/detaches the player. */
    fun releaseScreen() {
        setScreenActive(false)
        if (activeScreenCount == 0) detach()
    }

    /** Pause and drop the current media item (e.g. the feed has no current video). */
    fun detach() {
        shouldPlay = false
        val exo = exoPlayer ?: return
        exo.pause()
        exo.stop()
        exo.clearMediaItems()
        currentMediaKey = null
        boundVideoId = null
        isPlaying = false
        isBuffering = false
        firstFrameRendered = false
        playbackError = null
        durationMs = 0L
        videoWidth = 0
        videoHeight = 0
        userPaused = false
    }

    /** Called from the Activity lifecycle ON_PAUSE. */
    fun onAppPaused() {
        appForeground = false
        exoPlayer?.pause()
    }

    /** Called from the Activity lifecycle ON_RESUME. */
    fun onAppResumed() {
        appForeground = true
        syncPlayWhenReady()
    }

    fun currentPositionMs(): Long = exoPlayer?.currentPosition ?: 0L

    /** Duration of the current media item in milliseconds (0 when unknown). */
    fun currentDurationMs(): Long {
        val exo = exoPlayer ?: return durationMs
        val value = exo.duration
        return if (value > 0L) value else durationMs
    }

    fun progressFraction(): Float {
        val exo = exoPlayer ?: return 0f
        val duration = exo.duration
        if (duration <= 0L) return 0f
        return (exo.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Seek to [fraction] (0..1) of the current media duration. Used by the
     * draggable feed progress bar.
     *
     * The play / pause state is intentionally preserved: dragging while playing
     * keeps playing, dragging while paused only refreshes the frame. The video
     * also keeps playing while the comment panel is open (it is no longer
     * paused when comments open).
     */
    fun seekToFraction(fraction: Float) {
        val exo = exoPlayer ?: return
        val duration = exo.duration
        if (duration <= 0L) return
        exo.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun release() {
        val exo = exoPlayer ?: return
        exoPlayer = null
        exo.removeListener(playerListener)
        exo.release()
    }

    // -------------------------------------------------------------- internal

    private fun syncPlayWhenReady() {
        exoPlayer?.playWhenReady = shouldPlay && screenActive && appForeground
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        // Small start buffer (fast first frame) + generous steady-state buffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 12_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_800,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val created = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        created.repeatMode = Player.REPEAT_MODE_ONE
        created.volume = if (isMuted) 0f else 1f
        created.setPlaybackSpeed(playbackSpeed)
        created.addListener(playerListener)
        exoPlayer = created
        return created
    }

    private fun friendlyPlaybackError(error: PlaybackException): String = when {
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            stringContext.getString(R.string.playback_error_network)

        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            stringContext.getString(R.string.playback_error_url_invalid)

        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
            stringContext.getString(R.string.playback_error_decode_unsupported)

        else -> error.message?.takeIf { it.isNotBlank() }?.let { stringContext.getString(R.string.playback_error_with_reason, it.take(80)) }
            ?: stringContext.getString(R.string.playback_error_generic)
    }
}
