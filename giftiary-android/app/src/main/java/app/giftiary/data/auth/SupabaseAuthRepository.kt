package app.giftiary.data.auth

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.SignUpOutcome
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase Auth behind [AuthRepository]. The SDK persists and refreshes the session itself; this
 * class never stores, logs or exposes tokens.
 *
 * Signing in does not open the account's local data by itself; [app.giftiary.data.user.OwnerSession]
 * decides when it does.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val auth: Auth,
    private val providerPages: ProviderPageResolver,
) : AuthRepository {

    override val authState: Flow<AuthState> = auth.sessionStatus.toAuthStates(::storedUser)

    override suspend fun currentUser(): AuthUser? =
        (authState.first { it != AuthState.Restoring } as? AuthState.SignedIn)?.user

    override suspend fun signIn(email: String, password: String) = asAuthFailure {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): SignUpOutcome = asAuthFailure {
        val returnedUser = auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        signUpOutcome(sessionStarted = auth.currentSessionOrNull() != null, returnedUser = returnedUser)
    }

    // The SDK's own OAuth sign-in URL, PKCE verifier included, resolved to the provider's page so
    // the activity can open that page in a Custom Tab of its own task. No extra scopes: Giftiary
    // uses no provider API, so it asks for nothing beyond the sign-in itself.
    override suspend fun providerSignInUrl(provider: AuthProvider): String = asAuthFailure {
        val authorizeUrl = when (provider) {
            // A shared phone can pick another account after a sign-out instead of the browser's last one.
            AuthProvider.GOOGLE -> auth.getOAuthUrl(Google, AUTH_CALLBACK_URL) { queryParams["prompt"] = "select_account" }
            AuthProvider.FACEBOOK -> auth.getOAuthUrl(Facebook, AUTH_CALLBACK_URL)
        }
        providerPages.providerPage(authorizeUrl)
    }

    override suspend fun completeSignIn(code: String) = asAuthFailure {
        auth.exchangeCodeForSession(code)
        Unit
    }

    override suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Offline: the server session simply expires, but this device must be signed out now.
            auth.clearSession()
        }
    }

    override suspend fun requestPasswordReset(email: String) = asAuthFailure {
        auth.resetPasswordForEmail(email, AUTH_RECOVERY_URL)
    }

    override suspend fun exchangeRecoveryCode(code: String) = asAuthFailure(Throwable::toRecoveryError) {
        // Saved like any session: setting the new password needs it, and so does what follows.
        auth.exchangeCodeForSession(code, true)
        Unit
    }

    override suspend fun setNewPassword(password: String) = asAuthFailure(Throwable::toRecoveryError) {
        auth.updateUser { this.password = password }
        Unit
    }

    /** The SDK's own persisted session, which outlives the transient statuses it passes through. */
    private suspend fun storedUser(): UserInfo? = try {
        auth.sessionManager.loadSession().user
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // NoSessionFoundException when signed out; an unreadable session counts as none.
        null
    }

    private suspend fun <T> asAuthFailure(
        errorOf: (Throwable) -> AuthError = Throwable::toAuthError,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(errorOf(e), e)
    }
}
