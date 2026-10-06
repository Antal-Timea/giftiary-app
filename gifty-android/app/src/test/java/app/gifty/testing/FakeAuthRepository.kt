package app.gifty.testing

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.AuthException
import app.gifty.domain.repository.AuthRepository
import app.gifty.domain.repository.SignUpOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class FakeAuthRepository : AuthRepository {

    val state = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val authState: StateFlow<AuthState> = state

    /** Thrown as an [AuthException] by the next sign-in or sign-up. */
    var failure: AuthError? = null

    var signUpOutcome = SignUpOutcome.CONFIRMATION_REQUIRED

    /** When set, sign-in and sign-up wait for it, to observe the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    val signInCalls = mutableListOf<Pair<String, String>>()
    val signUpCalls = mutableListOf<Pair<String, String>>()
    var signOutCalls = 0

    override suspend fun currentUser(): AuthUser? = (state.value as? AuthState.SignedIn)?.user

    override suspend fun signIn(email: String, password: String) {
        signInCalls += email to password
        gate?.await()
        failure?.let { throw AuthException(it) }
        state.value = AuthState.SignedIn(AuthUser(UUID.randomUUID(), email))
    }

    override suspend fun signUp(email: String, password: String): SignUpOutcome {
        signUpCalls += email to password
        gate?.await()
        failure?.let { throw AuthException(it) }
        if (signUpOutcome == SignUpOutcome.SIGNED_IN) {
            state.value = AuthState.SignedIn(AuthUser(UUID.randomUUID(), email))
        }
        return signUpOutcome
    }

    override suspend fun signOut() {
        signOutCalls++
        state.value = AuthState.SignedOut
    }
}
