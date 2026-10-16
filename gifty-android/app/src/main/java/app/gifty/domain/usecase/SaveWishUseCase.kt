package app.gifty.domain.usecase

import app.gifty.domain.model.Money
import app.gifty.domain.repository.WishDetails
import app.gifty.domain.repository.WishlistRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes user-entered wish details before they are stored: the name is
 * required, and optional text left blank is stored as nothing.
 */
class SaveWishUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {

    /** @throws app.gifty.domain.repository.WishlistNotFoundException if the wishlist is gone. */
    suspend fun add(wishlistId: UUID, input: WishInput): SaveWishResult {
        val details = input.toDetails() ?: return SaveWishResult.BLANK_NAME
        repository.addWish(wishlistId, details)
        return SaveWishResult.SAVED
    }

    /**
     * @throws app.gifty.domain.repository.WishNotFoundException if the wish is gone.
     * @throws app.gifty.domain.repository.WishlistNotFoundException if its wishlist is gone.
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
        )
    }

    private fun String.trimToNull(): String? = trim().ifEmpty { null }
}

data class WishInput(
    val name: String,
    val note: String,
    val estimatedPrice: Money?,
    val productUrl: String,
)

enum class SaveWishResult {
    SAVED,
    BLANK_NAME,
}
