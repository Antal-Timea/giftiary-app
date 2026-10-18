package app.giftiary.domain.repository

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Starts with [AuthState.Restoring] until any saved session has been checked. */
    val authState: Flow<AuthState>

    /** Waits until any saved session has been checked. */
    suspend fun currentUser(): AuthUser?

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String)

    /** @throws AuthException */
    suspend fun signUp(email: String, password: String): SignUpOutcome

    suspend fun signOut()

    /**
     * Sends a password recovery email for [email]. Succeeds alike whether or not an account exists.
     *
     * @throws AuthException
     */
    suspend fun requestPasswordReset(email: String)

    /**
     * Completes a recovery link: exchanges its one-time [code] for a session of the account.
     *
     * @throws AuthException [AuthError.LINK_INVALID] for a used, expired or foreign link.
     */
    suspend fun exchangeRecoveryCode(code: String)

    /**
     * Sets a new password for the signed-in account.
     *
     * @throws AuthException
     */
    suspend fun setNewPassword(password: String)
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

    /** A recovery link that was used already, expired, or was opened on another device. */
    LINK_INVALID,

    /** The new password is the one the account already has. */
    SAME_PASSWORD,
    UNKNOWN,
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
