package app.giftiary.data.auth

import android.content.Intent
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

/**
 * Completes sign-ups confirmed through the email link, which opens [AUTH_CALLBACK_URL]. The SDK
 * exchanges the code in the link for a session; the auth state then switches to signed in.
 */
class AuthCallbackHandler @Inject constructor(
    private val client: SupabaseClient,
) {

    fun handle(intent: Intent) {
        client.handleDeeplinks(
            intent = intent,
            // A link opened on another device, or opened twice, cannot be exchanged here. The email
            // is confirmed regardless, so signing in normally still works. Never log the intent:
            // its data carries the one-time code.
            onError = { Log.w(TAG, "Auth callback could not be completed: ${it::class.simpleName}") },
        )
    }

    private companion object {
        const val TAG = "AuthCallback"
    }
}

/** Must match the redirect URL allowed in the Supabase Auth settings and the manifest filter. */
const val AUTH_CALLBACK_SCHEME = "giftiary"
const val AUTH_CALLBACK_HOST = "auth"
const val AUTH_CALLBACK_URL = "$AUTH_CALLBACK_SCHEME://$AUTH_CALLBACK_HOST/callback"
