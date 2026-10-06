package app.gifty.data.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.util.UUID

/**
 * [currentUser] is the user of the session the SDK still holds. It matters for a failed refresh,
 * which usually means the device is offline: the user stays signed in, because Gifty works
 * offline and losing connectivity must not lock anyone out of their local data.
 */
internal fun SessionStatus.toAuthState(currentUser: UserInfo?): AuthState = when (this) {
    SessionStatus.Initializing -> AuthState.Restoring
    is SessionStatus.Authenticated -> signedInOrOut(session.user ?: currentUser)
    is SessionStatus.RefreshFailure -> signedInOrOut(currentUser)
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
}

private fun signedInOrOut(user: UserInfo?): AuthState =
    user?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut

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

private const val HTTP_TOO_MANY_REQUESTS = 429
