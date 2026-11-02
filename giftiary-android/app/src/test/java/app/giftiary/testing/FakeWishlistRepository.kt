package app.giftiary.testing

import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.WishDetails
import app.giftiary.domain.repository.WishNotFoundException
import app.giftiary.domain.repository.WishlistNotFoundException
import app.giftiary.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

/** An in-memory account's wishlists, by the repository's contract: deleting a wishlist deletes its wishes. */
class FakeWishlistRepository : WishlistRepository {

    private val wishlists = MutableStateFlow<List<Wishlist>>(emptyList())
    private val wishes = MutableStateFlow<List<Wish>>(emptyList())

    /** The people a new wishlist may be for; any other person is refused as the real repository does. */
    var activePeopleIds: Set<UUID> = emptySet()

    /** When set, writes throw it, standing in for a failing local database. */
    var writeFailure: Exception? = null

    /** When set, observing fails with it. */
    var flowFailure: Exception? = null

    val currentWishlists: List<Wishlist> get() = wishlists.value
    val currentWishes: List<Wish> get() = wishes.value

    fun givenWishlists(vararg wishlist: Wishlist) {
        wishlists.value = wishlist.toList()
    }

    fun givenWishes(vararg wish: Wish) {
        wishes.value = wish.toList()
    }

    override fun observeWishlists(): Flow<List<Wishlist>> = wishlists.map { list ->
        flowFailure?.let { throw it }
        list.sortedBy { it.name.lowercase() }
    }

    override fun observeWishes(wishlistId: UUID): Flow<List<Wish>> = wishes.map { list ->
        flowFailure?.let { throw it }
        list.filter { it.wishlistId == wishlistId }
    }

    override fun observeWishCounts(): Flow<Map<UUID, Int>> = wishes.map { list ->
        flowFailure?.let { throw it }
        list.groupingBy { it.wishlistId }.eachCount()
    }

    /** Wishlists with a change waiting to sync. */
    val waitingToSync = MutableStateFlow<Set<UUID>>(emptySet())

    override fun observeWaitingToSync(wishlistId: UUID): Flow<Boolean> = waitingToSync.map { wishlistId in it }

    /** Wishes with a change of their own waiting to sync. */
    val wishesWaitingToSync = MutableStateFlow<Set<UUID>>(emptySet())

    override fun observeWishWaitingToSync(id: UUID): Flow<Boolean> = wishesWaitingToSync.map { id in it }

    override fun observeWishlist(id: UUID): Flow<Wishlist?> = wishlists.map { list ->
        flowFailure?.let { throw it }
        list.find { it.id == id }
    }

    override fun observeWish(id: UUID): Flow<Wish?> = wishes.map { list ->
        flowFailure?.let { throw it }
        list.find { it.id == id }
    }

    override suspend fun getWishlist(id: UUID): Wishlist? = wishlists.value.find { it.id == id }

    override suspend fun getWish(id: UUID): Wish? = wishes.value.find { it.id == id }

    override suspend fun addWishlist(personId: UUID?, name: String): Wishlist {
        writeFailure?.let { throw it }
        if (personId != null && personId !in activePeopleIds) throw PersonNotFoundException(personId)
        return wishlist(name = name, personId = personId).also { added -> wishlists.update { it + added } }
    }

    override suspend fun renameWishlist(id: UUID, name: String) {
        writeFailure?.let { throw it }
        if (wishlists.value.none { it.id == id }) throw WishlistNotFoundException(id)
        wishlists.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun deleteWishlist(id: UUID) {
        writeFailure?.let { throw it }
        wishes.update { list -> list.filterNot { it.wishlistId == id } }
        wishlists.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addWish(wishlistId: UUID, details: WishDetails): Wish {
        writeFailure?.let { throw it }
        if (wishlists.value.none { it.id == wishlistId }) throw WishlistNotFoundException(wishlistId)
        return wish(wishlistId, details.name, details.note, details.estimatedPrice, details.productUrl)
            .also { added -> wishes.update { it + added } }
    }

    override suspend fun updateWish(id: UUID, details: WishDetails) {
        writeFailure?.let { throw it }
        val existing = wishes.value.find { it.id == id } ?: throw WishNotFoundException(id)
        if (wishlists.value.none { it.id == existing.wishlistId }) throw WishlistNotFoundException(existing.wishlistId)
        wishes.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(name = details.name, note = details.note, estimatedPrice = details.estimatedPrice, productUrl = details.productUrl)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun deleteWish(id: UUID) {
        writeFailure?.let { throw it }
        wishes.update { list -> list.filterNot { it.id == id } }
    }
}

fun wishlist(name: String, personId: UUID? = null, id: UUID = UUID.randomUUID()) =
    Wishlist(id = id, personId = personId, name = name, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

fun wish(
    wishlistId: UUID,
    name: String,
    note: String? = null,
    estimatedPrice: Money? = null,
    productUrl: String? = null,
    photoPath: String? = null,
    id: UUID = UUID.randomUUID(),
) = Wish(
    id = id,
    wishlistId = wishlistId,
    name = name,
    note = note,
    estimatedPrice = estimatedPrice,
    productUrl = productUrl,
    photoPath = photoPath,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
