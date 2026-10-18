package app.giftiary.domain.repository

/**
 * The signed-in user's own profile, online only: read from and written to the server each time,
 * never stored on the device. Fails with a [ProfileException].
 */
interface ProfileRepository {

    /** The name people the user shares with see, or null when there is none. */
    suspend fun displayName(): String?

    /** [name] must already be normalised (see `normalizeDisplayName`); null removes it. */
    suspend fun setDisplayName(name: String?)
}

enum class ProfileFailure {
    /** The server has no profile for the account, e.g. it was deleted. */
    UNAVAILABLE,

    /** No answer; trying again may work. */
    NETWORK,

    /** Anything else, e.g. an expired session or an unreadable answer; trying again may work. */
    SERVER,
}

class ProfileException(val failure: ProfileFailure) : Exception(failure.name)
