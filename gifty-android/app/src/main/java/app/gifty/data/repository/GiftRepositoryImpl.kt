package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.mapper.toDomain
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.user.CurrentUserIdProvider
import app.gifty.domain.model.Gift
import app.gifty.domain.repository.GiftDetails
import app.gifty.domain.repository.GiftEntry
import app.gifty.domain.repository.GiftNotFoundException
import app.gifty.domain.repository.GiftRepository
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class GiftRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val giftDao: GiftDao,
    private val personDao: PersonDao,
    private val occasionDao: OccasionDao,
    private val syncQueue: SyncOperationQueue,
    private val currentUserIdProvider: CurrentUserIdProvider,
    private val clock: Clock,
) : GiftRepository {

    override fun observeGifts(): Flow<List<GiftEntry>> = flow {
        val ownerUserId = currentUserIdProvider.currentUserId()
        emitAll(giftDao.observeActive(ownerUserId).map { gifts -> gifts.map { it.toDomain(ownerUserId) } })
    }

    override fun observeGiftsForPerson(personId: UUID): Flow<List<GiftEntry>> = flow {
        val ownerUserId = currentUserIdProvider.currentUserId()
        emitAll(
            giftDao.observeActiveForPerson(ownerUserId, personId)
                .map { gifts -> gifts.map { it.toDomain(ownerUserId) } },
        )
    }

    override fun observeGift(id: UUID): Flow<GiftEntry?> = flow {
        val ownerUserId = currentUserIdProvider.currentUserId()
        emitAll(giftDao.observeActiveById(id, ownerUserId).map { it?.toDomain(ownerUserId) })
    }

    override suspend fun getGift(id: UUID): GiftEntry? = observeGift(id).first()

    override suspend fun addGift(details: GiftDetails): Gift {
        val ownerUserId = currentUserIdProvider.currentUserId()
        val now = now()
        val gift = Gift(
            id = UUID.randomUUID(),
            direction = details.direction,
            giverPersonId = details.giverPersonId,
            recipientPersonId = details.recipientPersonId,
            occasionId = details.occasionId,
            sourceGiftIdeaId = null,
            name = details.name,
            givenAt = details.givenAt,
            price = details.price,
            photoPath = null,
            productUrl = details.productUrl,
            note = details.note,
            createdAt = now,
            updatedAt = now,
        )
        val entity = gift.toNewEntity(ownerUserId)
        database.withTransaction {
            details.giverPersonId?.let { requireActivePerson(it, ownerUserId) }
            details.recipientPersonId?.let { requireActivePerson(it, ownerUserId) }
            details.occasionId?.let { requireAssignableOccasion(it, ownerUserId, details) }
            giftDao.insert(entity)
            syncQueue.enqueue(SyncEntityType.GIFT, entity.id, SyncOperation.CREATE, entity.remoteState)
        }
        return gift
    }

    override suspend fun updateGift(id: UUID, details: GiftDetails) {
        val ownerUserId = currentUserIdProvider.currentUserId()
        database.withTransaction {
            // Only rows found through the owner-scoped lookup are ever written back.
            val existing = giftDao.findActiveById(id, ownerUserId) ?: throw GiftNotFoundException(id)
            validateChangedReferences(existing, details, ownerUserId)

            val updated = existing.copy(
                direction = details.direction,
                giverPersonId = details.giverPersonId,
                recipientPersonId = details.recipientPersonId,
                occasionId = details.occasionId,
                name = details.name,
                givenAt = details.givenAt,
                priceAmountMinor = details.price?.amountMinor,
                priceCurrencyCode = details.price?.currencyCode,
                productUrl = details.productUrl,
                note = details.note,
            )
            if (updated == existing) return@withTransaction

            giftDao.update(updated.copy(updatedAt = now()))
            syncQueue.enqueue(SyncEntityType.GIFT, id, SyncOperation.UPDATE, existing.remoteState)
        }
    }

    override suspend fun deleteGift(id: UUID) {
        val ownerUserId = currentUserIdProvider.currentUserId()
        database.withTransaction {
            val existing = giftDao.findActiveById(id, ownerUserId) ?: return@withTransaction

            giftDao.softDelete(id, ownerUserId, deletedAt = now())
            syncQueue.enqueue(SyncEntityType.GIFT, id, SyncOperation.DELETE, existing.remoteState)
        }
    }

    /**
     * A reference the gift already had may point at a person or occasion deleted since; keeping
     * it must not make the gift unsaveable. Anything newly assigned has to be active.
     */
    private suspend fun validateChangedReferences(existing: GiftEntity, details: GiftDetails, ownerUserId: UUID) {
        if (details.giverPersonId != existing.giverPersonId) {
            details.giverPersonId?.let { requireActivePerson(it, ownerUserId) }
        }
        if (details.recipientPersonId != existing.recipientPersonId) {
            details.recipientPersonId?.let { requireActivePerson(it, ownerUserId) }
        }
        val occasionId = details.occasionId ?: return
        if (occasionId != existing.occasionId) {
            requireAssignableOccasion(occasionId, ownerUserId, details)
        } else {
            // Kept even if deleted, but it must still belong to one of the (possibly new) participants.
            val occasion = occasionDao.findById(occasionId, ownerUserId) ?: throw OccasionNotFoundException(occasionId)
            if (!occasion.fits(details)) throw OccasionNotFoundException(occasionId)
        }
    }

    // The foreign key only proves the row exists; ownership and soft deletion are checked here.
    private suspend fun requireActivePerson(personId: UUID, ownerUserId: UUID) {
        personDao.findActiveById(personId, ownerUserId) ?: throw PersonNotFoundException(personId)
    }

    private suspend fun requireAssignableOccasion(occasionId: UUID, ownerUserId: UUID, details: GiftDetails) {
        val occasion = occasionDao.findActiveById(occasionId, ownerUserId)
        if (occasion == null || !occasion.fits(details)) throw OccasionNotFoundException(occasionId)
    }

    /** A person's birthday can only be the occasion of a gift that person gave or received. */
    private fun OccasionEntity.fits(details: GiftDetails): Boolean =
        personId == null || personId == details.giverPersonId || personId == details.recipientPersonId

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
