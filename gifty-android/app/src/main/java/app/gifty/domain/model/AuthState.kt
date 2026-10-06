package app.gifty.domain.model

import java.util.UUID

/**
 * Whether someone is signed in to their Gifty account. [Restoring] lasts until a session saved on
 * a previous launch has been checked, so the app never briefly shows the wrong side.
 */
sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/**
 * The signed-in account. Its [id] is the backend account id, which is deliberately not (yet) the
 * owner id of the rows stored on this device.
 */
data class AuthUser(
    val id: UUID,
    val email: String?,
)
