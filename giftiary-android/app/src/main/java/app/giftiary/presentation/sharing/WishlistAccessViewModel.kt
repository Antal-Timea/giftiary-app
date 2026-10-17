package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistRepository
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Who can open an own wishlist, and invitations to it. Online only: everything shown comes from
 * the server, and nothing about reservations is ever asked for.
 *
 * A created invite's token exists only in this ViewModel, for sharing while the screen is open;
 * the server keeps a hash, so once the screen is gone the link cannot be shown again.
 */
@HiltViewModel(assistedFactory = WishlistAccessViewModel.Factory::class)
class WishlistAccessViewModel @AssistedInject constructor(
    @Assisted private val wishlistId: UUID,
    private val sharing: WishlistSharingRepository,
    wishlists: WishlistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WishlistAccessUiState())
    val state: StateFlow<WishlistAccessUiState> = _state.asStateFlow()

    /** The list's name for the title and the share message; null until known or once deleted. */
    val wishlistName: StateFlow<String?> = wishlists.observeWishlist(wishlistId)
        .map { it?.name }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        load()
    }

    private var loading: Job? = null

    /** Loads, or refreshes while keeping what is shown; a newer load replaces one still running. */
    fun load() {
        loading?.cancel()
        _state.update { it.copy(isLoading = true) }
        loading = viewModelScope.launch {
            try {
                val access = sharing.access(wishlistId)
                _state.update { it.copy(isLoading = false, content = AccessContent.Loaded(access)) }
            } catch (e: SharingException) {
                _state.update { state ->
                    if (state.content is AccessContent.Loaded) {
                        state.copy(isLoading = false, message = e.failure)
                    } else {
                        state.copy(isLoading = false, content = AccessContent.Failed(e.failure))
                    }
                }
            }
        }
    }

    fun onCreateInvite() {
        if (_state.value.isCreating) return
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                val invite = sharing.createInvite(wishlistId)
                _state.update { it.copy(isCreating = false, newInvite = invite) }
                load()
            } catch (e: SharingException) {
                _state.update { it.copy(isCreating = false, message = e.failure) }
            }
        }
    }

    fun onRevokeInvite(inviteId: UUID) = change(inviteId) {
        sharing.revokeInvite(inviteId)
        _state.update { state -> if (state.newInvite?.id == inviteId) state.copy(newInvite = null) else state }
    }

    fun onRemoveRequested(member: WishlistMember) {
        _state.update { it.copy(confirmingRemoval = member) }
    }

    fun onRemoveDismissed() {
        _state.update { it.copy(confirmingRemoval = null) }
    }

    fun onRemoveConfirmed() {
        val member = _state.value.confirmingRemoval ?: return
        _state.update { it.copy(confirmingRemoval = null) }
        change(member.userId) { sharing.removeMember(wishlistId, member.userId) }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Runs a change of one invite or member, then shows the server's state again. Gone already
     * (unavailable) is what the change wanted, so it is not reported.
     */
    private fun change(id: UUID, block: suspend () -> Unit) {
        if (id in _state.value.changing) return
        _state.update { it.copy(changing = it.changing + id) }
        viewModelScope.launch {
            val failure = try {
                block()
                null
            } catch (e: SharingException) {
                e.failure.takeUnless { it == SharingFailure.UNAVAILABLE }
            }
            _state.update { it.copy(changing = it.changing - id, message = failure ?: it.message) }
            load()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(wishlistId: UUID): WishlistAccessViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class WishlistAccessUiState(
    val content: AccessContent = AccessContent.Loading,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    /** The invite created on this screen, the only one whose link can still be shared. */
    val newInvite: CreatedInvite? = null,
    /** Invites and members being revoked or removed. */
    val changing: Set<UUID> = emptySet(),
    val confirmingRemoval: WishlistMember? = null,
    /** A failed action or refresh, shown once over the content. */
    val message: SharingFailure? = null,
)

sealed interface AccessContent {
    data object Loading : AccessContent
    data class Loaded(val access: WishlistAccess) : AccessContent
    data class Failed(val failure: SharingFailure) : AccessContent
}
