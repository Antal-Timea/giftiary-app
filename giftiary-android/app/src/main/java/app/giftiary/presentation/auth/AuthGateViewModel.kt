package app.giftiary.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryState
import app.giftiary.data.onboarding.OnboardingPreferences
import app.giftiary.domain.repository.AccountDeletion
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.presentation.sharing.PendingWishlistJoin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decides between the auth flow and the app, which opens only for [OwnerSessionState.Ready]. */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val session: AccountSession,
    private val recovery: PasswordRecovery,
    private val deletion: AccountDeletion,
    pendingJoin: PendingWishlistJoin,
    private val onboarding: OnboardingPreferences,
) : ViewModel() {

    val state: StateFlow<OwnerSessionState> = session.state

    /**
     * Whether the signed-out side opens with the introduction: only on a new installation, and not
     * when an invite link brought the user here with something to do.
     */
    val showOnboarding: StateFlow<Boolean> = combine(onboarding.pending, pendingJoin.isPending) { pending, joining ->
        pending && !joining
    }.stateIn(viewModelScope, SharingStarted.Eagerly, onboarding.pending.value)

    fun onOnboardingDone() = onboarding.complete()

    /** A password recovery under way covers everything else, the auth flow and the app alike. */
    val recoveryState: StateFlow<RecoveryState> = recovery.state

    val passwordChanged: StateFlow<Boolean> = recovery.passwordChanged

    fun onPasswordChangedShown() = recovery.onPasswordChangedShown()

    /** A deletion being finished, or just finished, covers everything but a password recovery. */
    val deletionState: StateFlow<AccountDeletionState> = deletion.state

    fun signOut() = session.signOut()

    init {
        // Someone who has had an account open here has no use for the introduction, not even
        // after signing out; a signed-in user upgrading from before onboarding existed included.
        viewModelScope.launch {
            session.state.collect { if (it is OwnerSessionState.Ready) onboarding.complete() }
        }
        // An invite opened for the deleted account must not be redeemed by whoever signs in next.
        viewModelScope.launch {
            deletion.state.collect { if (it == AccountDeletionState.Deleted) pendingJoin.take() }
        }
    }

    fun retryDeletion() = deletion.retry()

    fun onAccountDeletedShown() = deletion.onDeletedShown()
}
