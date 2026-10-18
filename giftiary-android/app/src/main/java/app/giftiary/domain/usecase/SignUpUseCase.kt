package app.giftiary.domain.usecase

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.SignUpOutcome
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    /** [confirmation] is the password typed a second time; it never leaves the device. */
    suspend operator fun invoke(email: String, password: String, confirmation: String): SignUpResult {
        val trimmedEmail = email.trim()
        val errors = buildSet {
            emailError(trimmedEmail)?.let(::add)
            addAll(newPasswordErrors(password, confirmation))
        }
        if (errors.isNotEmpty()) return SignUpResult.Invalid(errors)

        return try {
            when (repository.signUp(trimmedEmail, password)) {
                SignUpOutcome.CONFIRMATION_REQUIRED -> SignUpResult.ConfirmationRequired(trimmedEmail)
                SignUpOutcome.SIGNED_IN -> SignUpResult.SignedIn
            }
        } catch (e: AuthException) {
            SignUpResult.Failed(e.error)
        }
    }
}

sealed interface SignUpResult {
    /** Not signed in: the account only becomes usable once [email] confirms it. */
    data class ConfirmationRequired(val email: String) : SignUpResult
    data object SignedIn : SignUpResult
    data class Invalid(val errors: Set<CredentialsInputError>) : SignUpResult
    data class Failed(val error: AuthError) : SignUpResult
}
