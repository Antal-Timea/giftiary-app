package app.giftiary.data.remote

import app.giftiary.data.local.entity.GiftEntity
import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.WishEntity
import app.giftiary.data.local.entity.WishlistEntity
import app.giftiary.data.remote.dto.RemoteGift
import app.giftiary.data.remote.dto.RemoteGiftIdea
import app.giftiary.data.remote.dto.RemoteOccasion
import app.giftiary.data.remote.dto.RemotePerson
import app.giftiary.data.remote.dto.RemoteWish
import app.giftiary.data.remote.dto.RemoteWishlist
import java.time.Instant
import java.time.temporal.ChronoUnit

// A backend row is, by definition, the synced state of that entity at its version. Every value is
// kept as is, nulls and tombstones included, except timestamps: Postgres keeps microseconds and
// Room milliseconds, so they are truncated here, giving the same value Room later reads back.
// Sync decisions use versions, never timestamps, so the dropped microseconds carry no meaning.

fun RemotePerson.toEntity() = PersonEntity(
    id = id,
    ownerUserId = ownerUserId,
    name = name,
    relationship = relationship,
    photoPath = photoPath,
    linkedUserId = linkedUserId,
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

fun RemoteOccasion.toEntity() = OccasionEntity(
    id = id,
    ownerUserId = ownerUserId,
    personId = personId,
    type = type,
    title = title,
    day = day,
    month = month,
    year = year,
    repeatsYearly = repeatsYearly,
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

fun RemoteGiftIdea.toEntity() = GiftIdeaEntity(
    id = id,
    ownerUserId = ownerUserId,
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
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    archivedAt = archivedAt?.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

fun RemoteGift.toEntity() = GiftEntity(
    id = id,
    ownerUserId = ownerUserId,
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
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

fun RemoteWishlist.toEntity() = WishlistEntity(
    id = id,
    ownerUserId = ownerUserId,
    personId = personId,
    name = name,
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

fun RemoteWish.toEntity() = WishEntity(
    id = id,
    ownerUserId = ownerUserId,
    wishlistId = wishlistId,
    name = name,
    note = note,
    estimatedPriceAmountMinor = estimatedPriceAmountMinor,
    estimatedPriceCurrencyCode = estimatedPriceCurrencyCode,
    productUrl = productUrl,
    photoPath = photoPath,
    version = version,
    remoteState = RemoteState.SYNCED,
    createdAt = createdAt.toRoomInstant(),
    updatedAt = updatedAt.toRoomInstant(),
    deletedAt = deletedAt?.toRoomInstant(),
)

private fun Instant.toRoomInstant(): Instant = truncatedTo(ChronoUnit.MILLIS)
