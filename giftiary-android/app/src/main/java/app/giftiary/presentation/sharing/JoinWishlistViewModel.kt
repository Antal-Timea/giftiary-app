package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Joins a shared wishlist, from an opened link (taken from [PendingWishlistJoin] once, when the
 * screen opens or while it is open) or from a pasted one. Tokens are kept in memory only, never in
 * saved state.
 *
 * Opening a link never joins by itself: joining shows the user to the list's owner, so an opened
 * invite only waits here until the user confirms it, and declining or leaving the screen drops it.
 */
@HiltViewModel
class JoinWishlistViewModel @Inject constructor(
    private val sharing: WishlistSharingRepository,
    pendingJoin: PendingWishlistJoin,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinWishlistUiState())
    val state: StateFlow<JoinWishlistUiState> = _state.asStateFlow()

    // The token of the last attempt, so a failed one can be tried again without opening the link again.
    private var lastToken: InviteToken? = null

    // An opened invite waiting for the user's answer.
    private var openedInvite: InviteToken? = null

    init {
        // Also a link opened while this screen is already open: no second screen opens for it.
        viewModelScope.launch {
            pendingJoin.isPending.filter { it }.collect {
                val join = pendingJoin.take()
                // While a join runs, a newly opened link is dropped rather than replacing what is shown.
                if (isJoiningOrJoined()) return@collect
                when (join) {
                    is PendingJoin.Invite -> {
                        openedInvite = join.token
                        lastToken = null
                        _state.update { it.copy(input = "", phase = JoinPhase.Idle, confirmingOpenedInvite = true) }
                    }
                    PendingJoin.InvalidLink -> {
                        openedInvite = null
                        _state.update {
                            it.copy(phase = JoinPhase.Failed(JoinFailure.INVALID_LINK), confirmingOpenedInvite = false)
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    fun onInputChange(input: String) {
        _state.update { state ->
            // Editing clears an error about what was typed before.
            val phase = if (state.phase is JoinPhase.Failed) JoinPhase.Idle else state.phase
            state.copy(input = input, phase = phase)
        }
    }

    fun onJoin() {
        val token = inviteTokenOfPastedText(_state.value.input)
        if (token == null) {
            _state.update { it.copy(phase = JoinPhase.Failed(JoinFailure.INVALID_LINK)) }
            return
        }
        redeem(token)
    }

    /** The user's yes to an opened invite: the only way an opened link is redeemed. */
    fun onConfirmOpenedInvite() {
        openedInvite?.let(::redeem)
    }

    /** The user's no: the opened invite is forgotten, and nothing was sent for it. */
    fun onDeclineOpenedInvite() {
        if (isJoiningOrJoined()) return
        openedInvite = null
        _state.update { it.copy(phase = JoinPhase.Idle, confirmingOpenedInvite = false) }
    }

    /** Tries the last token again, after a failure that may pass. */
    fun onRetry() {
        lastToken?.let(::redeem)
    }

    private fun redeem(token: InviteToken) {
        if (isJoiningOrJoined()) return
        lastToken = token
        _state.update { it.copy(phase = JoinPhase.Joining) }
        viewModelScope.launch {
            val phase = try {
                val wishlistId = sharing.redeem(token)
                lastToken = null
                openedInvite = null
                JoinPhase.Joined(wishlistId)
            } catch (e: SharingException) {
                JoinPhase.Failed(
                    when (e.failure) {
                        // A full list, an expired or a withdrawn invite: all the same to the guest.
                        SharingFailure.UNAVAILABLE, SharingFailure.INVITE_LIMIT -> JoinFailure.UNAVAILABLE
                        SharingFailure.NETWORK -> JoinFailure.NETWORK
                        SharingFailure.SERVER, SharingFailure.INVALID_INPUT, SharingFailure.ALREADY_MEMBER -> JoinFailure.SERVER
                    },
                )
            }
            if (phase is JoinPhase.Failed) {
                // A failure is shown on the paste form; one that may pass keeps Try again for its token.
                openedInvite = null
                if (phase.failure == JoinFailure.UNAVAILABLE) lastToken = null
            }
            _state.update {
                it.copy(phase = phase, confirmingOpenedInvite = it.confirmingOpenedInvite && phase !is JoinPhase.Failed)
            }
        }
    }

    private fun isJoiningOrJoined(): Boolean =
        _state.value.phase == JoinPhase.Joining || _state.value.phase is JoinPhase.Joined
}

data class JoinWishlistUiState(
    /** What was pasted or typed: possibly a link with a token, so in memory only. */
    val input: String = "",
    val phase: JoinPhase = JoinPhase.Idle,
    /** An opened invite waits for the user's Join or Not now; nothing has been sent for it yet. */
    val confirmingOpenedInvite: Boolean = false,
) {
    // Keeps a pasted token out of logs.
    override fun toString(): String = "JoinWishlistUiState(phase=$phase, confirmingOpenedInvite=$confirmingOpenedInvite)"
}

sealed interface JoinPhase {
    data object Idle : JoinPhase
    data object Joining : JoinPhase
    data class Joined(val wishlistId: UUID) : JoinPhase
    data class Failed(val failure: JoinFailure) : JoinPhase
}

enum class JoinFailure {
    /** Not a join link or token at all. */
    INVALID_LINK,

    /** The server refused; why is deliberately not known. */
    UNAVAILABLE,
    NETWORK,
    SERVER;

    /** Only these may pass by trying the same token again. */
    val isRetryable: Boolean get() = this == NETWORK || this == SERVER
}
