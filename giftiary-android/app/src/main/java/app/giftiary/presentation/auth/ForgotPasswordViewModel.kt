package app.giftiary.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.usecase.CredentialsInputError
import app.giftiary.domain.usecase.emailError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Asks for a password recovery email, starting from the address typed on the sign-in screen. */
@HiltViewModel(assistedFactory = ForgotPasswordViewModel.Factory::class)
class ForgotPasswordViewModel @AssistedInject constructor(
    @Assisted initialEmail: String,
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState(email = initialEmail))
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(email: String) {
        _state.update { it.copy(email = email, emailError = null, authError = null) }
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting || current.sent) return
        val email = current.email.trim()
        val error = emailError(email)
        if (error != null) {
            _state.update { it.copy(emailError = error) }
            return
        }
        _state.update { it.copy(isSubmitting = true, authError = null) }
        viewModelScope.launch {
            try {
                repository.requestPasswordReset(email)
                // The same answer for every address: whether an account exists is not revealed.
                _state.update { it.copy(isSubmitting = false, sent = true) }
            } catch (e: AuthException) {
                _state.update { it.copy(isSubmitting = false, authError = e.error) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(initialEmail: String): ForgotPasswordViewModel
    }
}

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: CredentialsInputError? = null,
    val authError: AuthError? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
)
