package app.gifty.domain.repository

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Starts with [AuthState.Restoring] until any saved session has been checked. */
    val authState: Flow<AuthState>

    fun currentUser(): AuthUser?

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String)

    /** @throws AuthException */
    suspend fun signUp(email: String, password: String): SignUpOutcome

    suspend fun signOut()
}

enum class SignUpOutcome {
    /** The account exists but stays unusable until the link in the confirmation email is opened. */
    CONFIRMATION_REQUIRED,

    /** The backend confirmed the account immediately and started a session. */
    SIGNED_IN,
}

/** Expected auth failures, independent of how the backend reports them. */
enum class AuthError {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_REGISTERED,
    EMAIL_NOT_CONFIRMED,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    NETWORK_UNAVAILABLE,
    RATE_LIMITED,
    UNKNOWN,
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
