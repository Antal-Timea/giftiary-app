package app.giftiary.testing

import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred

/** The server's profile as a field; a failure in [failNext] is thrown by the next call instead. */
class FakeProfileRepository(var name: String? = null) : ProfileRepository {

    val failNext = ArrayDeque<ProfileFailure>()
    var gate: CompletableDeferred<Unit>? = null
    val saved = mutableListOf<String?>()

    override suspend fun displayName(): String? {
        gate?.await()
        failNext.removeFirstOrNull()?.let { throw ProfileException(it) }
        return name
    }

    override suspend fun setDisplayName(name: String?) {
        gate?.await()
        failNext.removeFirstOrNull()?.let { throw ProfileException(it) }
        saved += name
        this.name = name
    }
}
