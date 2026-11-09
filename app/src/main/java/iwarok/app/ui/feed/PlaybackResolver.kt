package iwarok.app.ui.feed

import androidx.compose.runtime.mutableStateMapOf
import iwarok.app.core.PlaybackInfo
import iwarok.app.core.QualityPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** UI state of a single video's playback resolution. */
sealed interface PlaybackUiState {
    data object Loading : PlaybackUiState
    data class Ready(val info: PlaybackInfo) : PlaybackUiState
    data class Failed(val message: String) : PlaybackUiState
}

/**
 * Resolves and caches playable URLs for the feed pages around the current one.
 *
 * Prefetching a few pages ahead makes swiping feel instant: the cover is shown
 * while [Ready] is being prepared, then the player binds as soon as the URL is
 * available.
 */
class PlaybackResolver(
    private val scope: CoroutineScope,
    private val defaultErrorMessage: String,
    private val fetch: suspend (
        videoId: String,
        quality: QualityPreference,
        refresh: Boolean,
    ) -> Result<PlaybackInfo>,
) {

    private val states = mutableStateMapOf<String, PlaybackUiState>()
    private val inFlight = mutableSetOf<String>()

    fun stateOf(videoId: String): PlaybackUiState? = states[videoId]

    /** Starts resolving [videoId] if it has not been resolved yet. */
    fun prefetch(videoId: String, quality: QualityPreference) {
        if (videoId.isBlank()) return
        val existing = states[videoId]
        if (existing is PlaybackUiState.Ready || existing is PlaybackUiState.Loading) return
        resolve(videoId, quality, refresh = false, force = false)
    }

    /** Drops the cached URL and resolves again (quality or retry change). */
    fun retry(videoId: String, quality: QualityPreference) {
        states.remove(videoId)
        resolve(videoId, quality, refresh = true, force = true)
    }

    /** Clears cached URLs so the next prefetch uses a new quality preference. */
    fun invalidateAll() {
        states.clear()
    }

    fun invalidate(videoId: String) {
        states.remove(videoId)
    }

    private fun resolve(
        videoId: String,
        quality: QualityPreference,
        refresh: Boolean,
        force: Boolean,
    ) {
        if (videoId.isBlank()) return
        if (inFlight.contains(videoId)) return
        if (!force) {
            when (states[videoId]) {
                is PlaybackUiState.Ready, is PlaybackUiState.Loading -> return
                else -> Unit
            }
        }
        inFlight += videoId
        states[videoId] = PlaybackUiState.Loading
        scope.launch {
            try {
                val result = fetch(videoId, quality, refresh)
                val info = result.getOrNull()
                val source = info?.primary
                if (info != null && source != null && source.url.isNotBlank()) {
                    states[videoId] = PlaybackUiState.Ready(info)
                } else {
                    states[videoId] = PlaybackUiState.Failed(
                        result.exceptionOrNull()?.message ?: defaultErrorMessage,
                    )
                }
            } catch (cancelled: CancellationException) {
                states.remove(videoId)
                throw cancelled
            } catch (throwable: Throwable) {
                states[videoId] = PlaybackUiState.Failed(
                    throwable.message ?: defaultErrorMessage,
                )
            } finally {
                inFlight -= videoId
            }
        }
    }
}
