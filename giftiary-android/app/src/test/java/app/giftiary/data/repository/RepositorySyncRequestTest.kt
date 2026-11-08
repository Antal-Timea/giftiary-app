package app.giftiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.MutableClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Every write of the four repositories asks for sync exactly once, and only after its transaction
 * committed: the queue the request sees is already the committed one. A failed write asks for
 * nothing.
 */
@RunWith(RobolectricTestRunner::class)
class RepositorySyncRequestTest {

    private val owner = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-10-11T12:00:00.123Z"))
    private lateinit var database: GiftiaryDatabase
    private lateinit var requests: RecordingSyncRequester
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl

    private val operations get() = database.syncOperationDao()

    /** Records, for each request, the queue as another connection sees it at that moment. */
    private class RecordingSyncRequester(private val database: GiftiaryDatabase, private val owner: UUID) : SyncRequester {
        val seenQueues = mutableListOf<List<SyncOperationEntity>>()
        var insideTransaction = false

        override fun requestSync() {
            if (database.inTransaction()) {
                insideTransaction = true
                return
            }
            seenQueues += runBlocking { database.syncOperationDao().getAllForOwner(owner) }
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
        requests = RecordingSyncRequester(database, owner)
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(owner)
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, requests,
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        occasions = OccasionRepositoryImpl(database, database.occasionDao(), database.personDao(), queue, activeOwner, clock, requests)
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue,
            activeOwner, clock, requests,
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, requests,
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
    }

    @After
    fun tearDown() {
        assertFalse("sync was requested inside a Room transaction", requests.insideTransaction)
        database.close()
    }

    // ---- Person ----

    @Test
    fun `addPerson requests sync once its create is committed`() = runTest {
        val anna = expectOneRequest("addPerson") { people.addPerson("Anna", null) }
        assertQueued("addPerson", SyncEntityType.PERSON, anna.id)
    }

    @Test
    fun `updatePerson requests sync once its change is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        expectOneRequest("updatePerson") { people.updatePerson(anna.id, "Anna K.", null) }
    }

    @Test
    fun `deletePerson requests sync once, after the person and their occasions are deleted`() = runTest {
        val anna = people.addPerson("Anna", null)
        val birthday = occasions.addOccasion(anna.id, birthday())
        val nameDay = occasions.addOccasion(anna.id, birthday().copy(type = OccasionType.NAME_DAY, day = 3))

        expectOneRequest("deletePerson") { people.deletePerson(anna.id) }

        assertQueued("deletePerson", SyncEntityType.PERSON, anna.id)
        assertQueued("deletePerson", SyncEntityType.OCCASION, birthday.id)
        assertQueued("deletePerson", SyncEntityType.OCCASION, nameDay.id)
    }

    // ---- Occasion ----

    @Test
    fun `addOccasion requests sync once its create is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val birthday = expectOneRequest("addOccasion") { occasions.addOccasion(anna.id, birthday()) }
        assertQueued("addOccasion", SyncEntityType.OCCASION, birthday.id)
    }

    @Test
    fun `updateOccasion requests sync once its change is committed`() = runTest {
        val birthday = occasions.addOccasion(null, birthday())
        expectOneRequest("updateOccasion") { occasions.updateOccasion(birthday.id, birthday().copy(day = 13)) }
    }

    @Test
    fun `deleteOccasion requests sync once its deletion is committed`() = runTest {
        val birthday = occasions.addOccasion(null, birthday())
        expectOneRequest("deleteOccasion") { occasions.deleteOccasion(birthday.id) }
    }

    // ---- GiftIdea ----

    @Test
    fun `addGiftIdea requests sync once its create is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = expectOneRequest("addGiftIdea") { ideas.addGiftIdea(ideaFor(anna.id)) }
        assertQueued("addGiftIdea", SyncEntityType.GIFT_IDEA, idea.id)
    }

    @Test
    fun `updateGiftIdea requests sync once its change is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        expectOneRequest("updateGiftIdea") { ideas.updateGiftIdea(idea.id, ideaFor(anna.id).copy(name = "Gloves")) }
    }

    @Test
    fun `updateStatus requests sync once its change is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        expectOneRequest("updateStatus") { ideas.updateStatus(idea.id, GiftIdeaStatus.PLANNED) }
    }

    @Test
    fun `deleteGiftIdea requests sync once its deletion is committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        expectOneRequest("deleteGiftIdea") { ideas.deleteGiftIdea(idea.id) }
    }

    @Test
    fun `markAsGiven requests sync once, after both the gift and the archived idea are committed`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        val gift = expectOneRequest("markAsGiven") { ideas.markAsGiven(idea.id, LocalDate.of(2026, 10, 11), null) }

        assertQueued("markAsGiven", SyncEntityType.GIFT_IDEA, idea.id)
        assertQueued("markAsGiven", SyncEntityType.GIFT, gift.id)
    }

    // ---- Gift ----

    @Test
    fun `addGift requests sync once its create is committed`() = runTest {
        val gift = expectOneRequest("addGift") { gifts.addGift(giftDetails()) }
        assertQueued("addGift", SyncEntityType.GIFT, gift.id)
    }

    @Test
    fun `updateGift requests sync once its change is committed`() = runTest {
        val gift = gifts.addGift(giftDetails())
        expectOneRequest("updateGift") { gifts.updateGift(gift.id, giftDetails().copy(name = "Book")) }
    }

    @Test
    fun `deleteGift requests sync once its deletion is committed`() = runTest {
        val gift = gifts.addGift(giftDetails())
        // A never-synced gift's create and delete cancel out: the committed queue no longer has it.
        expectOneRequest("deleteGift") { gifts.deleteGift(gift.id) }
    }

    // ---- failures ----

    @Test
    fun `a write whose transaction fails requests nothing`() = runTest {
        val anna = people.addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        ideas.markAsGiven(idea.id, LocalDate.of(2026, 10, 11), null)
        val missing = UUID.randomUUID()
        val failing = listOf<Pair<String, suspend () -> Unit>>(
            "updatePerson" to { people.updatePerson(missing, "Nobody", null) },
            "addOccasion" to { occasions.addOccasion(missing, birthday()) },
            "updateOccasion" to { occasions.updateOccasion(missing, birthday()) },
            "addGiftIdea" to { ideas.addGiftIdea(ideaFor(missing)) },
            "updateStatus" to { ideas.updateStatus(missing, GiftIdeaStatus.PLANNED) },
            "markAsGiven" to { ideas.markAsGiven(idea.id, LocalDate.of(2026, 10, 12), null) },
            "updateGift" to { gifts.updateGift(missing, giftDetails()) },
        )
        val queue = operations.getAllForOwner(owner)
        val before = requests.seenQueues.size

        failing.forEach { (method, write) ->
            assertTrue("$method should have failed", runCatching { write() }.isFailure)
            assertEquals("$method requested sync", before, requests.seenQueues.size)
        }
        assertEquals(queue, operations.getAllForOwner(owner))
    }

    // ---- fixtures ----

    /**
     * Runs [write] and checks it requested sync exactly once, seeing the queue exactly as it was
     * committed, which differs from the queue before the write.
     */
    private suspend fun <T> expectOneRequest(method: String, write: suspend () -> T): T {
        val queueBefore = operations.getAllForOwner(owner)
        val requestsBefore = requests.seenQueues.size

        val result = write()

        assertEquals("$method: requests", requestsBefore + 1, requests.seenQueues.size)
        val seen = requests.seenQueues.last()
        assertEquals("$method: the request must see the committed queue", operations.getAllForOwner(owner), seen)
        assertNotEquals("$method: the write must have changed the queue", queueBefore, seen)
        return result
    }

    private fun assertQueued(method: String, type: SyncEntityType, id: UUID) {
        assertTrue(
            "$method: $type should be queued when sync is requested",
            requests.seenQueues.last().any { it.entityType == type && it.entityId == id },
        )
    }

    private fun birthday() =
        OccasionDetails(OccasionType.BIRTHDAY, title = null, day = 12, month = 5, year = null, repeatsYearly = true)

    private fun ideaFor(personId: UUID) = GiftIdeaDetails(
        recipientPersonId = personId, occasionId = null, name = "Scarf", status = GiftIdeaStatus.IDEA,
        estimatedPrice = null, actualPrice = null, productUrl = null, note = null,
    )

    private fun giftDetails() = GiftDetails(
        direction = GiftDirection.RECEIVED, giverPersonId = null, recipientPersonId = null, occasionId = null,
        name = "Scarf", givenAt = LocalDate.of(2026, 5, 12), price = null, productUrl = null, note = null,
    )
}
