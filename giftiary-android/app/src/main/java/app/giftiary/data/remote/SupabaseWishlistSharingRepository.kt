package app.giftiary.data.remote

import app.giftiary.data.remote.dto.RemoteCreatedInvite
import app.giftiary.data.remote.dto.RemoteEmailInvite
import app.giftiary.data.remote.dto.RemoteRecipientInvite
import app.giftiary.data.remote.dto.RemoteRedeemedInvite
import app.giftiary.data.remote.dto.RemoteSharedWish
import app.giftiary.data.remote.dto.RemoteSharedWishlist
import app.giftiary.data.remote.dto.RemoteSharedWishlistSummary
import app.giftiary.data.remote.dto.RemoteWishlistAccess
import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWish
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.NoActiveOwnerException
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishlistSharingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * [WishlistSharingRepository] over the sharing RPCs. Each call is made as the account that is
 * signed in when it starts, with that account's token only (see [RemoteAccount]).
 *
 * Nothing here is stored or synced. Failures carry no cause: an invite token travels in some of
 * these requests and answers, and must not ride along in an exception into a log or crash report.
 */
class SupabaseWishlistSharingRepository @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
    private val activeOwner: ActiveOwner,
) : WishlistSharingRepository {

    override suspend fun createInvite(wishlistId: UUID): CreatedInvite {
        val invite = rpc(
            "create_wishlist_invite",
            buildJsonObject {
                put("p_wishlist_id", wishlistId.toString())
                put("p_valid_days", INVITE_VALID_DAYS)
            },
            RemoteCreatedInvite.serializer(),
        )
        val token = InviteToken.of(invite.token) ?: throw SharingException(SharingFailure.SERVER)
        return CreatedInvite(invite.inviteId, token, invite.expiresAt)
    }

    override suspend fun revokeInvite(inviteId: UUID) {
        rpc("revoke_wishlist_invite", buildJsonObject { put("p_invite_id", inviteId.toString()) })
    }

    override suspend fun access(wishlistId: UUID): WishlistAccess {
        val access = rpc(
            "list_wishlist_access",
            buildJsonObject { put("p_wishlist_id", wishlistId.toString()) },
            RemoteWishlistAccess.serializer(),
        )
        return WishlistAccess(
            invites = access.invites.map { ActiveInvite(it.inviteId, it.createdAt, it.expiresAt) },
            members = access.members.map { WishlistMember(it.memberUserId, displayName(it.displayName), it.joinedAt) },
        )
    }

    override suspend fun removeMember(wishlistId: UUID, memberUserId: UUID) {
        rpc(
            "remove_wishlist_member",
            buildJsonObject {
                put("p_wishlist_id", wishlistId.toString())
                put("p_member_user_id", memberUserId.toString())
            },
        )
    }

    override suspend fun createEmailInvite(wishlistId: UUID, email: String): EmailInvite =
        emailInvite(
            rpc(
                "create_recipient_invite",
                buildJsonObject {
                    put("p_wishlist_id", wishlistId.toString())
                    put("p_email", email)
                },
                RemoteEmailInvite.serializer(),
            ),
        )

    override suspend fun emailInvites(wishlistId: UUID): List<EmailInvite> =
        rpc(
            "list_recipient_invites",
            buildJsonObject { put("p_wishlist_id", wishlistId.toString()) },
            ListSerializer(RemoteEmailInvite.serializer()),
        // A server from before closed invites lost their address still lists declined and expired ones.
        ).filter { it.state == PENDING }.map(::emailInvite)

    override suspend fun revokeEmailInvite(inviteId: UUID) {
        rpc("revoke_recipient_invite", buildJsonObject { put("p_invite_id", inviteId.toString()) })
    }

    override suspend fun reservedWishes(wishlistId: UUID): Set<UUID> =
        rpc(
            "get_owner_reserved_wishes",
            buildJsonObject { put("p_wishlist_id", wishlistId.toString()) },
            ListSerializer(String.serializer()),
        ).map { runCatching { UUID.fromString(it) }.getOrNull() ?: throw SharingException(SharingFailure.SERVER) }.toSet()

    override suspend fun redeem(token: InviteToken): UUID =
        rpc(
            "redeem_wishlist_invite",
            buildJsonObject { put("p_token", token.value) },
            RemoteRedeemedInvite.serializer(),
        ).wishlistId

    override suspend fun sharedWishlists(): List<SharedWishlistSummary> =
        rpc("get_shared_wishlists", JsonObject(emptyMap()), ListSerializer(RemoteSharedWishlistSummary.serializer()))
            .map { SharedWishlistSummary(it.wishlistId, it.name, displayName(it.ownerDisplayName), displayName(it.personName)) }

    override suspend fun myInvites(): List<RecipientInvite> =
        rpc("get_my_invites", JsonObject(emptyMap()), ListSerializer(RemoteRecipientInvite.serializer())).map {
            RecipientInvite(it.inviteId, it.wishlistName, displayName(it.ownerDisplayName), displayName(it.personName), it.createdAt, it.expiresAt)
        }

    override suspend fun acceptInvite(inviteId: UUID): UUID =
        rpc("respond_to_invite", respondParameters(inviteId, accept = true), RemoteRedeemedInvite.serializer()).wishlistId

    override suspend fun declineInvite(inviteId: UUID) {
        rpc("respond_to_invite", respondParameters(inviteId, accept = false))
    }

    private fun respondParameters(inviteId: UUID, accept: Boolean) = buildJsonObject {
        put("p_invite_id", inviteId.toString())
        put("p_accept", accept)
    }

    override suspend fun sharedWishlist(wishlistId: UUID): SharedWishlist {
        val list = rpc(
            "get_shared_wishlist",
            buildJsonObject { put("p_wishlist_id", wishlistId.toString()) },
            RemoteSharedWishlist.serializer(),
        )
        if (list.wishlistId != wishlistId) throw SharingException(SharingFailure.SERVER)
        return SharedWishlist(
            list.wishlistId,
            list.name,
            displayName(list.ownerDisplayName),
            list.wishes.map(::sharedWish),
            personName = displayName(list.personName),
        )
    }

    override suspend fun leave(wishlistId: UUID) {
        rpc("leave_wishlist", buildJsonObject { put("p_wishlist_id", wishlistId.toString()) })
    }

    override suspend fun reserve(wishId: UUID): ReservationState =
        reservation("reserve_wish", wishId, accepted = RESERVE_ANSWERS)

    override suspend fun release(wishId: UUID): ReservationState =
        reservation("release_wish", wishId, accepted = ReservationState.entries.toSet())

    // An action's answer decides what the user is shown, so anything unexpected is a failure,
    // never a guess; e.g. reserving cannot leave a wish available.
    private suspend fun reservation(function: String, wishId: UUID, accepted: Set<ReservationState>): ReservationState {
        val answer = rpc(function, buildJsonObject { put("p_wish_id", wishId.toString()) }, String.serializer())
        return accepted.find { it.name == answer } ?: throw SharingException(SharingFailure.SERVER)
    }

    private suspend fun rpc(function: String, parameters: JsonObject) {
        call { client -> client.postgrest.rpc(function, parameters) }
    }

    private suspend fun <T> rpc(function: String, parameters: JsonObject, serializer: KSerializer<T>): T {
        val body = call { client -> client.postgrest.rpc(function, parameters).data }
        return try {
            json.decodeFromString(serializer, body)
        } catch (e: IllegalArgumentException) {
            // SerializationException included; its message may quote the answer, so it is dropped.
            throw SharingException(SharingFailure.SERVER)
        }
    }

    // A token that expired while the device was offline is renewed by the SDK on its own shortly
    // after the connection returns. A call refused for it in the meantime waits for that once and
    // is made again; a refused token means the server ran nothing, so even a reservation is safe.
    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): T =
        try {
            val ownerId = activeOwner.requireOwnerId()
            var account: RemoteAccount? = null
            try {
                account = accounts.capture(ownerId)
                clients.call(account, block)
            } catch (e: RemoteException) {
                if (e.failure != RemoteFailure.UNAUTHENTICATED || !accounts.awaitNewToken(account, TOKEN_WAIT)) throw e
                clients.call(accounts.capture(ownerId), block)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteException) {
            throw SharingException(failureOf(e))
        } catch (_: NoActiveOwnerException) {
            throw SharingException(SharingFailure.SERVER)
        }

    private fun emailInvite(invite: RemoteEmailInvite) = EmailInvite(
        id = invite.inviteId,
        email = invite.recipientEmail,
        createdAt = invite.createdAt,
        expiresAt = invite.expiresAt,
    )

    private fun sharedWish(wish: RemoteSharedWish): SharedWish {
        val amount = wish.estimatedPriceAmountMinor
        val currency = wish.estimatedPriceCurrencyCode
        val price = when {
            amount == null && currency == null -> null
            amount != null && currency != null -> runCatching { Money(amount, currency) }.getOrNull()
                ?: throw SharingException(SharingFailure.SERVER)
            else -> throw SharingException(SharingFailure.SERVER)
        }
        return SharedWish(
            id = wish.wishId,
            name = wish.name,
            note = wish.note,
            estimatedPrice = price,
            productUrl = wish.productUrl,
            photoPath = wish.photoPath,
            reservation = ReservationState.entries.find { it.name == wish.reservationState },
        )
    }

    private companion object {
        const val INVITE_VALID_DAYS = 7

        // Longer than the SDK's 10 second wait between refresh attempts.
        val TOKEN_WAIT = 15.seconds
        const val PENDING = "PENDING"

        val RESERVE_ANSWERS = setOf(ReservationState.RESERVED_BY_ME, ReservationState.RESERVED_BY_OTHER)

        val json = Json { ignoreUnknownKeys = true }

        // Decided by SQLSTATE, as the sharing functions raise them; never by message text.
        fun failureOf(e: RemoteException): SharingFailure = when {
            e.failure == RemoteFailure.NETWORK -> SharingFailure.NETWORK
            else -> when ((e.cause as? PostgrestRestException)?.code) {
                "P0002" -> SharingFailure.UNAVAILABLE
                "P0001" -> SharingFailure.INVITE_LIMIT
                "22023" -> SharingFailure.INVALID_INPUT
                "23505" -> SharingFailure.ALREADY_MEMBER
                else -> SharingFailure.SERVER
            }
        }

        /** A missing or blank name stays absent; the screens show a neutral label instead. */
        fun displayName(raw: String?): String? = raw?.trim()?.ifEmpty { null }
    }
}
