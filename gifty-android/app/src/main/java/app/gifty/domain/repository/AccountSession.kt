package app.gifty.domain.repository

import app.gifty.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a signed-in account's data is open on this device. Signing in is not enough: the
 * account's data first has to be prepared, which includes taking over data created before
 * accounts existed.
 */
interface AccountSession {

    val state: StateFlow<OwnerSessionState>

    /** Prepares the failed account again; ignored unless preparation failed. */
    fun retry()

    /** Closes the account's data at once, then signs out. */
    fun signOut()
}

sealed interface OwnerSessionState {
    /** Nothing is open yet: a saved session is being checked or an account is being prepared. */
    data object Restoring : OwnerSessionState
    data object SignedOut : OwnerSessionState
    data class Ready(val user: AuthUser) : OwnerSessionState

    /** Signed in, but the account's data could not be prepared; nothing is open. */
    data class Failed(val user: AuthUser) : OwnerSessionState
}
