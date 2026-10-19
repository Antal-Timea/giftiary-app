package app.giftiary.data.auth

import android.content.Intent
import app.giftiary.domain.repository.AuthError

/** An opened auth callback link: a confirmed sign-up, or the return from a provider's sign-in. */
sealed interface AuthCallback {

    class Code(val value: String) : AuthCallback {
        // The code signs the account in once exchanged; it must not reach a log.
        override fun toString(): String = "AuthCallback.Code(redacted)"
    }

    /** The backend or the provider reported an error instead of a code. */
    data class Failed(val error: AuthError) : AuthCallback

    /** A callback link with neither a code nor an error, which there is nothing to do for. */
    data object Empty : AuthCallback
}

/**
 * The auth callback an intent carries, or null when it is not one. Recovery links have a path
 * of their own and are decided before this (see [app.giftiary.incomingLinkOf]).
 */
fun authCallbackOf(intent: Intent): AuthCallback? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val isCallback = uri.scheme.equals(AUTH_CALLBACK_SCHEME, ignoreCase = true) &&
        uri.host.equals(AUTH_CALLBACK_HOST, ignoreCase = true) &&
        uri.path == AUTH_CALLBACK_PATH
    if (!isCallback) return null
    val error = uri.getQueryParameter("error")
    val errorCode = uri.getQueryParameter("error_code")
    if (error != null || errorCode != null) {
        // Turning down the consent screen comes back as access_denied, from Google and Meta alike.
        val cancelled = error == ACCESS_DENIED && (errorCode == null || errorCode == ACCESS_DENIED)
        return AuthCallback.Failed(if (cancelled) AuthError.CANCELLED else AuthError.PROVIDER_FAILED)
    }
    val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return AuthCallback.Empty
    return AuthCallback.Code(code)
}

private const val ACCESS_DENIED = "access_denied"
