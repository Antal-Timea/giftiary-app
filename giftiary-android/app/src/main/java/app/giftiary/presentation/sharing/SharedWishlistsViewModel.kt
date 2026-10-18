package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
private class OnlineLoader<T>(private val fetch: suspend () -> T) {

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

/** The wishlists other people shared with the user, newest server state on every visit. */
@HiltViewModel
class SharedWishlistsViewModel @Inject constructor(
    sharing: WishlistSharingRepository,
) : ViewModel() {

    private val loader = OnlineLoader { sharing.sharedWishlists() }
    val state: StateFlow<OnlineState<List<SharedWishlistSummary>>> = loader.state.asStateFlow()

    /** Called whenever the screen is shown, so a list shared or withdrawn meanwhile is seen. */
    fun refresh() = loader.load(viewModelScope)

    fun onRefreshFailureShown() {
        loader.state.update { it.copy(refreshFailure = null) }
    }
}

/** One shared wishlist, read-only apart from leaving it. */
@HiltViewModel(assistedFactory = SharedWishlistViewModel.Factory::class)
class SharedWishlistViewModel @AssistedInject constructor(
    @Assisted private val wishlistId: UUID,
    private val sharing: WishlistSharingRepository,
) : ViewModel() {

    private val loader = OnlineLoader { sharing.sharedWishlist(wishlistId) }
    val state: StateFlow<OnlineState<SharedWishlist>> = loader.state.asStateFlow()

    private val _leaving = MutableStateFlow(LeaveState())
    val leaving: StateFlow<LeaveState> = _leaving.asStateFlow()

    fun refresh() = loader.load(viewModelScope)

    fun onRefreshFailureShown() {
        loader.state.update { it.copy(refreshFailure = null) }
    }

    fun onLeaveRequested() {
        _leaving.update { it.copy(confirming = true) }
    }

    fun onLeaveDismissed() {
        _leaving.update { it.copy(confirming = false) }
    }

    fun onLeaveConfirmed() {
        if (_leaving.value.isLeaving || _leaving.value.left) return
        _leaving.update { it.copy(confirming = false, isLeaving = true, failure = null) }
        viewModelScope.launch {
            val failure = try {
                sharing.leave(wishlistId)
                null
            } catch (e: SharingException) {
                // No longer a member, or the list is gone: either way the user is out, as asked.
                e.failure.takeUnless { it == SharingFailure.UNAVAILABLE }
            }
            _leaving.update { it.copy(isLeaving = false, left = failure == null, failure = failure) }
        }
    }

    fun onLeaveFailureShown() {
        _leaving.update { it.copy(failure = null) }
    }

    private val _reservations = MutableStateFlow(ReservationActions())
    val reservations: StateFlow<ReservationActions> = _reservations.asStateFlow()

    fun onReserve(wishId: UUID) = act(wishId, reserving = true) { sharing.reserve(wishId) }

    fun onReleaseRequested(wishId: UUID) {
        if (wishId in _reservations.value.inFlight) return
        _reservations.update { it.copy(confirmingRelease = wishId) }
    }

    fun onReleaseDismissed() {
        _reservations.update { it.copy(confirmingRelease = null) }
    }

    fun onReleaseConfirmed() {
        val wishId = _reservations.value.confirmingRelease ?: return
        _reservations.update { it.copy(confirmingRelease = null) }
        act(wishId, reserving = false) { sharing.release(wishId) }
    }

    fun onReservationMessageShown() {
        _reservations.update { it.copy(message = null) }
    }

    /**
     * Never optimistic: the row keeps its last server state until the server answers, and then
     * shows exactly what it answered before the whole list is read again. That reload replaces
     * any load started before the action, so an older read never overwrites the answer.
     */
    private fun act(wishId: UUID, reserving: Boolean, call: suspend () -> ReservationState) {
        if (wishId in _reservations.value.inFlight) return
        _reservations.update { it.copy(inFlight = it.inFlight + wishId) }
        viewModelScope.launch {
            val message = try {
                val answer = call()
                loader.patch { list -> list.withReservation(wishId, answer) }
                ReservationMessage.TAKEN_BY_OTHER.takeIf { reserving && answer == ReservationState.RESERVED_BY_OTHER }
            } catch (e: SharingException) {
                when (e.failure) {
                    SharingFailure.UNAVAILABLE -> ReservationMessage.WISH_UNAVAILABLE
                    SharingFailure.NETWORK -> ReservationMessage.NETWORK
                    else -> ReservationMessage.SERVER
                }
            }
            _reservations.update { it.copy(inFlight = it.inFlight - wishId, message = message ?: it.message) }
            // The truth for the whole list; after a lost connection the call may or may not have
            // committed, so only the server can say. A plain server error assumes nothing new.
            if (message != ReservationMessage.SERVER) loader.load(viewModelScope)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(wishlistId: UUID): SharedWishlistViewModel
    }
}

data class LeaveState(
    val confirming: Boolean = false,
    val isLeaving: Boolean = false,
    /** The membership has ended; the screen should close. */
    val left: Boolean = false,
    /** Leaving failed and may work when tried again; shown once. */
    val failure: SharingFailure? = null,
)

data class ReservationActions(
    /** Wishes whose reserve or release is waiting for the server; their actions are disabled. */
    val inFlight: Set<UUID> = emptySet(),
    val confirmingRelease: UUID? = null,
    /** Feedback for a finished action, shown once. */
    val message: ReservationMessage? = null,
)

enum class ReservationMessage {
    /** Reserving lost to someone else; who is never known. */
    TAKEN_BY_OTHER,

    /** The same for a deleted or moved wish, a deleted list and ended access. */
    WISH_UNAVAILABLE,
    NETWORK,
    SERVER,
}

private fun SharedWishlist.withReservation(wishId: UUID, state: ReservationState): SharedWishlist =
    copy(wishes = wishes.map { if (it.id == wishId) it.copy(reservation = state) else it })
