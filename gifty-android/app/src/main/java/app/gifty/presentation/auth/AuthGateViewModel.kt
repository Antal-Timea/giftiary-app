package app.gifty.presentation.auth

import androidx.lifecycle.ViewModel
import app.gifty.domain.repository.AccountSession
import app.gifty.domain.repository.OwnerSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Decides between the auth flow and the app. Being signed in is not enough: the app opens only
 * once the account's data is prepared ([OwnerSessionState.Ready]).
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val session: AccountSession,
) : ViewModel() {

    val state: StateFlow<OwnerSessionState> = session.state

    fun retry() = session.retry()

    fun signOut() = session.signOut()
}
