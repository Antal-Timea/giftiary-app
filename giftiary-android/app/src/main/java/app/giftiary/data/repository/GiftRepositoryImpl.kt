package app.giftiary.data.repository

import androidx.room.withTransaction
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.dao.GiftDao
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.entity.GiftEntity
import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.mapper.toDomain
import app.giftiary.data.local.mapper.toNewEntity
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.user.ActiveOwner
import app.giftiary.data.user.scoped
import app.giftiary.domain.model.Gift
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftNotFoundException
import app.giftiary.domain.repository.GiftRepository
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class GiftRepositoryImpl @Inject constructor(
    private val database: GiftiaryDatabase,
    private val giftDao: GiftDao,
    private val personDao: PersonDao,
    private val occasionDao: OccasionDao,
    private val syncQueue: SyncOperationQueue,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
    private val syncRequester: SyncRequester,
) : GiftRepository {

    override fun observeGifts(): Flow<List<GiftEntry>> = activeOwner.scoped(emptyList()) { ownerUserId ->
        giftDao.observeActive(ownerUserId).map { gifts -> gifts.map { it.toDomain(ownerUserId) } }
    }

    override fun observeGiftsForPerson(personId: UUID): Flow<List<GiftEntry>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            giftDao.observeActiveForPerson(ownerUserId, personId).map { gifts -> gifts.map { it.toDomain(ownerUserId) } }
        }

    override fun observeGift(id: UUID): Flow<GiftEntry?> = activeOwner.scoped(null) { ownerUserId ->
        giftDao.observeActiveById(id, ownerUserId).map { it?.toDomain(ownerUserId) }
    }

    override suspend fun getGift(id: UUID): GiftEntry? = observeGift(id).first()

    override suspend fun addGift(details: GiftDetails): Gift {
        val ownerUserId = activeOwner.requireOwnerId()
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
            syncQueue.enqueue(
                entity.ownerUserId,
                SyncEntityType.GIFT,
                entity.id,
                SyncOperation.CREATE,
                entity.remoteState,
            )
        }
        syncRequester.requestSync()
        return gift
    }

    override suspend fun updateGift(id: UUID, details: GiftDetails) {
        val ownerUserId = activeOwner.requireOwnerId()
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
            syncQueue.enqueue(existing.ownerUserId, SyncEntityType.GIFT, id, SyncOperation.UPDATE, existing.remoteState)
        }
        syncRequester.requestSync()
    }

    override suspend fun deleteGift(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = giftDao.findActiveById(id, ownerUserId) ?: return@withTransaction

            giftDao.softDelete(id, ownerUserId, deletedAt = now())
            syncQueue.enqueue(existing.ownerUserId, SyncEntityType.GIFT, id, SyncOperation.DELETE, existing.remoteState)
        }
        syncRequester.requestSync()
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
