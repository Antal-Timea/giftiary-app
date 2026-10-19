package app.giftiary.data.auth

import android.content.Intent
import android.util.Log
import app.giftiary.di.ApplicationScope
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Completes sign-ins that come back through [AUTH_CALLBACK_URL]: sign-ups confirmed through the
 * email link, and provider sign-ins. The code in the link is exchanged for a session; the auth
 * state then switches to signed in. [ProviderSignIn] follows the outcome.
 */
class AuthCallbackHandler @Inject constructor(
    private val authRepository: AuthRepository,
    private val providerSignIn: ProviderSignIn,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** Whether [intent] was an auth callback; its link then has no further use and can be dropped. */
    fun handle(intent: Intent): Boolean {
        when (val callback = authCallbackOf(intent) ?: return false) {
            is AuthCallback.Failed -> providerSignIn.onCallbackFailed(callback.error)
            is AuthCallback.Code -> {
                val exchange = providerSignIn.onExchangeStarted()
                scope.launch {
                    val error = try {
                        authRepository.completeSignIn(callback.value)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AuthException) {
                        e.error
                    } catch (_: Exception) {
                        AuthError.UNKNOWN
                    }
                    // A link opened on another device, or opened twice, cannot be exchanged here. For a
                    // sign-up the email is confirmed regardless, so signing in normally still works.
                    // Never log the link: it carries the one-time code.
                    if (error != null) Log.w(TAG, "Auth callback could not be completed: $error")
                    providerSignIn.onExchangeFinished(exchange, error)
                }
            }
            AuthCallback.Empty -> Unit
        }
        return true
    }

    private companion object {
        const val TAG = "AuthCallback"
    }
}

/** Must match the redirect URL allowed in the Supabase Auth settings and the manifest filter. */
const val AUTH_CALLBACK_SCHEME = "giftiary"
const val AUTH_CALLBACK_HOST = "auth"
const val AUTH_CALLBACK_PATH = "/callback"
const val AUTH_CALLBACK_URL = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST$AUTH_CALLBACK_PATH"

/**
 * Where recovery emails lead. A path of its own: the recovery code must never be
 * exchanged like a confirmation, so it must never reach [AuthCallbackHandler].
 */
const val AUTH_RECOVERY_PATH = "/recovery"
const val AUTH_RECOVERY_URL = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST$AUTH_RECOVERY_PATH"
