package app.giftiary.data.auth

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.SignUpOutcome
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/**
 * supabase-kt reports [SessionStatus.Initializing] not only on a cold start but also whenever it
 * reloads a session it already holds, e.g. each time Giftiary returns from the background, and stays
 * there while an expiring session cannot be refreshed offline. [SessionStatus.RefreshFailure]
 * likewise only means the refresh failed for now. Neither signs anyone out: as long as the SDK
 * still keeps the session in its storage, Giftiary stays signed in and usable with its local data.
 *
 * Only [SessionStatus.NotAuthenticated] signs out. The SDK reaches it after a sign-out, or after
 * the backend rejected the refresh token, and in both cases deletes the stored session first.
 *
 * [storedUser] reads the user of the session the SDK has persisted, or null if there is none.
 */
internal fun Flow<SessionStatus>.toAuthStates(storedUser: suspend () -> UserInfo?): Flow<AuthState> =
    map { status ->
        when (status) {
            is SessionStatus.Authenticated -> signedInOrOut(status.session.user ?: storedUser())
            SessionStatus.Initializing -> storedUser()?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.Restoring
            is SessionStatus.RefreshFailure -> signedInOrOut(storedUser())
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        }
    }.distinctUntilChanged()

private fun signedInOrOut(user: UserInfo?): AuthState =
    user?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut

/**
 * Interprets a completed email sign-up. supabase-kt returns the user in both cases and imports a
 * session only when the backend confirmed the account immediately, so the session decides.
 */
internal fun signUpOutcome(sessionStarted: Boolean, returnedUser: UserInfo?): SignUpOutcome = when {
    sessionStarted -> SignUpOutcome.SIGNED_IN
    // With email confirmation on, Supabase answers an address that already has a confirmed
    // account with a placeholder user without identities rather than an error.
    returnedUser?.identities?.isEmpty() == true -> throw AuthException(AuthError.EMAIL_ALREADY_REGISTERED)
    else -> SignUpOutcome.CONFIRMATION_REQUIRED
}

internal fun UserInfo.toAuthUser() = AuthUser(id = UUID.fromString(id), email = email)

internal fun Throwable.toAuthError(): AuthError = when (this) {
    is AuthWeakPasswordException -> AuthError.WEAK_PASSWORD
    is AuthRestException -> authErrorFor(errorCode, statusCode)
    is RestException -> if (statusCode == HTTP_TOO_MANY_REQUESTS) AuthError.RATE_LIMITED else AuthError.UNKNOWN
    // Ktor's timeouts and the SDK's request failures are both IOExceptions.
    is IOException -> AuthError.NETWORK_UNAVAILABLE
    else -> AuthError.UNKNOWN
}

internal fun authErrorFor(code: AuthErrorCode?, statusCode: Int): AuthError = when (code) {
    AuthErrorCode.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
    AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthError.EMAIL_ALREADY_REGISTERED
    AuthErrorCode.EmailNotConfirmed -> AuthError.EMAIL_NOT_CONFIRMED
    AuthErrorCode.WeakPassword, AuthErrorCode.SamePassword -> AuthError.WEAK_PASSWORD
    // The password length is validated before sending, so a failed validation is the address.
    AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> AuthError.INVALID_EMAIL
    AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> AuthError.RATE_LIMITED
    AuthErrorCode.RequestTimeout -> AuthError.NETWORK_UNAVAILABLE
    else -> if (statusCode == HTTP_TOO_MANY_REQUESTS) AuthError.RATE_LIMITED else AuthError.UNKNOWN
}

/**
 * For completing a recovery link and setting its new password: whatever says the link or its
 * session is no longer good is one answer, so the user is offered a new link.
 */
internal fun Throwable.toRecoveryError(): AuthError {
    val code = (this as? AuthRestException)?.errorCode
    return when (code) {
        AuthErrorCode.FlowStateNotFound, AuthErrorCode.FlowStateExpired, AuthErrorCode.BadCodeVerifier,
        AuthErrorCode.OtpExpired, AuthErrorCode.SessionNotFound, AuthErrorCode.BadJwt,
        -> AuthError.LINK_INVALID
        AuthErrorCode.SamePassword -> AuthError.SAME_PASSWORD
        AuthErrorCode.WeakPassword -> AuthError.WEAK_PASSWORD
        else -> toAuthError()
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429
