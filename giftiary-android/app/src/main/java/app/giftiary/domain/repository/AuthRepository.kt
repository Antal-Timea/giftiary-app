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

    /**
     * The page that signs in with [provider] in a browser. Opening it starts the sign-in; the
     * session arrives later through the auth callback link, as a confirmed sign-up does.
     *
     * @throws AuthException
     */
    suspend fun providerSignInUrl(provider: AuthProvider): String

    /**
     * Completes a sign-in that came back through the auth callback link: exchanges its one-time
     * [code] for a session.
     *
     * @throws AuthException
     */
    suspend fun completeSignIn(code: String)

    suspend fun signOut()

    /** Forgets the session on this device without asking the server, e.g. once the account is deleted. */
    suspend fun clearLocalSession()

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

/** Accounts of another service that can sign in to Giftiary. */
enum class AuthProvider {
    GOOGLE,
    FACEBOOK,
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

    /** The user turned down signing in at the provider. */
    CANCELLED,

    /** Nothing on the device can open the provider's sign-in page. */
    NO_BROWSER,

    /** Signing in with a provider failed for another reason. */
    PROVIDER_FAILED,
    UNKNOWN,
}

class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
