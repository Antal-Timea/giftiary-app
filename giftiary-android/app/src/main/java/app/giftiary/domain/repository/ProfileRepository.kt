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

    /** The name and the avatar's path together, in one read. */
    suspend fun profile(): OwnProfile

    /** The avatar at [path], which must be the account's own. */
    suspend fun avatar(path: String): ByteArray

    /**
     * Makes [photo], a picture imported with [PhotoKind.PERSON], the avatar and returns its path.
     * The avatar only counts as changed once this returns; after a failure the same call can be
     * made again.
     *
     * @throws GiftPhotoUnavailableException if [photo] is no longer on this device.
     */
    suspend fun setAvatar(photo: String): String

    suspend fun removeAvatar()
}

/** The avatar is never shown to anyone but its owner, so it is not part of what sharing reads. */
data class OwnProfile(val displayName: String?, val avatarPath: String?)

enum class ProfileFailure {
    /** The server has no profile for the account, e.g. it was deleted. */
    UNAVAILABLE,

    /** No answer; trying again may work. */
    NETWORK,

    /** Anything else, e.g. an expired session or an unreadable answer; trying again may work. */
    SERVER,
}

class ProfileException(val failure: ProfileFailure) : Exception(failure.name)
