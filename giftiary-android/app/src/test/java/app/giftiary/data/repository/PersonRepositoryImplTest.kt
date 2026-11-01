package app.giftiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.local.mapper.toNewEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PersonRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private lateinit var database: GiftiaryDatabase
    private lateinit var repository: PersonRepositoryImpl
    private lateinit var occasionRepository: OccasionRepositoryImpl
    private lateinit var giftRepository: GiftRepositoryImpl
    private lateinit var giftIdeaRepository: GiftIdeaRepositoryImpl

    private val personDao get() = database.personDao()
    private val occasionDao get() = database.occasionDao()
    private val syncDao get() = database.syncOperationDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        val syncQueue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(ownerUserId)
        repository = PersonRepositoryImpl(
            database = database,
            personDao = database.personDao(),
            occasionDao = database.occasionDao(),
            syncQueue = syncQueue,
            activeOwner = activeOwner,
            clock = clock,
            SyncRequester {},
        )
        occasionRepository = OccasionRepositoryImpl(
            database = database,
            occasionDao = database.occasionDao(),
            personDao = database.personDao(),
            syncQueue = syncQueue,
            activeOwner = activeOwner,
            clock = clock,
            SyncRequester {},
        )
        giftRepository = GiftRepositoryImpl(
            database = database,
            giftDao = database.giftDao(),
            personDao = database.personDao(),
            occasionDao = database.occasionDao(),
            syncQueue = syncQueue,
            activeOwner = activeOwner,
            clock = clock,
            SyncRequester {},
            photoTransfers = database.photoTransferDao(), photoFiles = GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        giftIdeaRepository = GiftIdeaRepositoryImpl(
            database = database,
            giftIdeaDao = database.giftIdeaDao(),
            giftDao = database.giftDao(),
            personDao = database.personDao(),
            occasionDao = database.occasionDao(),
            syncQueue = syncQueue,
            activeOwner = activeOwner,
            clock = clock,
            SyncRequester {},
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
                ownerUserId = ownerUserId,                entityType = SyncEntityType.PERSON,
                entityId = person.id,
                operation = SyncOperation.DELETE,
                mutationId = UUID.randomUUID(),
                inFlightMutationId = null,
                blockedReason = null,
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
    fun `deleting a local-only person soft-deletes it and keeps its create for the tombstone`() = runTest {
        val person = repository.addPerson("Anna", null)
        val create = syncDao.findForEntity(SyncEntityType.PERSON, person.id)!!

        repository.deletePerson(person.id)

        assertNull(repository.getPerson(person.id))
        assertTrue(repository.observePeople().first().isEmpty())
        // Gifts and ideas for Anna may still sync, so the backend must get her row, deleted.
        val tombstone = syncDao.findForEntity(SyncEntityType.PERSON, person.id)!!
        assertEquals(create.copy(mutationId = tombstone.mutationId), tombstone)
        assertNotEquals(create.mutationId, tombstone.mutationId)
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

    @Test
    fun `deleting a person soft-deletes their occasions and keeps them as tombstones`() = runTest {
        val anna = repository.addPerson("Anna", null)
        val birthday = occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
        val nameDay = occasionRepository.addOccasion(anna.id, birthdayOn(26, 7))

        repository.deletePerson(anna.id)

        assertTrue(occasionRepository.observeOccasionsForPerson(anna.id).first().isEmpty())
        assertNull(occasionRepository.getOccasion(birthday.id))
        assertEquals(1, countRows("occasion", birthday.id))
        assertEquals(1, countRows("occasion", nameDay.id))
    }

    @Test
    fun `deleting a person keeps pending creates of local-only occasions and queues deletes for synced ones`() =
        runTest {
            val anna = repository.addPerson("Anna", null)
            markSynced(anna.id, version = 1)
            val localOnly = occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
            val synced = occasionRepository.addOccasion(anna.id, birthdayOn(26, 7))
            markOccasionSynced(synced.id)

            repository.deletePerson(anna.id)

            assertEquals(SyncOperation.CREATE, pendingOperation(localOnly.id, SyncEntityType.OCCASION))
            assertEquals(SyncOperation.DELETE, pendingOperation(synced.id, SyncEntityType.OCCASION))
            assertEquals(SyncOperation.DELETE, pendingOperation(anna.id))
        }

    @Test
    fun `deleting a person makes each affected operation its own new mutation`() = runTest {
        val anna = repository.addPerson("Anna", null)
        markSynced(anna.id, version = 1)
        val birthday = occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
        val nameDay = occasionRepository.addOccasion(anna.id, birthdayOn(26, 7))
        markOccasionSynced(nameDay.id)
        val before = pendingMutationIds()

        repository.deletePerson(anna.id)

        val after = pendingMutationIds()
        assertEquals(setOf(anna.id, birthday.id, nameDay.id), after.keys)
        assertEquals(3, after.values.toSet().size)
        assertTrue(after.values.none { it in before.values })
    }

    @Test
    fun `editing a person whose update is in flight keeps the version sync last reconciled`() = runTest {
        val person = repository.addPerson("Anna", null)
        markSynced(person.id, version = 4)
        repository.updatePerson(person.id, "Anna Kovács", null)
        val sent = syncDao.findForEntity(SyncEntityType.PERSON, person.id)!!
        assertEquals(1, syncDao.markInFlight(sent.id, sent.mutationId))

        repository.updatePerson(person.id, "Anna K.", "Sister")
        repository.deletePerson(person.id)

        val entity = personDao.findById(person.id, ownerUserId)!!
        assertEquals(4L, entity.version)
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        val pending = syncDao.findForEntity(SyncEntityType.PERSON, person.id)!!
        assertEquals(SyncOperation.DELETE, pending.operation)
        assertEquals(sent.mutationId, pending.inFlightMutationId)
        assertNotEquals(sent.mutationId, pending.mutationId)
    }

    @Test
    fun `deleting a person leaves other people's occasions untouched`() = runTest {
        val anna = repository.addPerson("Anna", null)
        val bela = repository.addPerson("Béla", null)
        occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
        val belasBirthday = occasionRepository.addOccasion(bela.id, birthdayOn(4, 11))

        repository.deletePerson(anna.id)

        assertEquals(listOf(belasBirthday), occasionRepository.observeOccasionsForPerson(bela.id).first())
        assertEquals(SyncOperation.CREATE, pendingOperation(belasBirthday.id, SyncEntityType.OCCASION))
    }

    @Test
    fun `a failure while deleting an occasion rolls back the whole person deletion`() = runTest {
        val anna = repository.addPerson("Anna", null)
        val first = occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
        val second = occasionRepository.addOccasion(anna.id, birthdayOn(26, 7))
        // A pending DELETE for an active occasion is inconsistent, so the queue refuses another DELETE.
        syncDao.delete(syncDao.findForEntity(SyncEntityType.OCCASION, second.id)!!)
        syncDao.insert(pendingDelete(second.id, SyncEntityType.OCCASION))

        val result = runCatching { repository.deletePerson(anna.id) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(anna, repository.getPerson(anna.id))
        assertEquals(listOf(first, second), occasionRepository.observeOccasionsForPerson(anna.id).first())
        assertEquals(SyncOperation.CREATE, pendingOperation(first.id, SyncEntityType.OCCASION))
    }

    /**
     * The whole contract in one place: the person and their occasions go, their history and plans
     * stay. Gifts and ideas are made "synced" so that any DELETE a cascade queued would show.
     */
    @Test
    fun `deleting a person removes their occasions but keeps gifts and gift ideas involving them`() = runTest {
        val anna = repository.addPerson("Anna", "Sister")
        val birthday = occasionRepository.addOccasion(anna.id, birthdayOn(12, 3))
        val givenToAnna = giftRepository.addGift(
            GiftDetails(
                direction = GiftDirection.GIVEN,
                giverPersonId = null,
                recipientPersonId = anna.id,
                occasionId = birthday.id,
                name = "Book",
                givenAt = LocalDate.of(2026, 3, 12),
                price = null,
                productUrl = null,
                note = null,
            ),
        )
        val fromAnna = giftRepository.addGift(
            GiftDetails(
                direction = GiftDirection.RECEIVED,
                giverPersonId = anna.id,
                recipientPersonId = null,
                occasionId = null,
                name = "Headphones",
                givenAt = LocalDate.of(2025, 11, 4),
                price = null,
                productUrl = null,
                note = null,
            ),
        )
        val scarfForAnna = giftIdeaRepository.addGiftIdea(
            GiftIdeaDetails(
                recipientPersonId = anna.id,
                occasionId = null,
                name = "Scarf",
                status = GiftIdeaStatus.PLANNED,
                estimatedPrice = null,
                actualPrice = null,
                productUrl = null,
                note = null,
            ),
        )
        markEntitySynced(SyncEntityType.GIFT, givenToAnna.id)
        markEntitySynced(SyncEntityType.GIFT_IDEA, scarfForAnna.id)

        repository.deletePerson(anna.id)

        // The person and their occasions are soft-deleted, not removed.
        assertNull(repository.getPerson(anna.id))
        assertEquals(1, countRows("person", anna.id))
        assertTrue(occasionRepository.observeOccasionsForPerson(anna.id).first().isEmpty())
        assertEquals(1, countRows("occasion", birthday.id))

        // Gifts stay readable and still show who they were for and from.
        val history = giftRepository.observeGifts().first()
        assertEquals(setOf(givenToAnna.id, fromAnna.id), history.map { it.gift.id }.toSet())
        val given = giftRepository.getGift(givenToAnna.id)!!
        assertEquals(anna.id, given.gift.recipientPersonId)
        assertEquals(anna, given.recipient)
        assertEquals(birthday.id, given.occasion?.id)
        assertEquals(anna, giftRepository.getGift(fromAnna.id)?.giver)

        // The idea stays an active plan for its (now deleted) recipient.
        val idea = giftIdeaRepository.getGiftIdea(scarfForAnna.id)!!
        assertEquals(anna.id, idea.idea.recipientPersonId)
        assertEquals(anna, idea.recipient)
        assertEquals(listOf(scarfForAnna.id), giftIdeaRepository.observeGiftIdeas().first().map { it.idea.id })

        // Only the person and occasion deletions reach the queue; gifts and ideas are untouched.
        assertNull(syncDao.findForEntity(SyncEntityType.GIFT, givenToAnna.id))
        assertEquals(SyncOperation.CREATE, syncDao.findForEntity(SyncEntityType.GIFT, fromAnna.id)?.operation)
        assertNull(syncDao.findForEntity(SyncEntityType.GIFT_IDEA, scarfForAnna.id))
        val pending = syncDao.getAllForOwner(ownerUserId)
        assertTrue(
            pending.none { it.operation == SyncOperation.DELETE && it.entityType in setOf(SyncEntityType.GIFT, SyncEntityType.GIFT_IDEA) },
        )
    }

    /** Simulates a completed push for a gift or idea: nothing is pending for it any more. */
    private suspend fun markEntitySynced(entityType: SyncEntityType, entityId: UUID) {
        when (entityType) {
            SyncEntityType.GIFT -> database.giftDao().run {
                update(findActiveById(entityId, ownerUserId)!!.copy(remoteState = RemoteState.SYNCED, version = 1))
            }
            SyncEntityType.GIFT_IDEA -> database.giftIdeaDao().run {
                update(findActiveById(entityId, ownerUserId)!!.copy(remoteState = RemoteState.SYNCED, version = 1))
            }
            else -> error("Not needed by these tests: $entityType")
        }
        syncDao.findForEntity(entityType, entityId)?.let { syncDao.delete(it) }
    }

    private fun birthdayOn(day: Int, month: Int) =
        OccasionDetails(OccasionType.BIRTHDAY, title = null, day = day, month = month, year = null, repeatsYearly = true)

    private suspend fun markOccasionSynced(occasionId: UUID) {
        val entity = occasionDao.findActiveById(occasionId, ownerUserId)!!
        occasionDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = 1))
        syncDao.findForEntity(SyncEntityType.OCCASION, occasionId)?.let { syncDao.delete(it) }
    }

    private suspend fun pendingMutationIds(): Map<UUID, UUID> =
        syncDao.getAllForOwner(ownerUserId).associate { it.entityId to it.mutationId }

    private fun pendingDelete(entityId: UUID, entityType: SyncEntityType) = SyncOperationEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        entityType = entityType,
        entityId = entityId,
        operation = SyncOperation.DELETE,
        mutationId = UUID.randomUUID(),
        inFlightMutationId = null,
        blockedReason = null,
        createdAt = clock.instant,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    )

    private suspend fun pendingOperation(
        entityId: UUID,
        entityType: SyncEntityType = SyncEntityType.PERSON,
    ): SyncOperation? = syncDao.findForEntity(entityType, entityId)
        ?.also { assertEquals("an operation has its entity's owner", ownerUserId, it.ownerUserId) }
        ?.operation

    /** Simulates a completed push: the backend has the row and nothing is pending. */
    private suspend fun markSynced(personId: UUID, version: Long) {
        val entity = personDao.findActiveById(personId, ownerUserId)!!
        personDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = version))
        syncDao.findForEntity(SyncEntityType.PERSON, personId)?.let { syncDao.delete(it) }
    }

    private fun countRows(personId: UUID): Int = countRows("person", personId)

    private fun countRows(table: String, id: UUID): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table WHERE id = ?", arrayOf(id.toString()))
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}
