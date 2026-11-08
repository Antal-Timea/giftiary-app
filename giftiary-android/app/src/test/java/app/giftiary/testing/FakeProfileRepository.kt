package app.giftiary.testing

import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.OwnProfile
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred

/** The server's profile as fields; a failure in [failNext] is thrown by the next call instead. */
class FakeProfileRepository(var name: String? = null) : ProfileRepository {

    val failNext = ArrayDeque<ProfileFailure>()
    var gate: CompletableDeferred<Unit>? = null
    val saved = mutableListOf<String?>()

    var avatarPath: String? = null

    /** The avatar objects the server holds, by path. */
    val avatars = mutableMapOf<String, ByteArray>()

    /** Every call by name, in order, so a test can see what a screen asked for. */
    val calls = mutableListOf<String>()

    override suspend fun displayName(): String? = answer("displayName") { name }

    override suspend fun setDisplayName(name: String?) = answer("setDisplayName") {
        saved += name
        this.name = name
    }

    override suspend fun profile(): OwnProfile = answer("profile") { OwnProfile(name, avatarPath) }

    override suspend fun avatar(path: String): ByteArray = answer("avatar") {
        avatars[path] ?: throw ProfileException(ProfileFailure.SERVER)
    }

    /** Pictures [setAvatar] finds gone from the device. */
    val missingPhotos = mutableSetOf<String>()

    override suspend fun setAvatar(photo: String): String = answer("setAvatar") {
        if (photo in missingPhotos) throw GiftPhotoUnavailableException()
        avatars[photo] = photo.encodeToByteArray()
        avatarPath = photo
        photo
    }

    override suspend fun removeAvatar() = answer("removeAvatar") { avatarPath = null }

    private suspend fun <T> answer(call: String, block: () -> T): T {
        calls += call
        gate?.await()
        failNext.removeFirstOrNull()?.let { throw ProfileException(it) }
        return block()
    }
}
