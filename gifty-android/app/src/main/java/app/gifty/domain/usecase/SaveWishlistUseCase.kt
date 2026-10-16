package app.gifty.domain.usecase

import app.gifty.domain.repository.WishlistRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes a wishlist's name before it is stored. Who a wishlist is for is chosen
 * only when it is created; renaming cannot change it.
 */
class SaveWishlistUseCase @Inject constructor(
    private val repository: WishlistRepository,
) {

    /** @throws app.gifty.domain.repository.PersonNotFoundException if [personId] is not an active person of the account. */
    suspend fun add(personId: UUID?, name: String): SaveWishlistResult {
        val validName = name.trim().ifEmpty { return SaveWishlistResult.BlankName }
        return SaveWishlistResult.Saved(repository.addWishlist(personId, validName).id)
    }

    /** @throws app.gifty.domain.repository.WishlistNotFoundException if the wishlist no longer exists. */
    suspend fun rename(id: UUID, name: String): SaveWishlistResult {
        val validName = name.trim().ifEmpty { return SaveWishlistResult.BlankName }
        repository.renameWishlist(id, validName)
        return SaveWishlistResult.Saved(id)
    }
}

sealed interface SaveWishlistResult {
    data class Saved(val wishlistId: UUID) : SaveWishlistResult
    data object BlankName : SaveWishlistResult
}
