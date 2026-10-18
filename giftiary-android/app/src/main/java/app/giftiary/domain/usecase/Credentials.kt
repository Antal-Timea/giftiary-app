package app.giftiary.domain.usecase

/** Must match the minimum password length configured for Supabase Auth. */
const val MIN_PASSWORD_LENGTH = 8

enum class CredentialsInputError {
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    CONFIRMATION_REQUIRED,
    PASSWORDS_DO_NOT_MATCH,
}

// Only catches obvious typos; the backend decides whether an address is really usable.
private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun emailError(email: String): CredentialsInputError? = when {
    email.isEmpty() -> CredentialsInputError.EMAIL_REQUIRED
    !EMAIL_SHAPE.matches(email) -> CredentialsInputError.EMAIL_INVALID
    else -> null
}

/** A new password and the same typed again, as for a new account and for a password reset. */
internal fun newPasswordErrors(password: String, confirmation: String): Set<CredentialsInputError> = buildSet {
    when {
        password.isEmpty() -> add(CredentialsInputError.PASSWORD_REQUIRED)
        password.length < MIN_PASSWORD_LENGTH -> add(CredentialsInputError.PASSWORD_TOO_SHORT)
    }
    when {
        confirmation.isEmpty() -> add(CredentialsInputError.CONFIRMATION_REQUIRED)
        confirmation != password -> add(CredentialsInputError.PASSWORDS_DO_NOT_MATCH)
    }
}
