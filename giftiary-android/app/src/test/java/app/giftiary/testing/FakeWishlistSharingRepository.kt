package app.giftiary.testing

import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.EmailInviteState
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.UUID

/**
 * Answers set by the test; a failure set in [failNext] is thrown by the next call instead. Calls
 * are recorded by name, and [gate], when set, holds every call made meanwhile until completed.
 */
class FakeWishlistSharingRepository : WishlistSharingRepository {

    var access = WishlistAccess(emptyList(), emptyList())
    var createdInvite: CreatedInvite? = null
    var sharedWishlists: List<SharedWishlistSummary> = emptyList()
    var myInvites: List<RecipientInvite> = emptyList()
    var acceptedWishlistId: UUID = UUID.randomUUID()
    var sharedWishlist: SharedWishlist? = null
    var redeemedWishlistId: UUID = UUID.randomUUID()
    var reserveAnswer = ReservationState.RESERVED_BY_ME
    var releaseAnswer = ReservationState.AVAILABLE
    val failNext = ArrayDeque<SharingFailure>()
    var gate: CompletableDeferred<Unit>? = null

    val calls = mutableListOf<String>()

    // Email invites keep their own calls and failures, so link and member tests stay as they are.
    var emailInvites: List<EmailInvite> = emptyList()
    val failEmailNext = ArrayDeque<SharingFailure>()
    val emailCalls = mutableListOf<String>()
    val redeemedTokens = mutableListOf<InviteToken>()

    // The answer is what the server holds when the call is made, even if it is held back by the
    // gate and completes later, as a real read would be.
    private suspend fun <T> answer(name: String, value: () -> T): T {
        calls += name
        val failure = failNext.removeFirstOrNull()
        val result = if (failure == null) value() else null
        gate?.await()
        failure?.let { throw SharingException(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    override suspend fun createInvite(wishlistId: UUID) = answer("createInvite") { checkNotNull(createdInvite) }

    override suspend fun revokeInvite(inviteId: UUID) = answer("revokeInvite:$inviteId") {}

    override suspend fun access(wishlistId: UUID) = answer("access") { access }

    override suspend fun removeMember(wishlistId: UUID, memberUserId: UUID) = answer("removeMember:$memberUserId") {}

    override suspend fun createEmailInvite(wishlistId: UUID, email: String): EmailInvite {
        emailCalls += "create:$email"
        failEmailNext.removeFirstOrNull()?.let { throw SharingException(it) }
        val now = Instant.parse("2026-10-17T10:00:00Z")
        val invite = emailInvites.find { it.email == email && it.state == EmailInviteState.PENDING }
            ?: EmailInvite(UUID.randomUUID(), email, EmailInviteState.PENDING, now, now.plusSeconds(14 * 86_400L))
        if (invite !in emailInvites) emailInvites = emailInvites + invite
        return invite
    }

    override suspend fun emailInvites(wishlistId: UUID): List<EmailInvite> {
        emailCalls += "list"
        failEmailNext.removeFirstOrNull()?.let { throw SharingException(it) }
        return emailInvites
    }

    override suspend fun revokeEmailInvite(inviteId: UUID) {
        emailCalls += "revoke:$inviteId"
        failEmailNext.removeFirstOrNull()?.let { throw SharingException(it) }
        emailInvites = emailInvites.filterNot { it.id == inviteId }
    }

    override suspend fun redeem(token: InviteToken): UUID = answer("redeem") {
        redeemedTokens += token
        redeemedWishlistId
    }

    override suspend fun sharedWishlists() = answer("sharedWishlists") { sharedWishlists }

    override suspend fun myInvites() = answer("myInvites") { myInvites }

    override suspend fun acceptInvite(inviteId: UUID) = answer("accept:$inviteId") {
        myInvites = myInvites.filterNot { it.id == inviteId }
        acceptedWishlistId
    }

    override suspend fun declineInvite(inviteId: UUID) = answer("decline:$inviteId") {
        myInvites = myInvites.filterNot { it.id == inviteId }
    }

    override suspend fun sharedWishlist(wishlistId: UUID) = answer("sharedWishlist") { checkNotNull(sharedWishlist) }

    override suspend fun leave(wishlistId: UUID) = answer("leave:$wishlistId") {}

    override suspend fun reserve(wishId: UUID) = answer("reserve:$wishId") { reserveAnswer }

    override suspend fun release(wishId: UUID) = answer("release:$wishId") { releaseAnswer }
}
