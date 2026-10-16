package app.giftiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftNotFoundException
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.MutableClock
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
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class GiftRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val otherOwnerId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private lateinit var database: GiftiaryDatabase
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl

    private val giftDao get() = database.giftDao()
    private val syncDao get() = database.syncOperationDao()

    private lateinit var anna: Person
    private lateinit var bela: Person

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        val syncQueue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(ownerUserId)
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), syncQueue, activeOwner, clock,
            SyncRequester {},
        )
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), syncQueue, activeOwner, clock, SyncRequester {},
        )
        occasions = OccasionRepositoryImpl(
            database, database.occasionDao(), database.personDao(), syncQueue, activeOwner, clock, SyncRequester {},
        )
        anna = people.addPerson("Anna", "Sister")
        bela = people.addPerson("Béla", null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding a gift stores a local-only row and queues its creation`() = runTest {
        val gift = gifts.addGift(givenTo(anna.id).copy(price = Money(12500, "HUF")))

        val entity = giftDao.findActiveById(gift.id, ownerUserId)!!
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertNull(entity.sourceGiftIdeaId)
        assertEquals(Money(12500, "HUF"), gifts.getGift(gift.id)?.gift?.price)
        assertEquals(anna, gifts.getGift(gift.id)?.recipient)
        assertEquals(SyncOperation.CREATE, pendingOperation(gift.id))
    }

    @Test
    fun `updating a local-only gift keeps the pending create`() = runTest {
        val gift = gifts.addGift(givenTo(anna.id))
        clock.advanceBy(Duration.ofMinutes(1))

        gifts.updateGift(gift.id, givenTo(anna.id).copy(name = "Signed book"))

        val updated = gifts.getGift(gift.id)!!.gift
        assertEquals("Signed book", updated.name)
        assertEquals(clock.instant, updated.updatedAt)
        assertEquals(SyncOperation.CREATE, pendingOperation(gift.id))
    }

    @Test
    fun `updating a synced gift keeps its sync metadata and queues an update`() = runTest {
        val gift = gifts.addGift(givenTo(anna.id))
        markSynced(gift.id)

        gifts.updateGift(gift.id, givenTo(anna.id).copy(note = "She loved it"))

        val entity = giftDao.findActiveById(gift.id, ownerUserId)!!
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(5L, entity.version)
        assertEquals(SyncOperation.UPDATE, pendingOperation(gift.id))
    }

    @Test
    fun `deleting a local-only gift drops the pending create, deleting a synced one queues a delete`() = runTest {
        val localOnly = gifts.addGift(givenTo(anna.id))
        val synced = gifts.addGift(givenTo(bela.id))
        markSynced(synced.id)

        gifts.deleteGift(localOnly.id)
        gifts.deleteGift(synced.id)

        assertTrue(gifts.observeGifts().first().isEmpty())
        assertNull(pendingOperation(localOnly.id))
        assertEquals(SyncOperation.DELETE, pendingOperation(synced.id))
    }

    @Test
    fun `another owner's gift is never observed, returned, updated or deleted`() = runTest {
        val stranger = insertStranger()
        val theirs = gifts.addGift(givenTo(anna.id)).let { mine ->
            val entity = giftDao.findActiveById(mine.id, ownerUserId)!!
            giftDao.softDelete(mine.id, ownerUserId, clock.instant)
            entity.copy(id = UUID.randomUUID(), ownerUserId = otherOwnerId, recipientPersonId = stranger.id)
        }
        giftDao.insert(theirs)

        assertTrue(gifts.observeGifts().first().isEmpty())
        assertNull(gifts.getGift(theirs.id))
        val update = runCatching { gifts.updateGift(theirs.id, givenTo(anna.id)) }
        assertTrue(update.exceptionOrNull() is GiftNotFoundException)
        gifts.deleteGift(theirs.id)

        assertEquals(theirs, giftDao.findActiveById(theirs.id, otherOwnerId))
        assertNull(pendingOperation(theirs.id))
    }

    @Test
    fun `another owner's person cannot be referenced`() = runTest {
        val stranger = insertStranger()

        val asRecipient = runCatching { gifts.addGift(givenTo(stranger.id)) }
        val asGiver = runCatching { gifts.addGift(receivedFrom(stranger.id)) }

        assertTrue(asRecipient.exceptionOrNull() is PersonNotFoundException)
        assertTrue(asGiver.exceptionOrNull() is PersonNotFoundException)
        assertTrue(gifts.observeGifts().first().isEmpty())
    }

    @Test
    fun `a deleted person cannot be newly referenced`() = runTest {
        people.deletePerson(bela.id)
        val gift = gifts.addGift(givenTo(anna.id))

        val add = runCatching { gifts.addGift(givenTo(bela.id)) }
        val change = runCatching { gifts.updateGift(gift.id, givenTo(bela.id)) }

        assertTrue(add.exceptionOrNull() is PersonNotFoundException)
        assertTrue(change.exceptionOrNull() is PersonNotFoundException)
        assertEquals(anna.id, gifts.getGift(gift.id)?.gift?.recipientPersonId)
    }

    @Test
    fun `an occasion of the gift's person can be referenced`() = runTest {
        val birthday = occasions.addOccasion(anna.id, birthdayDetails)

        val gift = gifts.addGift(givenTo(anna.id).copy(occasionId = birthday.id))

        assertEquals(birthday, gifts.getGift(gift.id)?.occasion)
    }

    @Test
    fun `a general occasion can be referenced by a gift with anyone`() = runTest {
        val christmas = occasions.addOccasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))

        val given = gifts.addGift(givenTo(anna.id).copy(occasionId = christmas.id))
        val received = gifts.addGift(givenTo(anna.id).copy(direction = GiftDirection.RECEIVED, giverPersonId = bela.id, recipientPersonId = null, occasionId = christmas.id))

        assertEquals(christmas, gifts.getGift(given.id)?.occasion)
        assertEquals(christmas, gifts.getGift(received.id)?.occasion)
    }

    @Test
    fun `another person's occasion cannot be referenced`() = runTest {
        val belasBirthday = occasions.addOccasion(bela.id, birthdayDetails)

        val result = runCatching { gifts.addGift(givenTo(anna.id).copy(occasionId = belasBirthday.id)) }

        assertTrue(result.exceptionOrNull() is OccasionNotFoundException)
    }

    @Test
    fun `another owner's occasion cannot be referenced`() = runTest {
        val stranger = insertStranger()
        val theirBirthday = occasions.addOccasion(anna.id, birthdayDetails).let { mine ->
            val entity = database.occasionDao().findActiveById(mine.id, ownerUserId)!!
            entity.copy(id = UUID.randomUUID(), ownerUserId = otherOwnerId, personId = stranger.id)
        }
        database.occasionDao().insert(theirBirthday)

        val result = runCatching { gifts.addGift(givenTo(anna.id).copy(occasionId = theirBirthday.id)) }

        assertTrue(result.exceptionOrNull() is OccasionNotFoundException)
    }

    @Test
    fun `a deleted occasion cannot be newly referenced`() = runTest {
        val birthday = occasions.addOccasion(anna.id, birthdayDetails)
        occasions.deleteOccasion(birthday.id)
        val gift = gifts.addGift(givenTo(anna.id))

        val add = runCatching { gifts.addGift(givenTo(anna.id).copy(occasionId = birthday.id)) }
        val change = runCatching { gifts.updateGift(gift.id, givenTo(anna.id).copy(occasionId = birthday.id)) }

        assertTrue(add.exceptionOrNull() is OccasionNotFoundException)
        assertTrue(change.exceptionOrNull() is OccasionNotFoundException)
    }

    @Test
    fun `a gift keeps its deleted person and occasion when edited without changing them`() = runTest {
        val birthday = occasions.addOccasion(anna.id, birthdayDetails)
        val gift = gifts.addGift(givenTo(anna.id).copy(occasionId = birthday.id))
        people.deletePerson(anna.id) // also deletes her birthday

        gifts.updateGift(gift.id, givenTo(anna.id).copy(occasionId = birthday.id, name = "Signed book"))

        val entry = gifts.getGift(gift.id)!!
        assertEquals("Signed book", entry.gift.name)
        assertEquals(anna, entry.recipient)
        assertEquals(birthday.id, entry.occasion?.id)
    }

    @Test
    fun `a kept occasion must still belong to a participant when the person changes`() = runTest {
        val birthday = occasions.addOccasion(anna.id, birthdayDetails)
        val gift = gifts.addGift(givenTo(anna.id).copy(occasionId = birthday.id))

        val result = runCatching { gifts.updateGift(gift.id, givenTo(bela.id).copy(occasionId = birthday.id)) }

        assertTrue(result.exceptionOrNull() is OccasionNotFoundException)
        assertEquals(anna.id, gifts.getGift(gift.id)?.gift?.recipientPersonId)
    }

    @Test
    fun `gifts between two other people are supported`() = runTest {
        val gift = gifts.addGift(givenTo(bela.id).copy(giverPersonId = anna.id))

        val entry = gifts.getGift(gift.id)!!
        assertEquals(anna, entry.giver)
        assertEquals(bela, entry.recipient)
    }

    @Test
    fun `a failing queue write rolls back the gift change`() = runTest {
        val gift = gifts.addGift(givenTo(anna.id))
        markSynced(gift.id)
        // A pending DELETE for an active gift is inconsistent, so the queue refuses the UPDATE.
        syncDao.insert(
            SyncOperationEntity(
                id = UUID.randomUUID(),
                ownerUserId = ownerUserId,                entityType = SyncEntityType.GIFT,
                entityId = gift.id,
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

        val result = runCatching { gifts.updateGift(gift.id, givenTo(anna.id).copy(name = "Changed")) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Book", gifts.getGift(gift.id)?.gift?.name)
    }

    @Test
    fun `deleting a person keeps their gift history and still shows who it was for`() = runTest {
        val toAnna = gifts.addGift(givenTo(anna.id))
        val fromAnna = gifts.addGift(receivedFrom(anna.id))

        people.deletePerson(anna.id)

        val history = gifts.observeGifts().first()
        assertEquals(setOf(toAnna.id, fromAnna.id), history.map { it.gift.id }.toSet())
        assertTrue(history.all { (it.recipient ?: it.giver) == anna })
        assertEquals(2, gifts.observeGiftsForPerson(anna.id).first().size)
        assertEquals(SyncOperation.CREATE, pendingOperation(toAnna.id))
        assertTrue(people.observePeople().first().none { it.id == anna.id })
    }

    private val birthdayDetails = OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true)

    private fun givenTo(personId: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN,
        giverPersonId = null,
        recipientPersonId = personId,
        occasionId = null,
        name = "Book",
        givenAt = LocalDate.of(2026, 3, 12),
        price = null,
        productUrl = null,
        note = null,
    )

    private fun receivedFrom(personId: UUID) =
        givenTo(personId).copy(direction = GiftDirection.RECEIVED, giverPersonId = personId, recipientPersonId = null)

    private suspend fun insertStranger(): PersonEntity {
        val stranger = PersonEntity(
            id = UUID.randomUUID(),
            ownerUserId = otherOwnerId,
            name = "Stranger",
            relationship = null,
            photoPath = null,
            linkedUserId = null,
            version = 0,
            remoteState = RemoteState.LOCAL_ONLY,
            createdAt = clock.instant,
            updatedAt = clock.instant,
            deletedAt = null,
        )
        database.personDao().insert(stranger)
        return stranger
    }

    private suspend fun pendingOperation(giftId: UUID): SyncOperation? =
        syncDao.findForEntity(SyncEntityType.GIFT, giftId)
            ?.also { assertEquals("an operation has its entity's owner", ownerUserId, it.ownerUserId) }
            ?.operation

    private suspend fun markSynced(giftId: UUID) {
        val entity = giftDao.findActiveById(giftId, ownerUserId)!!
        giftDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = 5))
        syncDao.findForEntity(SyncEntityType.GIFT, giftId)?.let { syncDao.delete(it) }
    }
}
