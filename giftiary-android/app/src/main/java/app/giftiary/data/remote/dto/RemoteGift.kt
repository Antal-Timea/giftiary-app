@file:UseSerializers(UuidSerializer::class, InstantSerializer::class, LocalDateSerializer::class)

package app.giftiary.data.remote.dto

import app.giftiary.domain.model.GiftDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A `public.gifts` row. */
@Serializable
data class RemoteGift(
    override val id: UUID,
    @SerialName("owner_user_id") override val ownerUserId: UUID,
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
    override val version: Long,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    @SerialName("last_mutation_id") override val lastMutationId: UUID?,
) : RemoteRow
