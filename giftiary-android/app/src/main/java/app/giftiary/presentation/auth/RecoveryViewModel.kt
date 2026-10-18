package app.giftiary.presentation.auth

import androidx.lifecycle.ViewModel
import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.usecase.CredentialsInputError
import app.giftiary.domain.usecase.newPasswordErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The screens of a password recovery. The new password is typed into memory only: no saved
 * state, no route, and it is gone once handed to [PasswordRecovery] or the screen is left.
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val recovery: PasswordRecovery,
    private val accountSession: AccountSession,
) : ViewModel() {

    val state: StateFlow<RecoveryState> = recovery.state

    private val _input = MutableStateFlow(NewPasswordInput())
    val input: StateFlow<NewPasswordInput> = _input.asStateFlow()

    fun onPasswordChange(password: String) {
        _input.update { it.copy(password = password, passwordError = null) }
    }

    fun onConfirmationChange(confirmation: String) {
        _input.update { it.copy(confirmation = confirmation, confirmationError = null) }
    }

    fun onSubmit() {
        val current = _input.value
        val errors = newPasswordErrors(current.password, current.confirmation)
        if (errors.isNotEmpty()) {
            _input.update {
                it.copy(
                    passwordError = errors.firstOrNull { e -> e == CredentialsInputError.PASSWORD_REQUIRED || e == CredentialsInputError.PASSWORD_TOO_SHORT },
                    confirmationError = errors.firstOrNull { e ->
                        e == CredentialsInputError.CONFIRMATION_REQUIRED || e == CredentialsInputError.PASSWORDS_DO_NOT_MATCH
                    },
                )
            }
            return
        }
        recovery.setPassword(current.password)
        _input.value = NewPasswordInput()
    }

    fun onRetry() = recovery.retry()

    /** Gives up; a session the recovery holds ends through the ordinary sign-out. */
    fun onLeave() {
        _input.value = NewPasswordInput()
        recovery.leave(signOut = accountSession::signOut)
    }

    /** Someone else is signed in: signs them out so the link can be opened again. */
    fun onSignOutOtherAccount() {
        recovery.leave(signOut = {})
        accountSession.signOut()
    }
}

data class NewPasswordInput(
    val password: String = "",
    val confirmation: String = "",
    val passwordError: CredentialsInputError? = null,
    val confirmationError: CredentialsInputError? = null,
) {
    // Keeps the password out of logs.
    override fun toString(): String = "NewPasswordInput(passwordError=$passwordError, confirmationError=$confirmationError)"
}
