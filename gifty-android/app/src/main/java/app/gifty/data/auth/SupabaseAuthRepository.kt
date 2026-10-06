package app.gifty.data.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.AuthException
import app.gifty.domain.repository.AuthRepository
import app.gifty.domain.repository.SignUpOutcome
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase Auth behind [AuthRepository]. The SDK persists and refreshes the session itself; this
 * class never stores, logs or exposes tokens.
 *
 * The signed-in account is not the owner of local Room rows: those still belong to the
 * device-local id from [app.gifty.data.user.CurrentUserIdProvider] until local ownership is
 * migrated in a later phase.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val auth: Auth,
) : AuthRepository {

    override val authState: Flow<AuthState> = auth.sessionStatus
        .map { it.toAuthState(auth.currentUserOrNull()) }
        .distinctUntilChanged()

    override fun currentUser(): AuthUser? = auth.currentUserOrNull()?.toAuthUser()

    override suspend fun signIn(email: String, password: String) = asAuthFailure {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): SignUpOutcome = asAuthFailure {
        val pendingUser = auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        when {
            pendingUser == null -> SignUpOutcome.SIGNED_IN
            // With email confirmation on, Supabase answers an address that already has a confirmed
            // account with a placeholder user without identities rather than an error.
            pendingUser.identities?.isEmpty() == true -> throw AuthException(AuthError.EMAIL_ALREADY_REGISTERED)
            else -> SignUpOutcome.CONFIRMATION_REQUIRED
        }
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

    private suspend fun <T> asAuthFailure(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthException) {
        throw e
    } catch (e: Exception) {
        throw AuthException(e.toAuthError(), e)
    }
}
