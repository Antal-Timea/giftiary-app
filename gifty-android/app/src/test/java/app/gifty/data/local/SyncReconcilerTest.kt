package app.gifty.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftDetails
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

/**
 * Applying a committed in-flight mutation to Room. States are built through the real repositories
 * and queue; the test only plays sync's part: marking the current mutation in flight, and
 * marking rows as known to the backend.
 */
@RunWith(RobolectricTestRunner::class)
class SyncReconcilerTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-10T12:00:00.123Z"))
    private lateinit var database: GiftyDatabase
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var reconciler: SyncReconciler
    private lateinit var anna: Person

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        val owner = FakeActiveOwner(alice)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, owner, clock)
        occasions = OccasionRepositoryImpl(database, database.occasionDao(), database.personDao(), queue, owner, clock)
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue, owner, clock,
        )
        gifts = GiftRepositoryImpl(database, database.giftDao(), database.personDao(), database.occasionDao(), queue, owner, clock)
        reconciler = SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(),
        )
        anna = people.addPerson("Anna", "Sister")
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 1)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- the committed mutation was the latest change ----

    @Test
    fun `a committed create that is still the latest change completes, for every entity`() = runTest {
        val created = createOneOfEach()

        created.forEach { (type, id) ->
            val before = entity(type, id)
            val sent = send(type, id)

            assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, type, id, sent, resultingVersion = 1))

            assertNull("$type operation", operations.findForEntity(type, id))
            val after = entity(type, id)
            assertEquals(1L, after.version)
            assertEquals(RemoteState.SYNCED, after.remoteState)
            assertEquals("$type content untouched", before.content, after.content)
        }
    }

    @Test
    fun `a committed update that is still the latest change completes at the next version`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4))

        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertEquals(4L, entity(SyncEntityType.PERSON, anna.id).version)
        assertEquals("Anna Kovács", people.getPerson(anna.id)?.name)
    }

    @Test
    fun `a committed tombstone completes and the row stays, deleted`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 2)
        people.deletePerson(anna.id)
        val deletedAt = entity(SyncEntityType.PERSON, anna.id).deletedAt
        val sent = send(SyncEntityType.PERSON, anna.id)
        assertEquals(SyncOperation.DELETE, operations.findForEntity(SyncEntityType.PERSON, anna.id)?.operation)

        assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 3))

        val after = entity(SyncEntityType.PERSON, anna.id)
        assertNotNull(deletedAt)
        assertEquals(deletedAt, after.deletedAt)
        assertEquals(3L, after.version)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
    }

    // ---- a newer change is pending ----

    @Test
    fun `a committed update with a newer edit pending keeps that edit queued against the new version`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        operations.update(pending(SyncEntityType.PERSON, anna.id).copy(attemptCount = 2, lastErrorCode = "timeout"))
        clock.advanceBy(Duration.ofMinutes(1))
        people.updatePerson(anna.id, "Anna K.", "Big sister")
        val edit = pending(SyncEntityType.PERSON, anna.id)

        assertEquals(
            CommitOutcome.NEWER_MUTATION_PENDING,
            reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4),
        )

        assertEquals(edit.copy(inFlightMutationId = null), pending(SyncEntityType.PERSON, anna.id))
        val after = entity(SyncEntityType.PERSON, anna.id)
        assertEquals(4L, after.version)
        assertEquals(RemoteState.SYNCED, after.remoteState)
        // The local content is the newer edit, not what the committed mutation carried.
        assertEquals("Anna K.", people.getPerson(anna.id)?.name)
    }

    @Test
    fun `failed attempts of the committed mutation do not carry over to the newer one`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        people.updatePerson(anna.id, "Anna K.", "Sister")
        // Resends of the in-flight mutation after the edit, while its outcome was unknown.
        val edit = pending(SyncEntityType.PERSON, anna.id)
        operations.update(edit.copy(attemptCount = 3, lastAttemptAt = clock.instant, lastErrorCode = "timeout"))

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4)

        assertEquals(edit.copy(inFlightMutationId = null), pending(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a committed create with a newer edit pending continues as an update, for every entity`() = runTest {
        val created = createOneOfEach()
        val sent = created.associate { (type, id) -> (type to id) to send(type, id) }
        // The user edits each entity while its create is still unresolved.
        people.updatePerson(created[0].second, "Béla B.", null)
        occasions.updateOccasion(created[1].second, birthday.copy(day = 13))
        ideas.updateStatus(created[2].second, GiftIdeaStatus.PURCHASED)
        gifts.updateGift(created[3].second, giftDetails.copy(name = "Blue scarf"))

        created.forEach { (type, id) ->
            val edit = pending(type, id)
            val before = entity(type, id)

            assertEquals(
                CommitOutcome.NEWER_MUTATION_PENDING,
                reconciler.applyCommitted(alice, type, id, sent.getValue(type to id), resultingVersion = 1),
            )

            val next = pending(type, id)
            assertEquals("$type", edit.copy(operation = SyncOperation.UPDATE, inFlightMutationId = null), next)
            assertEquals("$type keeps its newer mutation", edit.mutationId, next.mutationId)
            val after = entity(type, id)
            assertEquals(1L, after.version)
            assertEquals(RemoteState.SYNCED, after.remoteState)
            assertEquals("$type content untouched", before.content, after.content)
        }
    }

    @Test
    fun `a committed create of a person deleted meanwhile continues as the deletion`() = runTest {
        val bela = people.addPerson("Béla", null)
        val sent = send(SyncEntityType.PERSON, bela.id)
        people.deletePerson(bela.id)
        val tombstone = pending(SyncEntityType.PERSON, bela.id)
        assertEquals(SyncOperation.CREATE, tombstone.operation)

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, bela.id, sent, 1)

        assertEquals(
            tombstone.copy(operation = SyncOperation.DELETE, inFlightMutationId = null),
            pending(SyncEntityType.PERSON, bela.id),
        )
        assertNotNull(entity(SyncEntityType.PERSON, bela.id).deletedAt)
    }

    @Test
    fun `a gift deleted while its create was in flight keeps its deletion queued once the create is confirmed`() = runTest {
        val gift = gifts.addGift(giftDetails)
        val sent = send(SyncEntityType.GIFT, gift.id)
        gifts.deleteGift(gift.id)
        // Room v3 keeps the create, because the create in flight may have committed.
        val tombstone = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(SyncOperation.CREATE, tombstone.operation)
        val deletedAt = entity(SyncEntityType.GIFT, gift.id).deletedAt

        assertEquals(
            CommitOutcome.NEWER_MUTATION_PENDING,
            reconciler.applyCommitted(alice, SyncEntityType.GIFT, gift.id, sent, 1),
        )

        val next = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(tombstone.copy(operation = SyncOperation.DELETE, inFlightMutationId = null), next)
        val after = entity(SyncEntityType.GIFT, gift.id)
        assertEquals(deletedAt, after.deletedAt)
        assertEquals(1L, after.version)
        assertEquals(RemoteState.SYNCED, after.remoteState)
    }

    // ---- results that do not belong to the in-flight slot ----

    @Test
    fun `a result for another mutation, a repeated result or the current mutation changes nothing`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        people.updatePerson(anna.id, "Anna K.", "Sister")
        val edit = pending(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, UUID.randomUUID(), 4))
            // The newer mutation is not in flight, so it cannot have committed.
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, edit.mutationId, 4))
        }

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4)
        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4))
        }
    }

    @Test
    fun `another account's result changes nothing`() = runTest {
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(bob, SyncEntityType.PERSON, anna.id, sent, 2))
        }
    }

    @Test
    fun `an impossible version is refused and nothing is applied`() = runTest {
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            listOf(0L, -1L).forEach { version ->
                val impossible = runCatching { reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, version) }
                assertTrue("$version", impossible.exceptionOrNull() is IllegalArgumentException)
            }
            // Anna is at version 1, so her in-flight update can only have committed as version 2.
            val skipped = runCatching { reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 5) }
            assertTrue(skipped.exceptionOrNull() is IllegalStateException)
        }
    }

    // ---- fixtures ----

    private val birthday = OccasionDetails(OccasionType.BIRTHDAY, title = null, day = 12, month = 3, year = null, repeatsYearly = true)

    private val giftDetails get() = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = anna.id, occasionId = null,
        name = "Scarf", givenAt = LocalDate.of(2026, 3, 12), price = Money(9900, "HUF"), productUrl = null, note = null,
    )

    /** A person, an occasion, an idea and a gift, each created locally with its create pending. */
    private suspend fun createOneOfEach(): List<Pair<SyncEntityType, UUID>> {
        val bela = people.addPerson("Béla", null)
        val occasion = occasions.addOccasion(anna.id, birthday)
        val idea = ideas.addGiftIdea(
            GiftIdeaDetails(anna.id, occasionId = null, name = "Book", status = GiftIdeaStatus.IDEA, estimatedPrice = null,
                actualPrice = null, productUrl = null, note = null),
        )
        val gift = gifts.addGift(giftDetails)
        return listOf(
            SyncEntityType.PERSON to bela.id,
            SyncEntityType.OCCASION to occasion.id,
            SyncEntityType.GIFT_IDEA to idea.id,
            SyncEntityType.GIFT to gift.id,
        )
    }

    /** What sync does before sending: the current payload becomes the mutation in flight. */
    private suspend fun send(type: SyncEntityType, id: UUID): UUID {
        val operation = pending(type, id)
        assertEquals(1, operations.markInFlight(operation.id, operation.mutationId))
        return operation.mutationId
    }

    private suspend fun pending(type: SyncEntityType, id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(type, id)) { "No pending $type operation" }

    /** As if a push had completed earlier: the backend has the row at [version], nothing is pending. */
    private suspend fun markKnownToBackend(type: SyncEntityType, id: UUID, version: Long) {
        check(type == SyncEntityType.PERSON)
        database.personDao().run { update(findById(id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)) }
        operations.findForEntity(type, id)?.let { operations.delete(it) }
    }

    private suspend fun assertUnchanged(type: SyncEntityType, id: UUID, block: suspend () -> Unit) {
        val entityBefore = entity(type, id)
        val operationBefore = operations.findForEntity(type, id)
        block()
        assertEquals(entityBefore, entity(type, id))
        assertEquals(operationBefore, operations.findForEntity(type, id))
    }

    /** The sync metadata of a row, and everything else about it as [content]. */
    private data class Snapshot(val version: Long, val remoteState: RemoteState, val deletedAt: Instant?, val content: Any)

    private suspend fun entity(type: SyncEntityType, id: UUID): Snapshot = when (type) {
        SyncEntityType.PERSON -> database.personDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.OCCASION -> database.occasionDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.GIFT_IDEA -> database.giftIdeaDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.GIFT -> database.giftDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        else -> error("Not synced: $type")
    }
}
