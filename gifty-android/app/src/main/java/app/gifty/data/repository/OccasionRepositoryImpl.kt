package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.mapper.toDomain
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.user.CurrentUserIdProvider
import app.gifty.domain.model.Occasion
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.PersonNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class OccasionRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val occasionDao: OccasionDao,
    private val personDao: PersonDao,
    private val syncQueue: SyncOperationQueue,
    private val currentUserIdProvider: CurrentUserIdProvider,
    private val clock: Clock,
) : OccasionRepository {

    override fun observeOccasionsForPerson(personId: UUID): Flow<List<Occasion>> = flow {
        val ownerUserId = currentUserIdProvider.currentUserId()
        emitAll(
            occasionDao.observeActiveForPerson(ownerUserId, personId)
                .map { occasions -> occasions.map { it.toDomain() } },
        )
    }

    override suspend fun getOccasion(id: UUID): Occasion? =
        occasionDao.findActiveById(id, currentUserIdProvider.currentUserId())?.toDomain()

    override suspend fun addOccasion(personId: UUID?, details: OccasionDetails): Occasion {
        val ownerUserId = currentUserIdProvider.currentUserId()
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
        return occasion
    }

    override suspend fun updateOccasion(id: UUID, details: OccasionDetails) {
        val ownerUserId = currentUserIdProvider.currentUserId()
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
    }

    override suspend fun deleteOccasion(id: UUID) {
        val ownerUserId = currentUserIdProvider.currentUserId()
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
    }

    // The foreign key only proves the row exists; ownership and soft deletion are checked here.
    private suspend fun requireActivePerson(personId: UUID, ownerUserId: UUID) {
        personDao.findActiveById(personId, ownerUserId) ?: throw PersonNotFoundException(personId)
    }

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
