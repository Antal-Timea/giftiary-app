package app.giftiary.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.model.normalizedEmail
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileRepository
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
 *
 * Email invites are loaded on their own: if they cannot be (e.g. a server without them yet), the
 * people with access and the links still are.
 */
@HiltViewModel(assistedFactory = WishlistAccessViewModel.Factory::class)
class WishlistAccessViewModel @AssistedInject constructor(
    @Assisted private val wishlistId: UUID,
    private val sharing: WishlistSharingRepository,
    wishlists: WishlistRepository,
    private val profiles: ProfileRepository,
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

    /**
     * Asks again whether the owner has a name, e.g. after coming back from Settings. Without one,
     * a prompt suggests adding it; creating an invite never waits for it.
     */
    fun checkOwnerName() {
        if (_state.value.namePromptDismissed) return
        viewModelScope.launch {
            val missing = try {
                profiles.displayName() == null
            } catch (_: ProfileException) {
                // Unknown is not missing: no prompt rather than a wrong one.
                false
            }
            _state.update { it.copy(ownerNameMissing = missing) }
        }
    }

    fun onNamePromptDismissed() {
        _state.update { it.copy(namePromptDismissed = true) }
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
            val emailInvites = try {
                EmailInvitesContent.Loaded(sharing.emailInvites(wishlistId))
            } catch (e: SharingException) {
                EmailInvitesContent.Failed(e.failure)
            }
            // A failed refresh keeps the invites shown; the access refresh already reports it.
            _state.update { state ->
                if (emailInvites is EmailInvitesContent.Failed && state.emailInvites is EmailInvitesContent.Loaded) {
                    state
                } else {
                    state.copy(emailInvites = emailInvites)
                }
            }
        }
    }

    fun onEmailInviteOpened() {
        _state.update { it.copy(emailForm = it.emailForm ?: EmailInviteForm()) }
    }

    fun onEmailChanged(text: String) {
        _state.update { state -> state.copy(emailForm = state.emailForm?.copy(text = text, error = null)) }
    }

    fun onEmailInviteDismissed() {
        _state.update { it.copy(emailForm = null) }
    }

    /**
     * Sends the invite. Whatever the address, success reads the same: the server's answer says
     * nothing about accounts, and neither does this screen.
     */
    fun onSendEmailInvite() {
        val form = _state.value.emailForm ?: return
        if (form.isSending) return
        val email = normalizedEmail(form.text)
        if (email == null) {
            _state.update { it.copy(emailForm = form.copy(error = EmailInviteError.INVALID)) }
            return
        }
        _state.update { it.copy(emailForm = form.copy(isSending = true, error = null)) }
        viewModelScope.launch {
            try {
                val invite = sharing.createEmailInvite(wishlistId, email)
                _state.update { it.copy(emailForm = null, invitedEmail = invite.email) }
                load()
            } catch (e: SharingException) {
                val error = when (e.failure) {
                    SharingFailure.INVALID_INPUT -> EmailInviteError.NOT_ALLOWED
                    SharingFailure.INVITE_LIMIT -> EmailInviteError.LIMIT
                    SharingFailure.UNAVAILABLE -> EmailInviteError.UNAVAILABLE
                    SharingFailure.NETWORK -> EmailInviteError.NETWORK
                    SharingFailure.SERVER -> EmailInviteError.SERVER
                }
                _state.update { state -> state.copy(emailForm = state.emailForm?.copy(isSending = false, error = error)) }
            }
        }
    }

    fun onRevokeEmailInvite(inviteId: UUID) = change(inviteId) { sharing.revokeEmailInvite(inviteId) }

    fun onInvitedNoticeShown() {
        _state.update { it.copy(invitedEmail = null) }
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
    val ownerNameMissing: Boolean = false,
    /** "Not now" for this screen; it does not come back until the screen is opened again. */
    val namePromptDismissed: Boolean = false,
    val emailInvites: EmailInvitesContent = EmailInvitesContent.Loading,
    /** The email invite being written; null while closed. */
    val emailForm: EmailInviteForm? = null,
    /** The address just invited, confirmed once. */
    val invitedEmail: String? = null,
) {
    val showNamePrompt: Boolean get() = ownerNameMissing && !namePromptDismissed
}

data class EmailInviteForm(
    val text: String = "",
    val error: EmailInviteError? = null,
    val isSending: Boolean = false,
)

/** Why an email invite was not sent; none of them says anything about an account. */
enum class EmailInviteError { INVALID, NOT_ALLOWED, LIMIT, UNAVAILABLE, NETWORK, SERVER }

sealed interface EmailInvitesContent {
    data object Loading : EmailInvitesContent
    data class Loaded(val invites: List<EmailInvite>) : EmailInvitesContent
    data class Failed(val failure: SharingFailure) : EmailInvitesContent
}

sealed interface AccessContent {
    data object Loading : AccessContent
    data class Loaded(val access: WishlistAccess) : AccessContent
    data class Failed(val failure: SharingFailure) : AccessContent
}
