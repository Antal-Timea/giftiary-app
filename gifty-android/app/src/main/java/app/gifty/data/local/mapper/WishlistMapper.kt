package app.gifty.data.local.mapper

import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.WishEntity
import app.gifty.data.local.entity.WishlistEntity
import app.gifty.domain.model.Money
import app.gifty.domain.model.Wish
import app.gifty.domain.model.Wishlist
import java.util.UUID

fun WishlistEntity.toDomain(): Wishlist = Wishlist(
    id = id,
    personId = personId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a wishlist created on this device, which the backend has not seen yet. */
fun Wishlist.toNewEntity(ownerUserId: UUID): WishlistEntity = WishlistEntity(
    id = id,
    ownerUserId = ownerUserId,
    personId = personId,
    name = name,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

fun WishEntity.toDomain(): Wish = Wish(
    id = id,
    wishlistId = wishlistId,
    name = name,
    note = note,
    estimatedPrice = moneyOrNull(estimatedPriceAmountMinor, estimatedPriceCurrencyCode),
    productUrl = productUrl,
    photoPath = photoPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Maps a wish created on this device; its owner is always its wishlist's. */
fun Wish.toNewEntity(ownerUserId: UUID): WishEntity = WishEntity(
    id = id,
    ownerUserId = ownerUserId,
    wishlistId = wishlistId,
    name = name,
    note = note,
    estimatedPriceAmountMinor = estimatedPrice?.amountMinor,
    estimatedPriceCurrencyCode = estimatedPrice?.currencyCode,
    productUrl = productUrl,
    photoPath = photoPath,
    version = 0,
    remoteState = RemoteState.LOCAL_ONLY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

private fun moneyOrNull(amountMinor: Long?, currencyCode: String?): Money? =
    if (amountMinor != null && currencyCode != null) Money(amountMinor, currencyCode) else null
