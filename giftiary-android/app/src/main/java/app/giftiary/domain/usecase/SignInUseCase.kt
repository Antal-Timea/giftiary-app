package app.giftiary.domain.usecase

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val repository: AuthRepository,
) {

    suspend operator fun invoke(email: String, password: String): SignInResult {
        val trimmedEmail = email.trim()
        val errors = buildSet {
            emailError(trimmedEmail)?.let(::add)
            // No length rule here: an existing password is checked by the backend only.
            if (password.isEmpty()) add(CredentialsInputError.PASSWORD_REQUIRED)
        }
        if (errors.isNotEmpty()) return SignInResult.Invalid(errors)

        return try {
            repository.signIn(trimmedEmail, password)
            SignInResult.SignedIn
        } catch (e: AuthException) {
            SignInResult.Failed(e.error)
        }
    }
}

sealed interface SignInResult {
    data object SignedIn : SignInResult
    data class Invalid(val errors: Set<CredentialsInputError>) : SignInResult
    data class Failed(val error: AuthError) : SignInResult
}
