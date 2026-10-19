package app.giftiary.domain.repository

import app.giftiary.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

/** Whether a signed-in account's data is open on this device. */
interface AccountSession {

    val state: StateFlow<OwnerSessionState>

    /** Closes the account's data at once, then signs out. */
    fun signOut()
}

sealed interface OwnerSessionState {
    /** Nothing is open yet: a saved session is being checked, or a password recovery holds it closed. */
    data object Restoring : OwnerSessionState
    data object SignedOut : OwnerSessionState
    data class Ready(val user: AuthUser) : OwnerSessionState
}
