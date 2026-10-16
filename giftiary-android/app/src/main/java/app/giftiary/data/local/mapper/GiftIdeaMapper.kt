package app.giftiary.data.local.mapper

import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.GiftIdeaWithReferences
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftIdeaEntry
import java.util.UUID

fun GiftIdeaEntity.toDomain(): GiftIdea = GiftIdea(
    id = id,
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    name = name,
    status = status,
    estimatedPrice = moneyOrNull(estimatedPriceAmountMinor, estimatedPriceCurrencyCode),
    actualPrice = moneyOrNull(actualPriceAmountMinor, actualPriceCurrencyCode),
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps an idea created on this device, which the backend has not seen yet. */
fun GiftIdea.toNewEntity(ownerUserId: UUID): GiftIdeaEntity = GiftIdeaEntity(
    id = id,
    ownerUserId = ownerUserId,
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    name = name,
    status = status,
    estimatedPriceAmountMinor = estimatedPrice?.amountMinor,
    estimatedPriceCurrencyCode = estimatedPrice?.currencyCode,
    actualPriceAmountMinor = actualPrice?.amountMinor,
    actualPriceCurrencyCode = actualPrice?.currencyCode,
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = null,
    deletedAt = null,
)

/** Referenced rows owned by someone else are dropped rather than shown. */
fun GiftIdeaWithReferences.toDomain(ownerUserId: UUID): GiftIdeaEntry = GiftIdeaEntry(
    idea = idea.toDomain(),
    recipient = recipient?.takeIf { it.ownerUserId == ownerUserId }?.toDomain(),
    occasion = occasion?.takeIf { it.ownerUserId == ownerUserId }?.toDomain(),
)

// Amount and currency are written together, so a half-filled pair cannot come from this app.
private fun moneyOrNull(amountMinor: Long?, currencyCode: String?): Money? =
    if (amountMinor != null && currencyCode != null) Money(amountMinor, currencyCode) else null
