package app.giftiary.data.repository

import androidx.room.withTransaction
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.dao.PhotoTransferDao
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.mapper.toDomain
import app.giftiary.data.local.mapper.toNewEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.photos.queuePhotoChange
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.user.ActiveOwner
import app.giftiary.data.user.scoped
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val database: GiftiaryDatabase,
    private val personDao: PersonDao,
    private val occasionDao: OccasionDao,
    private val syncQueue: SyncOperationQueue,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
    private val syncRequester: SyncRequester,
    private val photoTransfers: PhotoTransferDao,
    private val photoFiles: GiftPhotoFiles,
) : PersonRepository {

    override fun observePeople(): Flow<List<Person>> = activeOwner.scoped(emptyList()) { ownerUserId ->
        personDao.observeActive(ownerUserId).map { people -> people.map { it.toDomain() } }
    }

    override fun observePerson(id: UUID): Flow<Person?> = activeOwner.scoped(null) { ownerUserId ->
        personDao.observeActiveById(id, ownerUserId).map { it?.toDomain() }
    }

    override suspend fun getPerson(id: UUID): Person? =
        activeOwner.ownerId.value?.let { ownerUserId -> personDao.findActiveById(id, ownerUserId)?.toDomain() }

    override suspend fun addPerson(name: String, relationship: String?, photoPath: String?): Person {
        val ownerUserId = activeOwner.requireOwnerId()
        val now = now()
        val person = Person(
            id = UUID.randomUUID(),
            name = name,
            relationship = relationship,
            photoPath = photoPath,
            linkedUserId = null,
            createdAt = now,
            updatedAt = now,
        )
        val entity = person.toNewEntity(ownerUserId)
        database.withTransaction {
            personDao.insert(entity)
            changePhoto(ownerUserId, entity.id, old = null, new = photoPath)
            syncQueue.enqueue(
                entity.ownerUserId,
                SyncEntityType.PERSON,
                entity.id,
                SyncOperation.CREATE,
                entity.remoteState,
            )
        }
        syncRequester.requestSync()
        return person
    }

    override suspend fun updatePerson(id: UUID, name: String, relationship: String?) {
        val ownerUserId = activeOwner.requireOwnerId()
        database.withTransaction {
            // Only rows found through the owner-scoped lookup are ever written back.
            val existing = personDao.findActiveById(id, ownerUserId) ?: throw PersonNotFoundException(id)
            if (existing.name == name && existing.relationship == relationship) return@withTransaction

            personDao.update(existing.copy(name = name, relationship = relationship, updatedAt = now()))
            syncQueue.enqueue(
                existing.ownerUserId,
                SyncEntityType.PERSON,
                id,
                SyncOperation.UPDATE,
                existing.remoteState,
            )
        }
        syncRequester.requestSync()
    }

    override suspend fun setPersonPhoto(id: UUID, photoPath: String?) {
        val ownerUserId = activeOwner.requireOwnerId()
        var replaced: String? = null
        database.withTransaction {
            val existing = personDao.findActiveById(id, ownerUserId) ?: throw PersonNotFoundException(id)
            if (existing.photoPath == photoPath) return@withTransaction

            personDao.update(existing.copy(photoPath = photoPath, updatedAt = now()))
            replaced = changePhoto(ownerUserId, id, old = existing.photoPath, new = photoPath)
            syncQueue.enqueue(existing.ownerUserId, SyncEntityType.PERSON, id, SyncOperation.UPDATE, existing.remoteState)
        }
        replaced?.let(photoFiles::delete)
        syncRequester.requestSync()
    }

    override suspend fun deletePerson(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        var removed: String? = null
        database.withTransaction {
            val existing = personDao.findActiveById(id, ownerUserId) ?: return@withTransaction
            val deletedAt = now()

            // Occasions are meaningless without their person. Soft-deleting them here, rather than
            // relying on a foreign-key cascade, keeps tombstones and queue entries for sync.
            // Children are queued first so a push can delete them before their person.
            occasionDao.findActiveForPerson(ownerUserId, id).forEach { occasion ->
                occasionDao.softDelete(occasion.id, ownerUserId, deletedAt)
                syncQueue.enqueue(
                    occasion.ownerUserId,
                    SyncEntityType.OCCASION,
                    occasion.id,
                    SyncOperation.DELETE,
                    occasion.remoteState,
                )
            }
            // As for a gift: the tombstone names no photo, and the photo's object goes once it is pushed.
            if (existing.photoPath != null) personDao.update(existing.copy(photoPath = null))
            removed = changePhoto(ownerUserId, id, old = existing.photoPath, new = null)
            personDao.softDelete(id, ownerUserId, deletedAt)
            syncQueue.enqueue(
                existing.ownerUserId,
                SyncEntityType.PERSON,
                id,
                SyncOperation.DELETE,
                existing.remoteState,
            )
        }
        removed?.let(photoFiles::delete)
        syncRequester.requestSync()
    }

    private suspend fun changePhoto(ownerUserId: UUID, personId: UUID, old: String?, new: String?): String? =
        queuePhotoChange(photoTransfers, photoFiles, ownerUserId, SyncEntityType.PERSON, personId, old, new, now())

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
