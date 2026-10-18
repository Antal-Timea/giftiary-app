package app.giftiary.testing

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.SignUpOutcome
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

    /** When set, sign-out waits for it, to observe what happens while the network call runs. */
    var signOutGate: CompletableDeferred<Unit>? = null

    /** Thrown by sign-out instead of signing out, leaving the session in place. */
    var signOutFailure: Exception? = null

    override suspend fun signOut() {
        signOutCalls++
        signOutGate?.await()
        signOutFailure?.let { throw it }
        state.value = AuthState.SignedOut
    }

    // Password recovery: each call fails with its own error when set, and may be held by [gate].

    var resetFailure: AuthError? = null
    var exchangeFailure: AuthError? = null
    var setPasswordFailure: AuthError? = null

    /** The account a recovery link signs in, as the SDK does when exchanging its code. */
    var recoveryUser = AuthUser(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"), "carol@example.com")

    val resetCalls = mutableListOf<String>()
    val exchangeCalls = mutableListOf<String>()
    val setPasswordCalls = mutableListOf<String>()

    override suspend fun requestPasswordReset(email: String) {
        resetCalls += email
        gate?.await()
        resetFailure?.let { throw AuthException(it) }
    }

    override suspend fun exchangeRecoveryCode(code: String) {
        exchangeCalls += code
        gate?.await()
        exchangeFailure?.let { throw AuthException(it) }
        state.value = AuthState.SignedIn(recoveryUser)
    }

    override suspend fun setNewPassword(password: String) {
        setPasswordCalls += password
        gate?.await()
        setPasswordFailure?.let { throw AuthException(it) }
    }
}
