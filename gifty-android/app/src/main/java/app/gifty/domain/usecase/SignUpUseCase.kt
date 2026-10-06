package app.gifty.domain.usecase

import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.AuthException
import app.gifty.domain.repository.AuthRepository
import app.gifty.domain.repository.SignUpOutcome
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    /** [confirmation] is the password typed a second time; it never leaves the device. */
    suspend operator fun invoke(email: String, password: String, confirmation: String): SignUpResult {
        val trimmedEmail = email.trim()
        val errors = buildSet {
            emailError(trimmedEmail)?.let(::add)
            when {
                password.isEmpty() -> add(CredentialsInputError.PASSWORD_REQUIRED)
                password.length < MIN_PASSWORD_LENGTH -> add(CredentialsInputError.PASSWORD_TOO_SHORT)
            }
            when {
                confirmation.isEmpty() -> add(CredentialsInputError.CONFIRMATION_REQUIRED)
                confirmation != password -> add(CredentialsInputError.PASSWORDS_DO_NOT_MATCH)
            }
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
