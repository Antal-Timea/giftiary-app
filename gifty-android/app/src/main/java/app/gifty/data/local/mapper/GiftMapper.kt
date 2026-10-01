package app.gifty.data.local.mapper

import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftWithReferences
import app.gifty.data.local.entity.RemoteState
import app.gifty.domain.model.Gift
import app.gifty.domain.model.Money
import app.gifty.domain.repository.GiftEntry
import java.util.UUID

fun GiftEntity.toDomain(): Gift = Gift(
    id = id,
    direction = direction,
    giverPersonId = giverPersonId,
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    sourceGiftIdeaId = sourceGiftIdeaId,
    name = name,
    givenAt = givenAt,
    price = moneyOrNull(priceAmountMinor, priceCurrencyCode),
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a gift created on this device, which the backend has not seen yet. */
fun Gift.toNewEntity(ownerUserId: UUID): GiftEntity = GiftEntity(
    id = id,
    ownerUserId = ownerUserId,
    direction = direction,
    giverPersonId = giverPersonId,
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    sourceGiftIdeaId = sourceGiftIdeaId,
    name = name,
    givenAt = givenAt,
    priceAmountMinor = price?.amountMinor,
    priceCurrencyCode = price?.currencyCode,
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

/** Referenced rows owned by someone else are dropped rather than shown. */
fun GiftWithReferences.toDomain(ownerUserId: UUID): GiftEntry = GiftEntry(
    gift = gift.toDomain(),
    giver = giver?.takeIf { it.ownerUserId == ownerUserId }?.toDomain(),
    recipient = recipient?.takeIf { it.ownerUserId == ownerUserId }?.toDomain(),
    occasion = occasion?.takeIf { it.ownerUserId == ownerUserId }?.toDomain(),
)

// Amount and currency are written together, so a half-filled pair cannot come from this app.
private fun moneyOrNull(amountMinor: Long?, currencyCode: String?): Money? =
    if (amountMinor != null && currencyCode != null) Money(amountMinor, currencyCode) else null
