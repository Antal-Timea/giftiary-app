package app.gifty.data.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthException
import app.gifty.domain.repository.AuthRepository
import app.gifty.domain.repository.SignUpOutcome
import io.github.jan.supabase.auth.Auth
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
 * The signed-in account is not the owner of local Room rows: those still belong to the
 * device-local id from [app.gifty.data.user.CurrentUserIdProvider] until local ownership is
 * migrated in a later phase.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val auth: Auth,
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

    /** The SDK's own persisted session, which outlives the transient statuses it passes through. */
    private suspend fun storedUser(): UserInfo? = try {
        auth.sessionManager.loadSession().user
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // NoSessionFoundException when signed out; an unreadable session counts as none.
        null
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
