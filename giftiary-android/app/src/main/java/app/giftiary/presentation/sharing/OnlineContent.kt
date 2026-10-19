package app.giftiary.presentation.sharing

import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Online content: what was loaded stays shown while it is refreshed, and a failed refresh is
 * reported over it rather than replacing it.
 */
data class OnlineState<T>(
    val content: OnlineContent<T> = OnlineContent.Loading,
    val isRefreshing: Boolean = false,
    /** A refresh failed while earlier content is still shown. */
    val refreshFailure: SharingFailure? = null,
)

sealed interface OnlineContent<out T> {
    data object Loading : OnlineContent<Nothing>
    data class Loaded<T>(val value: T) : OnlineContent<T>
    data class Failed(val failure: SharingFailure) : OnlineContent<Nothing>
}

/**
 * Loads [fetch] into an [OnlineState]. A newer load replaces one still running: an answer asked
 * for before it can never overwrite what it shows.
 */
internal class OnlineLoader<T>(private val fetch: suspend () -> T) {

    val state = MutableStateFlow(OnlineState<T>())
    private var loading: Job? = null
    private var generation = 0L

    fun load(scope: CoroutineScope) {
        loading?.cancel()
        val startedIn = ++generation
        state.update { it.copy(isRefreshing = true, refreshFailure = null) }
        loading = scope.launch {
            try {
                val value = fetch()
                if (startedIn != generation) return@launch
                state.value = OnlineState(OnlineContent.Loaded(value))
            } catch (e: SharingException) {
                if (startedIn != generation) return@launch
                state.update { current ->
                    // Unavailable means access is gone: what was shown must not stay on screen.
                    if (current.content is OnlineContent.Loaded && e.failure != SharingFailure.UNAVAILABLE) {
                        current.copy(isRefreshing = false, refreshFailure = e.failure)
                    } else {
                        OnlineState(OnlineContent.Failed(e.failure))
                    }
                }
            }
        }
    }

    /** Changes what is shown by a server answer; callers load again right after. */
    fun patch(change: (T) -> T) {
        state.update { current ->
            val content = current.content as? OnlineContent.Loaded ?: return@update current
            current.copy(content = OnlineContent.Loaded(change(content.value)), isRefreshing = false)
        }
    }
}
