package app.gifty.data.remote

import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteWish
import app.gifty.data.remote.dto.RemoteWishlist
import app.gifty.domain.model.Occasion
import java.util.UUID

/**
 * Checks backend rows against what Room and the domain rely on, before any of them is stored.
 * The backend enforces most of it too, but not everything (e.g. 31 February passes its range
 * checks), and a row it lets through must not reach Room as something the app cannot read.
 *
 * Every violation throws [RemoteException] with [RemoteFailure.SERVER]: the backend's data is
 * unusable as it is, so nothing from it may be applied.
 */
object RemoteRowValidator {

    fun validate(person: RemotePerson) {
        requireVersion(person)
    }

    fun validate(occasion: RemoteOccasion) {
        requireVersion(occasion)
        require(Occasion.dateProblem(occasion.day, occasion.month, occasion.year) == null)
    }

    fun validate(idea: RemoteGiftIdea) {
        requireVersion(idea)
        requireMoney(idea.estimatedPriceAmountMinor, idea.estimatedPriceCurrencyCode)
        requireMoney(idea.actualPriceAmountMinor, idea.actualPriceCurrencyCode)
    }

    fun validate(gift: RemoteGift) {
        requireVersion(gift)
        requireMoney(gift.priceAmountMinor, gift.priceCurrencyCode)
    }

    fun validate(wishlist: RemoteWishlist) {
        requireVersion(wishlist)
    }

    fun validate(wish: RemoteWish) {
        requireVersion(wish)
        requireMoney(wish.estimatedPriceAmountMinor, wish.estimatedPriceCurrencyCode)
    }

    /** A wishlist's person never changes in the app, so a backend row that moved it is not applied. */
    fun requireSamePerson(wishlist: RemoteWishlist, localPersonId: UUID?) {
        require(wishlist.personId == localPersonId)
    }

    /** A wish stays on its wishlist in the app, so a backend row that moved it is not applied. */
    fun requireSameWishlist(wish: RemoteWish, localWishlistId: UUID) {
        require(wish.wishlistId == localWishlistId)
    }

    /** An occasion's person never changes in the app, so a backend row that moved it is not applied. */
    fun requireSamePerson(occasion: RemoteOccasion, localPersonId: UUID?) {
        require(occasion.personId == localPersonId)
    }

    /**
     * A person's occasion fits only an idea for that person; a general occasion (no person) fits
     * any. [occasionPersonId] is the person of the idea's occasion as Room will hold it.
     */
    fun requireOccasionFits(idea: RemoteGiftIdea, occasionPersonId: UUID?) {
        require(occasionPersonId == null || occasionPersonId == idea.recipientPersonId)
    }

    /** A person's occasion fits only a gift that person gave or received. */
    fun requireOccasionFits(gift: RemoteGift, occasionPersonId: UUID?) {
        require(occasionPersonId == null || occasionPersonId == gift.giverPersonId || occasionPersonId == gift.recipientPersonId)
    }

    private fun requireVersion(row: RemoteRow) {
        require(row.version >= 1)
    }

    // The same rules as Money: both parts or neither, never negative, an ISO 4217-style code.
    private fun requireMoney(amountMinor: Long?, currencyCode: String?) {
        if (amountMinor == null && currencyCode == null) return
        require(amountMinor != null && currencyCode != null && amountMinor >= 0 && CURRENCY_CODE.matches(currencyCode))
    }

    private fun require(valid: Boolean) {
        if (!valid) throw RemoteException(RemoteFailure.SERVER)
    }

    private val CURRENCY_CODE = Regex("[A-Z]{3}")
}
