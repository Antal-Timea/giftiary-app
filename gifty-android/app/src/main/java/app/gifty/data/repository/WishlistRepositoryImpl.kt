package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.WishDao
import app.gifty.data.local.dao.WishlistDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.WishlistEntity
import app.gifty.data.local.mapper.toDomain
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.sync.SyncRequester
import app.gifty.data.user.ActiveOwner
import app.gifty.data.user.scoped
import app.gifty.domain.model.Wish
import app.gifty.domain.model.Wishlist
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.repository.WishDetails
import app.gifty.domain.repository.WishNotFoundException
import app.gifty.domain.repository.WishlistNotFoundException
import app.gifty.domain.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class WishlistRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val wishlistDao: WishlistDao,
    private val wishDao: WishDao,
    private val personDao: PersonDao,
    private val syncQueue: SyncOperationQueue,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
    private val syncRequester: SyncRequester,
) : WishlistRepository {

    override fun observeWishlists(): Flow<List<Wishlist>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            wishlistDao.observeActive(ownerUserId).map { wishlists -> wishlists.map { it.toDomain() } }
        }

    override fun observeWishes(wishlistId: UUID): Flow<List<Wish>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            wishDao.observeActiveForWishlist(ownerUserId, wishlistId).map { wishes -> wishes.map { it.toDomain() } }
        }

    override suspend fun getWishlist(id: UUID): Wishlist? =
        activeOwner.ownerId.value?.let { ownerUserId -> wishlistDao.findActiveById(id, ownerUserId)?.toDomain() }

    override suspend fun getWish(id: UUID): Wish? =
        activeOwner.ownerId.value?.let { ownerUserId -> wishDao.findActiveById(id, ownerUserId)?.toDomain() }

    override suspend fun addWishlist(personId: UUID?, name: String): Wishlist {
        val ownerUserId = activeOwner.requireOwnerId()
        val now = now()
        val wishlist = Wishlist(id = UUID.randomUUID(), personId = personId, name = name, createdAt = now, updatedAt = now)
        val entity = wishlist.toNewEntity(ownerUserId)
        database.withTransaction {
            // The foreign key only proves the person exists; ownership and soft deletion are checked here.
            if (personId != null) personDao.findActiveById(personId, ownerUserId) ?: throw PersonNotFoundException(personId)
            wishlistDao.insert(entity)
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISHLIST, entity.id, SyncOperation.CREATE, entity.remoteState)
        }
        syncRequester.requestSync()
        return wishlist
    }

    override suspend fun renameWishlist(id: UUID, name: String) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            // Only rows found through the owner-scoped lookup are ever written back.
            val existing = wishlistDao.findActiveById(id, ownerUserId) ?: throw WishlistNotFoundException(id)
            if (existing.name == name) return@withTransaction
            wishlistDao.update(existing.copy(name = name, updatedAt = now()))
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISHLIST, id, SyncOperation.UPDATE, existing.remoteState)
        }
        syncRequester.requestSync()
    }

    override suspend fun deleteWishlist(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = wishlistDao.findActiveById(id, ownerUserId) ?: return@withTransaction
            val now = now()
            // A wish is only ever shown on its wishlist, so it goes with it, each a change of its own.
            wishDao.findActiveForWishlist(ownerUserId, id).forEach { wish ->
                wishDao.softDelete(wish.id, ownerUserId, now)
                syncQueue.enqueue(ownerUserId, SyncEntityType.WISH, wish.id, SyncOperation.DELETE, wish.remoteState)
            }
            wishlistDao.softDelete(id, ownerUserId, now)
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISHLIST, id, SyncOperation.DELETE, existing.remoteState)
        }
        syncRequester.requestSync()
    }

    override suspend fun addWish(wishlistId: UUID, details: WishDetails): Wish {
        val ownerUserId = activeOwner.requireOwnerId()
        val now = now()
        val wish = Wish(
            id = UUID.randomUUID(),
            wishlistId = wishlistId,
            name = details.name,
            note = details.note,
            estimatedPrice = details.estimatedPrice,
            productUrl = details.productUrl,
            photoPath = null,
            createdAt = now,
            updatedAt = now,
        )
        val entity = wish.toNewEntity(ownerUserId)
        database.withTransaction {
            requireActiveWishlist(wishlistId, ownerUserId)
            wishDao.insert(entity)
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISH, entity.id, SyncOperation.CREATE, entity.remoteState)
        }
        syncRequester.requestSync()
        return wish
    }

    override suspend fun updateWish(id: UUID, details: WishDetails) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = wishDao.findActiveById(id, ownerUserId) ?: throw WishNotFoundException(id)
            requireActiveWishlist(existing.wishlistId, ownerUserId)
            val updated = existing.copy(
                name = details.name,
                note = details.note,
                estimatedPriceAmountMinor = details.estimatedPrice?.amountMinor,
                estimatedPriceCurrencyCode = details.estimatedPrice?.currencyCode,
                productUrl = details.productUrl,
            )
            if (updated == existing) return@withTransaction
            wishDao.update(updated.copy(updatedAt = now()))
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISH, id, SyncOperation.UPDATE, existing.remoteState)
        }
        syncRequester.requestSync()
    }

    override suspend fun deleteWish(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = wishDao.findActiveById(id, ownerUserId) ?: return@withTransaction
            wishDao.softDelete(id, ownerUserId, now())
            syncQueue.enqueue(ownerUserId, SyncEntityType.WISH, id, SyncOperation.DELETE, existing.remoteState)
        }
        syncRequester.requestSync()
    }

    private suspend fun requireActiveWishlist(id: UUID, ownerUserId: UUID): WishlistEntity =
        wishlistDao.findActiveById(id, ownerUserId) ?: throw WishlistNotFoundException(id)

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
