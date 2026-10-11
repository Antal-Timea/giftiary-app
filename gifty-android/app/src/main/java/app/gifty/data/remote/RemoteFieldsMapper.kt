package app.gifty.data.remote

import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePersonFields

// A local row's whole client-decided state, as written to the backend: every business column, the
// tombstone and the archive marker. Owner, version and timestamps are never part of it.

fun PersonEntity.toRemoteFields() = RemotePersonFields(
    name = name,
    relationship = relationship,
    photoPath = photoPath,
    linkedUserId = linkedUserId,
    deletedAt = deletedAt,
)

fun OccasionEntity.toRemoteFields() = RemoteOccasionFields(
    personId = personId,
    type = type,
    title = title,
    day = day,
    month = month,
    year = year,
    repeatsYearly = repeatsYearly,
    deletedAt = deletedAt,
)

fun GiftIdeaEntity.toRemoteFields() = RemoteGiftIdeaFields(
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    name = name,
    status = status,
    estimatedPriceAmountMinor = estimatedPriceAmountMinor,
    estimatedPriceCurrencyCode = estimatedPriceCurrencyCode,
    actualPriceAmountMinor = actualPriceAmountMinor,
    actualPriceCurrencyCode = actualPriceCurrencyCode,
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
)

fun GiftEntity.toRemoteFields() = RemoteGiftFields(
    direction = direction,
    giverPersonId = giverPersonId,
    recipientPersonId = recipientPersonId,
    occasionId = occasionId,
    sourceGiftIdeaId = sourceGiftIdeaId,
    name = name,
    givenAt = givenAt,
    priceAmountMinor = priceAmountMinor,
    priceCurrencyCode = priceCurrencyCode,
    photoPath = photoPath,
    productUrl = productUrl,
    note = note,
    deletedAt = deletedAt,
)
