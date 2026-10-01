package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.mapper.toDomain
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.user.CurrentUserIdProvider
import app.gifty.domain.model.Person
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val personDao: PersonDao,
    private val syncQueue: SyncOperationQueue,
    private val currentUserIdProvider: CurrentUserIdProvider,
    private val clock: Clock,
) : PersonRepository {

    override fun observePeople(): Flow<List<Person>> =
        personDao.observeActive().map { people -> people.map { it.toDomain() } }

    override suspend fun getPerson(id: UUID): Person? = personDao.findActiveById(id)?.toDomain()

    override suspend fun addPerson(name: String, relationship: String?): Person {
        val ownerUserId = currentUserIdProvider.currentUserId()
        val now = now()
        val person = Person(
            id = UUID.randomUUID(),
            name = name,
            relationship = relationship,
            photoPath = null,
            linkedUserId = null,
            createdAt = now,
            updatedAt = now,
        )
        val entity = person.toNewEntity(ownerUserId)
        database.withTransaction {
            personDao.insert(entity)
            syncQueue.enqueue(SyncEntityType.PERSON, entity.id, SyncOperation.CREATE, entity.remoteState)
        }
        return person
    }

    override suspend fun updatePerson(id: UUID, name: String, relationship: String?) {
        database.withTransaction {
            val existing = personDao.findActiveById(id) ?: throw PersonNotFoundException(id)
            if (existing.name == name && existing.relationship == relationship) return@withTransaction

            personDao.update(existing.copy(name = name, relationship = relationship, updatedAt = now()))
            syncQueue.enqueue(SyncEntityType.PERSON, id, SyncOperation.UPDATE, existing.remoteState)
        }
    }

    override suspend fun deletePerson(id: UUID) {
        database.withTransaction {
            val existing = personDao.findActiveById(id) ?: return@withTransaction

            personDao.softDelete(id, deletedAt = now())
            syncQueue.enqueue(SyncEntityType.PERSON, id, SyncOperation.DELETE, existing.remoteState)
        }
    }

    // Room stores instants with millisecond precision; truncating keeps returned models equal
    // to what is read back later.
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
