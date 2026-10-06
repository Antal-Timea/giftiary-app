package app.gifty.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.repository.AuthError
import app.gifty.domain.usecase.CredentialsInputError
import app.gifty.domain.usecase.SignInResult
import app.gifty.domain.usecase.SignInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A successful sign-in needs no navigation: the auth gate switches to the app. */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CredentialsUiState())
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null, authError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null, authError = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, authError = null) }
        viewModelScope.launch {
            val result = try {
                signIn(state.email, state.password)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SignInResult.Failed(AuthError.UNKNOWN)
            }
            _uiState.update {
                when (result) {
                    SignInResult.SignedIn -> it.copy(isSubmitting = false)
                    is SignInResult.Invalid -> it.withInputErrors(result.errors)
                    is SignInResult.Failed -> it.copy(isSubmitting = false, authError = result.error)
                }
            }
        }
    }
}

/** Email and password, which sign-in and account creation both ask for. */
data class CredentialsUiState(
    val email: String = "",
    val password: String = "",
    val emailError: CredentialsInputError? = null,
    val passwordError: CredentialsInputError? = null,
    val authError: AuthError? = null,
    val isSubmitting: Boolean = false,
) {
    fun withInputErrors(errors: Set<CredentialsInputError>) = copy(
        isSubmitting = false,
        emailError = errors.firstOrNull { it == CredentialsInputError.EMAIL_REQUIRED || it == CredentialsInputError.EMAIL_INVALID },
        passwordError = errors.firstOrNull {
            it == CredentialsInputError.PASSWORD_REQUIRED || it == CredentialsInputError.PASSWORD_TOO_SHORT
        },
    )
}
