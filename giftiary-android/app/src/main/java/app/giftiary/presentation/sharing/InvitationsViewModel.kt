package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Invitations to other people's wishlists: the inbox on Lists, and the count for the navigation badge. */
@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val inbox: InvitationInbox,
    private val sharing: WishlistSharingRepository,
) : ViewModel() {

    val invites: StateFlow<OnlineState<List<RecipientInvite>>> =
        inbox.state.stateIn(viewModelScope, SharingStarted.Eagerly, OnlineState())

    /** How many invitations wait, as far as the last load knows. */
    val count: StateFlow<Int> = inbox.state
        .map { (it.content as? OnlineContent.Loaded)?.value?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _responses = MutableStateFlow(InvitationResponses())
    val responses: StateFlow<InvitationResponses> = _responses.asStateFlow()

    fun refresh() {
        viewModelScope.launch { inbox.refresh() }
    }

    /** Joins the list; [InvitationResponses.accepted] then names it, to be opened. */
    fun onAccept(inviteId: UUID) = respond(inviteId) {
        val wishlistId = sharing.acceptInvite(inviteId)
        _responses.update { it.copy(accepted = wishlistId) }
    }

    fun onDecline(inviteId: UUID) = respond(inviteId) { sharing.declineInvite(inviteId) }

    fun onAcceptedOpened() {
        _responses.update { it.copy(accepted = null) }
    }

    fun onMessageShown() {
        _responses.update { it.copy(message = null) }
    }

    private fun respond(inviteId: UUID, block: suspend () -> Unit) {
        if (inviteId in _responses.value.responding) return
        _responses.update { it.copy(responding = it.responding + inviteId) }
        viewModelScope.launch {
            val message = try {
                block()
                inbox.remove(inviteId)
                null
            } catch (e: SharingException) {
                when (e.failure) {
                    // Withdrawn, expired, or the list is full or gone: it cannot be answered any more.
                    SharingFailure.UNAVAILABLE -> InvitationMessage.GONE.also { inbox.remove(inviteId) }
                    SharingFailure.NETWORK -> InvitationMessage.NETWORK
                    else -> InvitationMessage.SERVER
                }
            }
            _responses.update { it.copy(responding = it.responding - inviteId, message = message ?: it.message) }
            inbox.refresh()
        }
    }
}

data class InvitationResponses(
    /** Invitations being accepted or declined. */
    val responding: Set<UUID> = emptySet(),
    /** The wishlist just joined, to be opened once. */
    val accepted: UUID? = null,
    val message: InvitationMessage? = null,
)

enum class InvitationMessage { GONE, NETWORK, SERVER }
