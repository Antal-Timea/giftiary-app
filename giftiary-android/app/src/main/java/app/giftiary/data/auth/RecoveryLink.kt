package app.giftiary.data.auth

import android.content.Intent

/** An opened recovery link: its one-time code, or a link to the recovery path without a usable one. */
sealed interface RecoveryLink {

    class Code(val value: String) : RecoveryLink {
        // The code signs the account in once exchanged; it must not reach a log.
        override fun toString(): String = "RecoveryLink.Code(redacted)"
    }

    data object Invalid : RecoveryLink
}

/**
 * The recovery link an intent carries, or null when it is not one, e.g. a sign-up confirmation,
 * which keeps going to [AuthCallbackHandler].
 */
fun recoveryLinkOf(intent: Intent): RecoveryLink? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val isRecovery = uri.scheme.equals(AUTH_CALLBACK_SCHEME, ignoreCase = true) &&
        uri.host.equals(AUTH_CALLBACK_HOST, ignoreCase = true) &&
        uri.path == AUTH_RECOVERY_PATH
    if (!isRecovery) return null
    // PKCE recovery returns the code as a query parameter; a fragment would mean another flow.
    if (uri.fragment != null) return RecoveryLink.Invalid
    val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return RecoveryLink.Invalid
    return RecoveryLink.Code(code)
}
