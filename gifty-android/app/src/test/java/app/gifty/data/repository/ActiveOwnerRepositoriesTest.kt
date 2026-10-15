package app.gifty.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.sync.SyncRequester
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftDetails
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.NoActiveOwnerException
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionSnapshot
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** How the repositories follow the active owner: one account's data at a time, none without one. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActiveOwnerRepositoriesTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val activeOwner = FakeActiveOwner(alice)
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))

    private lateinit var database: GiftyDatabase
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java)
            .build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {})
        occasions = OccasionRepositoryImpl(
            database, database.occasionDao(), database.personDao(), queue, activeOwner, clock, SyncRequester {},
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock,
            SyncRequester {},
        )
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(),
            queue, activeOwner, clock,
            SyncRequester {},
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a running people list switches from one account to none to the next`() = runTest {
        val anna = people.addPerson("Anna", null)
        val names = collect(people.observePeople())
        awaitLatest(names) { it == listOf("Anna") }

        activeOwner.ownerId.value = null
        awaitLatest(names) { it.isEmpty() }

        activeOwner.ownerId.value = bob
        people.addPerson("Béla", null)
        awaitLatest(names) { it == listOf("Béla") }

        assertTrue("Alice's people never reached Bob's list", names.value.none { it == anna.name })
        assertNull(people.getPerson(anna.id))
    }

    @Test
    fun `another account sees none of the first account's occasions, gifts or ideas`() = runTest {
        val anna = people.addPerson("Anna", null)
        val birthday = occasions.addOccasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
        val gift = gifts.addGift(giftFor(anna.id))
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        activeOwner.ownerId.value = bob

        assertEquals(emptyList<Any>(), occasions.observeOccasionsForPerson(anna.id).first())
        assertEquals(emptyList<Any>(), occasions.observeOccasions().first())
        assertEquals(OccasionSnapshot(bob, emptyList()), occasions.getOccasionSnapshot())
        assertEquals(emptyList<Any>(), gifts.observeGifts().first())
        assertEquals(emptyList<Any>(), gifts.observeGiftsForPerson(anna.id).first())
        assertEquals(emptyList<Any>(), ideas.observeGiftIdeas().first())
        assertEquals(emptyList<Any>(), ideas.observeGiftIdeasForPerson(anna.id).first())
        assertNull(occasions.getOccasion(birthday.id))
        assertNull(gifts.getGift(gift.id))
        assertNull(ideas.getGiftIdea(idea.id))
    }

    @Test
    fun `without an owner nothing can be read`() = runTest {
        val anna = people.addPerson("Anna", null)
        val gift = gifts.addGift(giftFor(anna.id))

        activeOwner.ownerId.value = null

        assertEquals(emptyList<Person>(), people.observePeople().first())
        assertEquals(emptyList<Any>(), occasions.observeOccasions().first())
        assertNull(occasions.getOccasionSnapshot())
        assertNull(people.observePerson(anna.id).first())
        assertNull(people.getPerson(anna.id))
        assertNull(gifts.observeGift(gift.id).first())
        assertNull(gifts.getGift(gift.id))
    }

    @Test
    fun `without an owner nothing can be written`() = runTest {
        val anna = people.addPerson("Anna", null)
        activeOwner.ownerId.value = null
        val rowsBefore = rowCounts()

        val attempts = listOf(
            runCatching { people.addPerson("Béla", null) },
            runCatching { people.updatePerson(anna.id, "Anna Kovács", null) },
            runCatching { people.deletePerson(anna.id) },
            runCatching { gifts.addGift(giftFor(anna.id)) },
            runCatching { ideas.addGiftIdea(ideaFor(anna.id)) },
        )

        attempts.forEach { assertTrue(it.exceptionOrNull() is NoActiveOwnerException) }
        assertEquals(rowsBefore, rowCounts())
        activeOwner.ownerId.value = alice
        assertEquals("Anna", people.getPerson(anna.id)?.name)
    }

    @Test
    fun `each account's writes and queued operations carry that account`() = runTest {
        val anna = people.addPerson("Anna", null)
        activeOwner.ownerId.value = bob
        val bela = people.addPerson("Béla", null)

        assertEquals(alice, ownerOf("person", anna.id))
        assertEquals(bob, ownerOf("person", bela.id))
        val queue = database.syncOperationDao()
        assertEquals(listOf(anna.id), queue.getPending(alice, limit = 10).map { it.entityId })
        assertEquals(listOf(bela.id), queue.getPending(bob, limit = 10).map { it.entityId })
        assertEquals(SyncEntityType.PERSON, queue.findForEntity(SyncEntityType.PERSON, bela.id)?.entityType)
    }

    @Test
    fun `a related row of another account is never shown`() = runTest {
        val anna = people.addPerson("Anna", null)
        activeOwner.ownerId.value = bob
        val bela = people.addPerson("Béla", null)
        val gift = gifts.addGift(giftFor(bela.id))
        // A corrupted reference to Alice's person, as only a broken sync or bug could produce.
        database.giftDao().run { update(findActiveById(gift.id, bob)!!.copy(recipientPersonId = anna.id)) }

        val shown = gifts.getGift(gift.id)!!

        assertEquals(anna.id, shown.gift.recipientPersonId)
        assertNull(shown.recipient)
    }

    // ---- helpers ----

    private fun TestScope.collect(flow: Flow<List<Person>>): MutableStateFlow<List<String>> {
        val names = MutableStateFlow<List<String>>(listOf("<nothing yet>"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { list -> names.value = list.map { it.name } }
        }
        return names
    }

    // Room delivers on its own threads, so waits use real time.
    private suspend fun awaitLatest(names: MutableStateFlow<List<String>>, condition: (List<String>) -> Boolean) {
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { names.first(condition) } }
    }

    private fun rowCounts(): Map<String, Int> =
        listOf("person", "occasion", "gift", "gift_idea", "sync_operation").associateWith { table ->
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        }

    private fun ownerOf(table: String, id: UUID): UUID =
        database.openHelper.readableDatabase.query("SELECT ownerUserId FROM $table WHERE id = ?", arrayOf(id.toString()))
            .use { it.moveToFirst(); UUID.fromString(it.getString(0)) }

    private fun giftFor(personId: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = personId, occasionId = null,
        name = "Scarf", givenAt = LocalDate.parse("2026-03-12"), price = null, productUrl = null, note = null,
    )

    private fun ideaFor(personId: UUID) = GiftIdeaDetails(
        recipientPersonId = personId, occasionId = null, name = "Book", status = GiftIdeaStatus.IDEA,
        estimatedPrice = null, actualPrice = null, productUrl = null, note = null,
    )
}
