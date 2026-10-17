package app.giftiary.testing

import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

/**
 * Answers set by the test; a failure set in [failNext] is thrown by the next call instead. Calls
 * are recorded by name, and [gate], when set, holds every call until completed.
 */
class FakeWishlistSharingRepository : WishlistSharingRepository {

    var access = WishlistAccess(emptyList(), emptyList())
    var createdInvite: CreatedInvite? = null
    var sharedWishlists: List<SharedWishlistSummary> = emptyList()
    var sharedWishlist: SharedWishlist? = null
    var redeemedWishlistId: UUID = UUID.randomUUID()
    val failNext = ArrayDeque<SharingFailure>()
    var gate: CompletableDeferred<Unit>? = null

    val calls = mutableListOf<String>()
    val redeemedTokens = mutableListOf<InviteToken>()

    private suspend fun <T> answer(name: String, value: () -> T): T {
        calls += name
        gate?.await()
        failNext.removeFirstOrNull()?.let { throw SharingException(it) }
        return value()
    }

    override suspend fun createInvite(wishlistId: UUID) = answer("createInvite") { checkNotNull(createdInvite) }

    override suspend fun revokeInvite(inviteId: UUID) = answer("revokeInvite:$inviteId") {}

    override suspend fun access(wishlistId: UUID) = answer("access") { access }

    override suspend fun removeMember(wishlistId: UUID, memberUserId: UUID) = answer("removeMember:$memberUserId") {}

    override suspend fun redeem(token: InviteToken): UUID = answer("redeem") {
        redeemedTokens += token
        redeemedWishlistId
    }

    override suspend fun sharedWishlists() = answer("sharedWishlists") { sharedWishlists }

    override suspend fun sharedWishlist(wishlistId: UUID) = answer("sharedWishlist") { checkNotNull(sharedWishlist) }
}
