package app.gifty.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.repository.AuthError
import app.gifty.domain.usecase.CredentialsInputError
import app.gifty.domain.usecase.SignUpResult
import app.gifty.domain.usecase.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(credentials = it.credentials.copy(email = email, emailError = null, authError = null)) }
    }

    // Changing either password can resolve a mismatch, so both clear the confirmation error.
    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                credentials = it.credentials.copy(password = password, passwordError = null, authError = null),
                confirmationError = null,
            )
        }
    }

    fun onPasswordConfirmationChange(confirmation: String) {
        _uiState.update {
            it.copy(
                passwordConfirmation = confirmation,
                confirmationError = null,
                credentials = it.credentials.copy(authError = null),
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.credentials.isSubmitting || state.confirmationSentTo != null) return

        _uiState.update { it.copy(credentials = it.credentials.copy(isSubmitting = true, authError = null)) }
        viewModelScope.launch {
            val result = try {
                signUp(state.credentials.email, state.credentials.password, state.passwordConfirmation)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SignUpResult.Failed(AuthError.UNKNOWN)
            }
            _uiState.update {
                when (result) {
                    // Not signed in yet: the screen moves on to "check your email".
                    is SignUpResult.ConfirmationRequired -> it.copy(
                        credentials = it.credentials.copy(isSubmitting = false),
                        confirmationSentTo = result.email,
                    )
                    SignUpResult.SignedIn -> it.copy(credentials = it.credentials.copy(isSubmitting = false))
                    is SignUpResult.Invalid -> it.copy(
                        credentials = it.credentials.withInputErrors(result.errors),
                        confirmationError = result.errors.firstOrNull {
                            it == CredentialsInputError.CONFIRMATION_REQUIRED ||
                                it == CredentialsInputError.PASSWORDS_DO_NOT_MATCH
                        },
                    )
                    is SignUpResult.Failed -> it.copy(
                        credentials = it.credentials.copy(isSubmitting = false, authError = result.error),
                    )
                }
            }
        }
    }
}

data class CreateAccountUiState(
    val credentials: CredentialsUiState = CredentialsUiState(),
    val passwordConfirmation: String = "",
    val confirmationError: CredentialsInputError? = null,
    /** Set once the account is created and waits for email confirmation. */
    val confirmationSentTo: String? = null,
)
