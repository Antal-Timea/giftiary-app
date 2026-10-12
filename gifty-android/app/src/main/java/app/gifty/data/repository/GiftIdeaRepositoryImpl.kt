package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.mapper.toDomain
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.sync.SyncRequester
import app.gifty.data.user.ActiveOwner
import app.gifty.data.user.scoped
import app.gifty.domain.model.Gift
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdea
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.repository.GiftIdeaAlreadyGivenException
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.GiftIdeaEntry
import app.gifty.domain.repository.GiftIdeaNotFoundException
import app.gifty.domain.repository.GiftIdeaRepository
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class GiftIdeaRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val giftIdeaDao: GiftIdeaDao,
    private val giftDao: GiftDao,
    private val personDao: PersonDao,
    private val occasionDao: OccasionDao,
    private val syncQueue: SyncOperationQueue,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
    private val syncRequester: SyncRequester,
) : GiftIdeaRepository {

    override fun observeGiftIdeas(): Flow<List<GiftIdeaEntry>> = activeOwner.scoped(emptyList()) { ownerUserId ->
        giftIdeaDao.observeActive(ownerUserId).map { ideas -> ideas.map { it.toDomain(ownerUserId) } }
    }

    override fun observeGiftIdeasForPerson(personId: UUID): Flow<List<GiftIdeaEntry>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            giftIdeaDao.observeActiveForPerson(ownerUserId, personId).map { ideas -> ideas.map { it.toDomain(ownerUserId) } }
        }

    override fun observeGiftIdea(id: UUID): Flow<GiftIdeaEntry?> = activeOwner.scoped(null) { ownerUserId ->
        giftIdeaDao.observeActiveById(id, ownerUserId).map { it?.toDomain(ownerUserId) }
    }

    override suspend fun getGiftIdea(id: UUID): GiftIdeaEntry? = observeGiftIdea(id).first()

    override suspend fun addGiftIdea(details: GiftIdeaDetails): GiftIdea {
        val ownerUserId = activeOwner.requireOwnerId()
        val now = now()
        val idea = GiftIdea(
            id = UUID.randomUUID(),
            recipientPersonId = details.recipientPersonId,
            occasionId = details.occasionId,
            name = details.name,
            status = details.status,
            estimatedPrice = details.estimatedPrice,
            actualPrice = details.actualPrice,
            photoPath = null,
            productUrl = details.productUrl,
            note = details.note,
            createdAt = now,
            updatedAt = now,
        )
        val entity = idea.toNewEntity(ownerUserId)
        database.withTransaction {
            requireActivePerson(details.recipientPersonId, ownerUserId)
            details.occasionId?.let { requireAssignableOccasion(it, ownerUserId, details.recipientPersonId) }
            giftIdeaDao.insert(entity)
            syncQueue.enqueue(
                entity.ownerUserId,
                SyncEntityType.GIFT_IDEA,
                entity.id,
                SyncOperation.CREATE,
                entity.remoteState,
            )
        }
        syncRequester.requestSync()
        return idea
    }

    override suspend fun updateGiftIdea(id: UUID, details: GiftIdeaDetails) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            // Only rows found through the owner-scoped lookup are ever written back.
            val existing = giftIdeaDao.findActiveById(id, ownerUserId) ?: throw GiftIdeaNotFoundException(id)
            validateChangedReferences(existing, details, ownerUserId)

            val updated = existing.copy(
                recipientPersonId = details.recipientPersonId,
                occasionId = details.occasionId,
                name = details.name,
                status = details.status,
                estimatedPriceAmountMinor = details.estimatedPrice?.amountMinor,
                estimatedPriceCurrencyCode = details.estimatedPrice?.currencyCode,
                actualPriceAmountMinor = details.actualPrice?.amountMinor,
                actualPriceCurrencyCode = details.actualPrice?.currencyCode,
                productUrl = details.productUrl,
                note = details.note,
            )
            writeChange(existing, updated)
        }
        syncRequester.requestSync()
    }

    override suspend fun updateStatus(id: UUID, status: GiftIdeaStatus) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = giftIdeaDao.findActiveById(id, ownerUserId) ?: throw GiftIdeaNotFoundException(id)
            writeChange(existing, existing.copy(status = status))
        }
        syncRequester.requestSync()
    }

    override suspend fun deleteGiftIdea(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = giftIdeaDao.findActiveById(id, ownerUserId) ?: return@withTransaction

            giftIdeaDao.softDelete(id, ownerUserId, deletedAt = now())
            syncQueue.enqueue(
                existing.ownerUserId,
                SyncEntityType.GIFT_IDEA,
                id,
                SyncOperation.DELETE,
                existing.remoteState,
            )
        }
        syncRequester.requestSync()
    }

    override suspend fun markAsGiven(id: UUID, givenAt: LocalDate, price: Money?): Gift {
        val ownerUserId = activeOwner.requireOwnerId()
        val created = database.withTransaction {
            // Re-read inside the transaction: a second conversion must find the idea archived.
            val idea = giftIdeaDao.findNotDeletedById(id, ownerUserId) ?: throw GiftIdeaNotFoundException(id)
            if (idea.archivedAt != null) throw GiftIdeaAlreadyGivenException(id)
            // The references were valid when assigned; they may have been deleted since, which must
            // not stop the user from recording the gift, but they must still be theirs.
            personDao.findById(idea.recipientPersonId, ownerUserId) ?: throw PersonNotFoundException(idea.recipientPersonId)
            idea.occasionId?.let { occasionId ->
                val occasion = occasionDao.findById(occasionId, ownerUserId)
                if (occasion == null || !occasion.fits(idea.recipientPersonId)) throw OccasionNotFoundException(occasionId)
            }

            val now = now()
            val gift = Gift(
                id = UUID.randomUUID(),
                direction = GiftDirection.GIVEN,
                giverPersonId = null,
                recipientPersonId = idea.recipientPersonId,
                occasionId = idea.occasionId,
                sourceGiftIdeaId = idea.id,
                name = idea.name,
                givenAt = givenAt,
                price = price,
                photoPath = idea.photoPath,
                productUrl = idea.productUrl,
                note = idea.note,
                createdAt = now,
                updatedAt = now,
            )
            val giftEntity = gift.toNewEntity(ownerUserId)

            // Archived, not deleted: the idea stays as the gift's source and must still reach the
            // backend, so a pending CREATE of a never-synced idea is kept (CREATE + UPDATE = CREATE).
            giftIdeaDao.update(idea.copy(archivedAt = now, updatedAt = now))
            syncQueue.enqueue(
                idea.ownerUserId,
                SyncEntityType.GIFT_IDEA,
                idea.id,
                SyncOperation.UPDATE,
                idea.remoteState,
            )
            giftDao.insert(giftEntity)
            syncQueue.enqueue(
                giftEntity.ownerUserId,
                SyncEntityType.GIFT,
                gift.id,
                SyncOperation.CREATE,
                giftEntity.remoteState,
            )
            gift
        }
        syncRequester.requestSync()
        return created
    }

    private suspend fun writeChange(existing: GiftIdeaEntity, updated: GiftIdeaEntity) {
        if (updated == existing) return
        giftIdeaDao.update(updated.copy(updatedAt = now()))
        syncQueue.enqueue(
            existing.ownerUserId,
            SyncEntityType.GIFT_IDEA,
            existing.id,
            SyncOperation.UPDATE,
            existing.remoteState,
        )
    }

    /**
     * A reference the idea already had may point at a person or occasion deleted since; keeping
     * it must not make the idea unsaveable. Anything newly assigned has to be active.
     */
    private suspend fun validateChangedReferences(
        existing: GiftIdeaEntity,
        details: GiftIdeaDetails,
        ownerUserId: UUID,
    ) {
        if (details.recipientPersonId != existing.recipientPersonId) {
            requireActivePerson(details.recipientPersonId, ownerUserId)
        }
        val occasionId = details.occasionId ?: return
        if (occasionId != existing.occasionId) {
            requireAssignableOccasion(occasionId, ownerUserId, details.recipientPersonId)
        } else {
            // Kept even if deleted, but it must still belong to the (possibly new) recipient.
            val occasion = occasionDao.findById(occasionId, ownerUserId) ?: throw OccasionNotFoundException(occasionId)
            if (!occasion.fits(details.recipientPersonId)) throw OccasionNotFoundException(occasionId)
        }
    }

    // The foreign key only proves the row exists; ownership and soft deletion are checked here.
    private suspend fun requireActivePerson(personId: UUID, ownerUserId: UUID) {
        personDao.findActiveById(personId, ownerUserId) ?: throw PersonNotFoundException(personId)
    }

    private suspend fun requireAssignableOccasion(occasionId: UUID, ownerUserId: UUID, recipientPersonId: UUID) {
        val occasion = occasionDao.findActiveById(occasionId, ownerUserId)
        if (occasion == null || !occasion.fits(recipientPersonId)) throw OccasionNotFoundException(occasionId)
    }

    /** Only the recipient's own occasions, or general ones, can be the occasion of their idea. */
    private fun OccasionEntity.fits(recipientPersonId: UUID): Boolean =
        personId == null || personId == recipientPersonId

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
