package app.giftiary.domain.usecase

import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.WishDetails
import app.giftiary.domain.repository.WishlistRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered wish details before they are stored: the name is
 * required, and optional text left blank is stored as nothing.
 */
class SaveWishUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {

    /**
     * @throws app.giftiary.domain.repository.WishlistNotFoundException if the wishlist is gone.
     * @throws app.giftiary.domain.repository.GiftPhotoUnavailableException if the photo is not on this device.
     */
    suspend fun add(wishlistId: UUID, input: WishInput): SaveWishResult {
        val details = input.toDetails() ?: return SaveWishResult.BLANK_NAME
        repository.addWish(wishlistId, details)
        return SaveWishResult.SAVED
    }

    /**
     * @throws app.giftiary.domain.repository.WishNotFoundException if the wish is gone.
     * @throws app.giftiary.domain.repository.WishlistNotFoundException if its wishlist is gone.
     * @throws app.giftiary.domain.repository.GiftPhotoUnavailableException if a new photo is not on this device.
     */
    suspend fun update(id: UUID, input: WishInput): SaveWishResult {
        val details = input.toDetails() ?: return SaveWishResult.BLANK_NAME
        repository.updateWish(id, details)
        return SaveWishResult.SAVED
    }

    private fun WishInput.toDetails(): WishDetails? {
        val validName = name.trim().ifEmpty { return null }
        return WishDetails(
            name = validName,
            note = note.trimToNull(),
            estimatedPrice = estimatedPrice,
            productUrl = productUrl.trimToNull(),
            photoPath = photoPath,
        )
    }

    private fun String.trimToNull(): String? = trim().ifEmpty { null }
}

data class WishInput(
    val name: String,
    val note: String,
    val estimatedPrice: Money?,
    val productUrl: String,
    val photoPath: String?,
)

enum class SaveWishResult {
    SAVED,
    BLANK_NAME,
}
