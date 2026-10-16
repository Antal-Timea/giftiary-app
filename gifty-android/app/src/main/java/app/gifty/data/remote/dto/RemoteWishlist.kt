@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.gifty.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/** A `public.wishlists` row. */
@Serializable
data class RemoteWishlist(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
    @SerialName("person_id") val personId: UUID?,
    val name: String,
    override val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    @SerialName("last_mutation_id") override val lastMutationId: UUID?,
) : RemoteRow

/** A `public.wishes` row. Its owner is always its wishlist's; the backend's foreign key enforces it. */
@Serializable
data class RemoteWish(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
    @SerialName("wishlist_id") val wishlistId: UUID,
    val name: String,
    val note: String?,
    @SerialName("estimated_price_amount_minor") val estimatedPriceAmountMinor: Long?,
    @SerialName("estimated_price_currency_code") val estimatedPriceCurrencyCode: String?,
    @SerialName("product_url") val productUrl: String?,
    @SerialName("photo_path") val photoPath: String?,
    override val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    @SerialName("last_mutation_id") override val lastMutationId: UUID?,
) : RemoteRow
