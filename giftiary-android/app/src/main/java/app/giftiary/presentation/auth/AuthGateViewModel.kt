package app.giftiary.presentation.auth

import androidx.lifecycle.ViewModel
import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Decides between the auth flow and the app, which opens only for [OwnerSessionState.Ready]. */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val session: AccountSession,
    private val recovery: PasswordRecovery,
) : ViewModel() {

    val state: StateFlow<OwnerSessionState> = session.state

    /** A password recovery under way covers everything else, the auth flow and the app alike. */
    val recoveryState: StateFlow<RecoveryState> = recovery.state

    val passwordChanged: StateFlow<Boolean> = recovery.passwordChanged

    fun onPasswordChangedShown() = recovery.onPasswordChangedShown()

    fun signOut() = session.signOut()
}
