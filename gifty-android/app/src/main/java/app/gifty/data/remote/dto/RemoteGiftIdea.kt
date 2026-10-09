@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package app.gifty.data.remote.dto

import app.gifty.domain.model.GiftIdeaStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

/** A `public.gift_ideas` row. Prices are minor units (bigint) plus a currency, both or neither. */
@Serializable
data class RemoteGiftIdea(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
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
    val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("archived_at") val archivedAt: Instant?,
    @SerialName("deleted_at") val deletedAt: Instant?,
    @SerialName("last_mutation_id") val lastMutationId: UUID?,
) : RemoteRow
