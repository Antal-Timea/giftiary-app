package app.gifty.domain.usecase

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
