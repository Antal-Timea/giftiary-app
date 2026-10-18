package app.giftiary.data.auth

import app.giftiary.di.ApplicationScope
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Whether a password recovery owns the current session, so the account must not be opened. */
interface RecoveryGate {
    val isActive: StateFlow<Boolean>
}

sealed interface RecoveryState {
    data object Idle : RecoveryState

    /** Waiting to learn whether someone is signed in already. */
    data object Checking : RecoveryState
    data object Exchanging : RecoveryState

    /** The link is exchanged; [error] is about the previous attempt to set the password. */
    data class ChoosingPassword(val error: AuthError? = null) : RecoveryState
    data object Updating : RecoveryState
    /** [afterExchange]: the link was being exchanged, so a session of the account may exist. */
    data class Failed(val reason: RecoveryFailure, val afterExchange: Boolean = true) : RecoveryState

    /** Signing out after the user gave up; idle again once signed out. */
    data object Leaving : RecoveryState
}

enum class RecoveryFailure {
    /** Used, expired, opened on another device, or its session was lost. */
    LINK_INVALID,

    /** The link could not be exchanged for want of a connection; it can be tried again. */
    NETWORK,

    /** Someone is signed in already; the link is left unused rather than switching accounts. */
    SIGNED_IN,
}

/**
 * Completing a recovery link and setting the new password.
 *
 * Exchanging the link signs the account in, but only to set a password: until that is done, or
 * the user gives up, [isActive] holds back opening the account, and with it sync and every other
 * background work. Everything here lives in memory only. A killed process forgets the link, and
 * if it already was exchanged, the account is simply signed in on the next start.
 */
@Singleton
class PasswordRecovery @Inject constructor(
    private val auth: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : RecoveryGate {

    private val _state = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    // Set together with the state, never later: an exchange's sign-in must already find it closed.
    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _passwordChanged = MutableStateFlow(false)

    /** True once after a completed recovery, for a one-time confirmation. */
    val passwordChanged: StateFlow<Boolean> = _passwordChanged.asStateFlow()

    private val lock = Any()

    // Kept only while it may still be exchanged: dropped once used or found to be invalid.
    private var code: String? = null

    /** A link opened while another recovery is under way is ignored; that one finishes first. */
    fun start(link: RecoveryLink) {
        synchronized(lock) {
            if (_state.value != RecoveryState.Idle) return
            when (link) {
                RecoveryLink.Invalid -> {
                    // Nothing was exchanged: whoever may be signed in stays as they are.
                    set(RecoveryState.Failed(RecoveryFailure.LINK_INVALID, afterExchange = false))
                    return
                }
                is RecoveryLink.Code -> {
                    code = link.value
                    set(RecoveryState.Checking)
                }
            }
        }
        scope.launch {
            // Exchanging now would sign the existing account out without asking.
            if (auth.currentUser() != null) {
                synchronized(lock) {
                    code = null
                    set(RecoveryState.Failed(RecoveryFailure.SIGNED_IN, afterExchange = false))
                }
            } else {
                exchange()
            }
        }
    }

    /**
     * Tries the same link again. Only a network failure keeps its code, so that is the only
     * failure a retry can follow; a used or refused code is gone and cannot be sent twice.
     */
    fun retry() {
        synchronized(lock) {
            if (_state.value !is RecoveryState.Failed || code == null) return
        }
        scope.launch { exchange() }
    }

    fun setPassword(password: String) {
        synchronized(lock) {
            if (_state.value !is RecoveryState.ChoosingPassword) return
            set(RecoveryState.Updating)
        }
        scope.launch {
            val next = try {
                auth.setNewPassword(password)
                _passwordChanged.value = true
                RecoveryState.Idle
            } catch (e: AuthException) {
                when (e.error) {
                    AuthError.LINK_INVALID -> RecoveryState.Failed(RecoveryFailure.LINK_INVALID)
                    else -> RecoveryState.ChoosingPassword(e.error)
                }
            }
            set(next)
        }
    }

    /**
     * The user gives up. Without a session this is simply the end; with one, the caller signs out
     * the normal way first, and the account stays closed until that sign-out has happened.
     */
    fun leave(signOut: () -> Unit) {
        val signedIn = synchronized(lock) {
            val current = _state.value
            if (current == RecoveryState.Idle || current == RecoveryState.Leaving) return
            code = null
            // Nothing was exchanged, so any session is someone's ordinary one: nothing to end.
            if (current is RecoveryState.Failed && !current.afterExchange) {
                set(RecoveryState.Idle)
                return
            }
            set(RecoveryState.Leaving)
            true
        }
        if (!signedIn) return
        scope.launch {
            if (auth.currentUser() != null) {
                signOut()
                auth.authState.first { it == AuthState.SignedOut }
            }
            set(RecoveryState.Idle)
        }
    }

    fun onPasswordChangedShown() {
        _passwordChanged.value = false
    }

    private fun set(state: RecoveryState) {
        _state.value = state
        _isActive.value = state.ownsSession()
    }

    private suspend fun exchange() {
        val current = synchronized(lock) {
            set(RecoveryState.Exchanging)
            code
        } ?: return
        val next = try {
            auth.exchangeRecoveryCode(current)
            RecoveryState.ChoosingPassword()
        } catch (e: AuthException) {
            if (e.error == AuthError.NETWORK_UNAVAILABLE) {
                RecoveryState.Failed(RecoveryFailure.NETWORK)
            } else {
                RecoveryState.Failed(RecoveryFailure.LINK_INVALID)
            }
        }
        synchronized(lock) {
            // A used or refused code is worth nothing; only a network failure keeps it for a retry.
            if ((next as? RecoveryState.Failed)?.reason != RecoveryFailure.NETWORK) code = null
            set(next)
        }
    }

    private companion object {
        // Checking and failures before any exchange are not the recovery's; everything after the
        // exchange started is, including a failure that may have left its session behind.
        fun RecoveryState.ownsSession(): Boolean = when (this) {
            RecoveryState.Idle, RecoveryState.Checking -> false
            is RecoveryState.Failed -> afterExchange
            RecoveryState.Exchanging, is RecoveryState.ChoosingPassword, RecoveryState.Updating, RecoveryState.Leaving -> true
        }
    }
}
