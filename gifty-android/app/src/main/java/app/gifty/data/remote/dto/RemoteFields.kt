@file:UseSerializers(UuidSerializer::class, InstantSerializer::class, LocalDateSerializer::class)

package app.gifty.data.remote.dto

import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// The desired state of a row, as written by sync: every column the client decides, nulls and the
// tombstone included, so a write always describes the whole row rather than a partial change.
// Deliberately absent: id and owner (set by the writer, and only on insert), version and the
// timestamps (set by the server), and the mutation id (supplied with each write).

@Serializable
data class RemotePersonFields(
    val name: String,
    val relationship: String?,
    @SerialName("photo_path") val photoPath: String?,
    @SerialName("linked_user_id") val linkedUserId: UUID?,
    @SerialName("deleted_at") val deletedAt: Instant?,
)

@Serializable
data class RemoteOccasionFields(
    @SerialName("person_id") val personId: UUID?,
    val type: OccasionType,
    val title: String?,
    val day: Int,
    val month: Int,
    val year: Int?,
    @SerialName("repeats_yearly") val repeatsYearly: Boolean,
    @SerialName("deleted_at") val deletedAt: Instant?,
)

@Serializable
data class RemoteGiftIdeaFields(
    @SerialName("recipient_person_id") val recipientPersonId: UUID,
    @SerialName("occasion_id") val occasionId: UUID?,
    val name: String,
    val status: GiftIdeaStatus,
    @SerialName("estimated_price_amount_minor") val estimatedPriceAmountMinor: Long?,
    @SerialName("estimated_price_currency_code") val estimatedPriceCurrencyCode: String?,
    @SerialName("actual_price_amount_minor") val actualPriceAmountMinor: Long?,
    @SerialName("actual_price_currency_code") val actualPriceCurrencyCode: String?,
    @SerialName("photo_path") val photoPath: String?,
    @SerialName("product_url") val productUrl: String?,
    val note: String?,
    @SerialName("archived_at") val archivedAt: Instant?,
    @SerialName("deleted_at") val deletedAt: Instant?,
)

@Serializable
data class RemoteGiftFields(
    val direction: GiftDirection,
    @SerialName("giver_person_id") val giverPersonId: UUID?,
    @SerialName("recipient_person_id") val recipientPersonId: UUID?,
    @SerialName("occasion_id") val occasionId: UUID?,
    @SerialName("source_gift_idea_id") val sourceGiftIdeaId: UUID?,
    val name: String,
    @SerialName("given_at") val givenAt: LocalDate,
    @SerialName("price_amount_minor") val priceAmountMinor: Long?,
    @SerialName("price_currency_code") val priceCurrencyCode: String?,
    @SerialName("photo_path") val photoPath: String?,
    @SerialName("product_url") val productUrl: String?,
    val note: String?,
    @SerialName("deleted_at") val deletedAt: Instant?,
)

@Serializable
data class RemoteWishlistFields(
    @SerialName("person_id") val personId: UUID?,
    val name: String,
    @SerialName("deleted_at") val deletedAt: Instant?,
)

@Serializable
data class RemoteWishFields(
    @SerialName("wishlist_id") val wishlistId: UUID,
    val name: String,
    val note: String?,
    @SerialName("estimated_price_amount_minor") val estimatedPriceAmountMinor: Long?,
    @SerialName("estimated_price_currency_code") val estimatedPriceCurrencyCode: String?,
    @SerialName("product_url") val productUrl: String?,
    @SerialName("photo_path") val photoPath: String?,
    @SerialName("deleted_at") val deletedAt: Instant?,
)
