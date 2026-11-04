@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.giftiary.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

// Answers of the wishlist sharing RPCs, shaped as the functions build them.

@Serializable
data class RemoteCreatedInvite(
    @SerialName("invite_id") val inviteId: UUID,
    val token: String,
    @SerialName("expires_at") val expiresAt: Instant,
) {
    // The token is a secret and must not reach a log through this either.
    override fun toString(): String = "RemoteCreatedInvite(inviteId=$inviteId)"
}

@Serializable
data class RemoteWishlistAccess(
    val invites: List<RemoteInvite>,
    val members: List<RemoteMember>,
)

@Serializable
data class RemoteInvite(
    @SerialName("invite_id") val inviteId: UUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("expires_at") val expiresAt: Instant,
)

@Serializable
data class RemoteMember(
    @SerialName("member_user_id") val memberUserId: UUID,
    @SerialName("display_name") val displayName: String?,
    @SerialName("joined_at") val joinedAt: Instant,
)

@Serializable
data class RemoteRedeemedInvite(
    @SerialName("wishlist_id") val wishlistId: UUID,
)

@Serializable
data class RemoteSharedWishlistSummary(
    @SerialName("wishlist_id") val wishlistId: UUID,
    val name: String,
    @SerialName("owner_display_name") val ownerDisplayName: String?,
    // Absent from servers before Sharing 2E.
    @SerialName("person_name") val personName: String? = null,
)

@Serializable
data class RemoteSharedWishlist(
    @SerialName("wishlist_id") val wishlistId: UUID,
    val name: String,
    @SerialName("owner_display_name") val ownerDisplayName: String?,
    val wishes: List<RemoteSharedWish>,
    // Absent from servers before Sharing 2D.
    @SerialName("person_name") val personName: String? = null,
)

@Serializable
data class RemoteSharedWish(
    @SerialName("wish_id") val wishId: UUID,
    val name: String,
    val note: String?,
    @SerialName("estimated_price_amount_minor") val estimatedPriceAmountMinor: Long?,
    @SerialName("estimated_price_currency_code") val estimatedPriceCurrencyCode: String?,
    @SerialName("product_url") val productUrl: String?,
    @SerialName("reservation_state") val reservationState: String,
)
