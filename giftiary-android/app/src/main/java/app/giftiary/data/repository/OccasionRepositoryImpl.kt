package app.giftiary.data.repository

import androidx.room.withTransaction
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.entity.OccasionWithPerson
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.mapper.toDomain
import app.giftiary.data.local.mapper.toNewEntity
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.user.ActiveOwner
import app.giftiary.data.user.scoped
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OccasionEntry
import app.giftiary.domain.repository.OccasionSnapshot
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class OccasionRepositoryImpl @Inject constructor(
    private val database: GiftiaryDatabase,
    private val occasionDao: OccasionDao,
    private val personDao: PersonDao,
    private val syncQueue: SyncOperationQueue,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
    private val syncRequester: SyncRequester,
) : OccasionRepository {

    override fun observeOccasionsForPerson(personId: UUID): Flow<List<Occasion>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            occasionDao.observeActiveForPerson(ownerUserId, personId).map { occasions -> occasions.map { it.toDomain() } }
        }

    override fun observeOccasions(): Flow<List<OccasionEntry>> =
        activeOwner.scoped(emptyList()) { ownerUserId ->
            occasionDao.observeActiveWithPerson(ownerUserId).map { it.toEntries() }
        }

    override suspend fun getOccasionSnapshot(): OccasionSnapshot? {
        val ownerUserId = activeOwner.ownerId.value ?: return null
        return OccasionSnapshot(ownerUserId, occasionDao.findActiveWithPerson(ownerUserId).toEntries())
    }

    // Saves and pulls only store real dates, but a single corrupt row must not take down every
    // screen that lists occasions; Occasion would refuse to be built.
    private fun List<OccasionWithPerson>.toEntries(): List<OccasionEntry> =
        filter { Occasion.dateProblem(it.occasion.day, it.occasion.month, it.occasion.year) == null }
            .map { OccasionEntry(it.occasion.toDomain(), it.person?.toDomain()) }

    override suspend fun getOccasion(id: UUID): Occasion? =
        activeOwner.ownerId.value?.let { ownerUserId -> occasionDao.findActiveById(id, ownerUserId)?.toDomain() }

    override suspend fun addOccasion(personId: UUID?, details: OccasionDetails): Occasion {
        val ownerUserId = activeOwner.requireOwnerId()
        val now = now()
        val occasion = Occasion(
            id = UUID.randomUUID(),
            personId = personId,
            type = details.type,
            title = details.title,
            day = details.day,
            month = details.month,
            year = details.year,
            repeatsYearly = details.repeatsYearly,
            createdAt = now,
            updatedAt = now,
        )
        val entity = occasion.toNewEntity(ownerUserId)
        database.withTransaction {
            if (personId != null) requireActivePerson(personId, ownerUserId)
            occasionDao.insert(entity)
            syncQueue.enqueue(
                entity.ownerUserId,
                SyncEntityType.OCCASION,
                entity.id,
                SyncOperation.CREATE,
                entity.remoteState,
            )
        }
        syncRequester.requestSync()
        return occasion
    }

    override suspend fun updateOccasion(id: UUID, details: OccasionDetails) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            // Only rows found through the owner-scoped lookup are ever written back.
            val existing = occasionDao.findActiveById(id, ownerUserId) ?: throw OccasionNotFoundException(id)
            existing.personId?.let { requireActivePerson(it, ownerUserId) }

            val updated = existing.copy(
                type = details.type,
                title = details.title,
                day = details.day,
                month = details.month,
                year = details.year,
                repeatsYearly = details.repeatsYearly,
            )
            if (updated == existing) return@withTransaction

            occasionDao.update(updated.copy(updatedAt = now()))
            syncQueue.enqueue(
                existing.ownerUserId,
                SyncEntityType.OCCASION,
                id,
                SyncOperation.UPDATE,
                existing.remoteState,
            )
        }
        syncRequester.requestSync()
    }

    override suspend fun deleteOccasion(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            val existing = occasionDao.findActiveById(id, ownerUserId) ?: return@withTransaction

            occasionDao.softDelete(id, ownerUserId, deletedAt = now())
            syncQueue.enqueue(
                existing.ownerUserId,
                SyncEntityType.OCCASION,
                id,
                SyncOperation.DELETE,
                existing.remoteState,
            )
        }
        syncRequester.requestSync()
    }

    // The foreign key only proves the row exists; ownership and soft deletion are checked here.
    private suspend fun requireActivePerson(personId: UUID, ownerUserId: UUID) {
        personDao.findActiveById(personId, ownerUserId) ?: throw PersonNotFoundException(personId)
    }

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
