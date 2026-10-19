package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** One shared wishlist: its wishes, reserving and releasing them, and leaving the list. */
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
