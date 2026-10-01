package app.gifty.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.local.mapper.toNewEntity
import app.gifty.data.user.CurrentUserIdProvider
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PersonRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private lateinit var database: GiftyDatabase
    private lateinit var repository: PersonRepositoryImpl

    private val personDao get() = database.personDao()
    private val syncDao get() = database.syncOperationDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        repository = PersonRepositoryImpl(
            database = database,
            personDao = database.personDao(),
            syncQueue = SyncOperationQueue(database.syncOperationDao(), clock),
            currentUserIdProvider = object : CurrentUserIdProvider {
                override suspend fun currentUserId() = ownerUserId
            },
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding a person stores a local-only row and queues its creation`() = runTest {
        val person = repository.addPerson("Anna", "Sister")

        val entity = personDao.findActiveById(person.id, ownerUserId)!!
        assertEquals(ownerUserId, entity.ownerUserId)
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertEquals(clock.instant, entity.createdAt)
        assertEquals(listOf(person), repository.observePeople().first())
        assertEquals(SyncOperation.CREATE, pendingOperation(person.id))
    }

    @Test
    fun `updating a local-only person keeps the pending create`() = runTest {
        val person = repository.addPerson("Anna", null)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.updatePerson(person.id, "Anna Kovács", "Sister")

        val updated = repository.getPerson(person.id)!!
        assertEquals("Anna Kovács", updated.name)
        assertEquals("Sister", updated.relationship)
        assertEquals(person.createdAt, updated.createdAt)
        assertEquals(clock.instant, updated.updatedAt)
        assertEquals(SyncOperation.CREATE, pendingOperation(person.id))
    }

    @Test
    fun `updating a synced person keeps its sync metadata and queues an update`() = runTest {
        val person = repository.addPerson("Anna", null)
        markSynced(person.id, version = 4)

        repository.updatePerson(person.id, "Anna Kovács", null)

        val entity = personDao.findActiveById(person.id, ownerUserId)!!
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(4L, entity.version)
        assertEquals(SyncOperation.UPDATE, pendingOperation(person.id))
    }

    @Test
    fun `saving unchanged details writes nothing`() = runTest {
        val person = repository.addPerson("Anna", "Sister")
        markSynced(person.id, version = 1)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.updatePerson(person.id, "Anna", "Sister")

        assertEquals(person.updatedAt, repository.getPerson(person.id)?.updatedAt)
        assertNull(pendingOperation(person.id))
    }

    @Test
    fun `a failing queue write rolls back the person change`() = runTest {
        val person = repository.addPerson("Anna", null)
        markSynced(person.id, version = 1)
        // A pending DELETE for an active person is inconsistent, so the queue refuses the UPDATE.
        syncDao.insert(
            SyncOperationEntity(
                id = UUID.randomUUID(),
                entityType = SyncEntityType.PERSON,
                entityId = person.id,
                operation = SyncOperation.DELETE,
                createdAt = clock.instant,
                attemptCount = 0,
                lastAttemptAt = null,
                lastErrorCode = null,
            ),
        )

        val result = runCatching { repository.updatePerson(person.id, "Anna Kovács", null) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Anna", repository.getPerson(person.id)?.name)
    }

    @Test
    fun `updating a deleted person fails`() = runTest {
        val person = repository.addPerson("Anna", null)
        repository.deletePerson(person.id)

        val result = runCatching { repository.updatePerson(person.id, "Anna", null) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
    }

    @Test
    fun `deleting a local-only person soft-deletes it and drops the pending create`() = runTest {
        val person = repository.addPerson("Anna", null)

        repository.deletePerson(person.id)

        assertNull(repository.getPerson(person.id))
        assertTrue(repository.observePeople().first().isEmpty())
        assertNull(pendingOperation(person.id))
        assertEquals(1, countRows(person.id))
    }

    @Test
    fun `deleting a synced person queues a delete`() = runTest {
        val person = repository.addPerson("Anna", null)
        markSynced(person.id, version = 2)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.deletePerson(person.id)

        assertNull(repository.getPerson(person.id))
        assertEquals(SyncOperation.DELETE, pendingOperation(person.id))
    }

    @Test
    fun `deleted people are excluded from active observations`() = runTest {
        val anna = repository.addPerson("Anna", null)
        val bela = repository.addPerson("Béla", null)

        repository.deletePerson(anna.id)

        assertEquals(listOf(bela), repository.observePeople().first())
    }

    @Test
    fun `people of another owner are never observed, returned, updated or deleted`() = runTest {
        val mine = repository.addPerson("Anna", null)
        val otherOwnerId = UUID.randomUUID()
        val theirs = mine.copy(id = UUID.randomUUID(), name = "Béla").toNewEntity(otherOwnerId)
        personDao.insert(theirs)

        assertEquals(listOf(mine), repository.observePeople().first())
        assertNull(repository.getPerson(theirs.id))

        val update = runCatching { repository.updatePerson(theirs.id, "Changed", null) }
        assertTrue(update.exceptionOrNull() is PersonNotFoundException)

        repository.deletePerson(theirs.id)

        assertEquals(theirs, personDao.findActiveById(theirs.id, otherOwnerId))
        assertNull(pendingOperation(theirs.id))
    }

    private suspend fun pendingOperation(personId: UUID): SyncOperation? =
        syncDao.findForEntity(SyncEntityType.PERSON, personId)?.operation

    /** Simulates a completed push: the backend has the row and nothing is pending. */
    private suspend fun markSynced(personId: UUID, version: Long) {
        val entity = personDao.findActiveById(personId, ownerUserId)!!
        personDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = version))
        syncDao.findForEntity(SyncEntityType.PERSON, personId)?.let { syncDao.delete(it) }
    }

    private fun countRows(personId: UUID): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM person WHERE id = ?", arrayOf(personId.toString()))
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}
