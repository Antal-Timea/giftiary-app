package app.gifty.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.sync.SyncRequester
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionNotFoundException
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.testing.FakeActiveOwner
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
class OccasionRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val otherOwnerId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private lateinit var database: GiftyDatabase
    private lateinit var repository: OccasionRepositoryImpl

    private val occasionDao get() = database.occasionDao()
    private val personDao get() = database.personDao()
    private val syncDao get() = database.syncOperationDao()

    private val anna = person(ownerUserId)
    private val birthday = OccasionDetails(OccasionType.BIRTHDAY, null, day = 12, month = 3, year = null, repeatsYearly = true)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        repository = OccasionRepositoryImpl(
            database = database,
            occasionDao = database.occasionDao(),
            personDao = database.personDao(),
            syncQueue = SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner = FakeActiveOwner(ownerUserId),
            clock = clock,
            SyncRequester {},
        )
        personDao.insert(anna)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding an occasion stores a local-only row and queues its creation`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)

        val entity = occasionDao.findActiveById(occasion.id, ownerUserId)!!
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertEquals(listOf(occasion), repository.observeOccasionsForPerson(anna.id).first())
        assertEquals(SyncOperation.CREATE, pendingOperation(occasion.id))
    }

    @Test
    fun `an unknown year stays unknown`() = runTest {
        val leapDay = repository.addOccasion(anna.id, birthday.copy(day = 29, month = 2, year = null))

        assertNull(repository.getOccasion(leapDay.id)?.year)
    }

    @Test
    fun `updating a local-only occasion keeps the pending create`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.updateOccasion(occasion.id, birthday.copy(year = 1990))

        val updated = repository.getOccasion(occasion.id)!!
        assertEquals(1990, updated.year)
        assertEquals(clock.instant, updated.updatedAt)
        assertEquals(occasion.createdAt, updated.createdAt)
        assertEquals(SyncOperation.CREATE, pendingOperation(occasion.id))
    }

    @Test
    fun `updating a synced occasion keeps its sync metadata and queues an update`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)
        markSynced(occasion.id)

        repository.updateOccasion(occasion.id, birthday.copy(type = OccasionType.NAME_DAY))

        val entity = occasionDao.findActiveById(occasion.id, ownerUserId)!!
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(3L, entity.version)
        assertEquals(SyncOperation.UPDATE, pendingOperation(occasion.id))
    }

    @Test
    fun `deleting a local-only occasion soft-deletes it and keeps its create for the tombstone`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)

        repository.deleteOccasion(occasion.id)

        assertNull(repository.getOccasion(occasion.id))
        assertTrue(repository.observeOccasionsForPerson(anna.id).first().isEmpty())
        // Gifts and ideas may still reference it, so the backend must get it, deleted.
        assertEquals(SyncOperation.CREATE, pendingOperation(occasion.id))
    }

    @Test
    fun `deleting a synced occasion queues a delete`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)
        markSynced(occasion.id)

        repository.deleteOccasion(occasion.id)

        assertNull(repository.getOccasion(occasion.id))
        assertEquals(SyncOperation.DELETE, pendingOperation(occasion.id))
    }

    @Test
    fun `another owner's occasion is never returned, updated or deleted`() = runTest {
        val strangersPerson = person(otherOwnerId)
        personDao.insert(strangersPerson)
        val theirs = occasionEntity(strangersPerson)
        occasionDao.insert(theirs)

        assertNull(repository.getOccasion(theirs.id))
        assertTrue(repository.observeOccasionsForPerson(strangersPerson.id).first().isEmpty())
        val update = runCatching { repository.updateOccasion(theirs.id, birthday.copy(day = 1)) }
        assertTrue(update.exceptionOrNull() is OccasionNotFoundException)
        repository.deleteOccasion(theirs.id)

        assertEquals(theirs, occasionDao.findActiveById(theirs.id, otherOwnerId))
        assertNull(pendingOperation(theirs.id))
    }

    @Test
    fun `an occasion cannot be added for another owner's person`() = runTest {
        val strangersPerson = person(otherOwnerId)
        personDao.insert(strangersPerson)

        val result = runCatching { repository.addOccasion(strangersPerson.id, birthday) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
        assertEquals(0, countOccasions())
    }

    @Test
    fun `all occasions come with their person, and a date that cannot exist is left out instead of failing`() = runTest {
        val annasBirthday = repository.addOccasion(anna.id, birthday)
        val christmas = repository.addOccasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
        // Only reachable through corrupted local data: saves and pulls validate dates.
        occasionDao.insert(occasionEntity(anna).copy(day = 31, month = 4))
        occasionDao.insert(occasionEntity(anna).copy(day = 29, month = 2, year = 2027))

        val entries = repository.observeOccasions().first()

        assertEquals(listOf(annasBirthday, christmas), entries.map { it.occasion })
        assertEquals(listOf("Someone", null), entries.map { it.person?.name })
    }

    @Test
    fun `an occasion cannot be added for a deleted person`() = runTest {
        personDao.softDelete(anna.id, ownerUserId, clock.instant)

        val result = runCatching { repository.addOccasion(anna.id, birthday) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
        assertEquals(0, countOccasions())
    }

    @Test
    fun `an occasion whose person was deleted cannot be updated`() = runTest {
        val occasion = repository.addOccasion(anna.id, birthday)
        personDao.softDelete(anna.id, ownerUserId, clock.instant)

        val result = runCatching { repository.updateOccasion(occasion.id, birthday.copy(day = 13)) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
        assertEquals(12, repository.getOccasion(occasion.id)?.day)
    }

    @Test
    fun `an occasion pointing at another owner's person cannot be updated`() = runTest {
        // Only reachable through corrupted data; the person check must not trust the stored reference.
        val strangersPerson = person(otherOwnerId)
        personDao.insert(strangersPerson)
        val corrupted = occasionEntity(strangersPerson).copy(ownerUserId = ownerUserId)
        occasionDao.insert(corrupted)

        val result = runCatching { repository.updateOccasion(corrupted.id, birthday.copy(day = 1)) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
    }

    private suspend fun pendingOperation(occasionId: UUID): SyncOperation? =
        syncDao.findForEntity(SyncEntityType.OCCASION, occasionId)
            ?.also { assertEquals("an operation has its entity's owner", ownerUserId, it.ownerUserId) }
            ?.operation

    private suspend fun markSynced(occasionId: UUID) {
        val entity = occasionDao.findActiveById(occasionId, ownerUserId)!!
        occasionDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = 3))
        syncDao.findForEntity(SyncEntityType.OCCASION, occasionId)?.let { syncDao.delete(it) }
    }

    private fun countOccasions(): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM occasion")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun person(ownerUserId: UUID) = PersonEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        name = "Someone",
        relationship = null,
        photoPath = null,
        linkedUserId = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = clock.instant,
        updatedAt = clock.instant,
        deletedAt = null,
    )

    private fun occasionEntity(person: PersonEntity) = OccasionEntity(
        id = UUID.randomUUID(),
        ownerUserId = person.ownerUserId,
        personId = person.id,
        type = OccasionType.BIRTHDAY,
        title = null,
        day = 5,
        month = 5,
        year = null,
        repeatsYearly = true,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = clock.instant,
        updatedAt = clock.instant,
        deletedAt = null,
    )
}
