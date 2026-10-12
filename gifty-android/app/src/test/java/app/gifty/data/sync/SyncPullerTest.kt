package app.gifty.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.SyncReconciler
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteException
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.toRemoteFields
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.GiftDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
 * Pulling an account's backend rows into real Room, with [FakeBackend] as the backend. Local state
 * is made through the real repositories and [SyncPusher], so queue rows are exactly the app's.
 * Every pull here is checked to have queued nothing.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPullerTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-12T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(alice)
    private val alicesAccount = RemoteAccount.capture(alice, token(alice))
    private lateinit var database: GiftyDatabase
    private lateinit var backend: FakeBackend
    private lateinit var puller: SyncPuller
    private lateinit var people: PersonRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl

    private val operations get() = database.syncOperationDao()
    private val personDao get() = database.personDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {})
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {},
        )
        backend = FakeBackend(database)
        puller = SyncPuller(
            database, backend, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(), activeOwner,
        )
    }

    @After
    fun tearDown() {
        assertTrue("a remote read was made inside a Room transaction", !backend.requestInsideTransaction)
        database.close()
    }

    // ---- applying ----

    @Test
    fun `a person the backend has and Room does not is inserted as synced`() = runTest {
        val anna = remotePerson("Anna", version = 3)

        assertEquals(applied(inserted = 1), pull())

        val local = personDao.findById(anna, alice)!!
        assertEquals("Anna", local.name)
        assertEquals(3L, local.version)
        assertEquals(RemoteState.SYNCED, local.remoteState)
        assertEquals(REMOTE_TIME, local.createdAt)
    }

    @Test
    fun `a newer backend version replaces the whole row, timestamps included`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val before = personDao.findById(anna, alice)!!
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna K.", "Sister", null, null, null))

        assertEquals(applied(updated = 1), pull())

        assertEquals(
            before.copy(name = "Anna K.", relationship = "Sister", version = 2, createdAt = REMOTE_TIME, updatedAt = REMOTE_TIME),
            personDao.findById(anna, alice),
        )
    }

    @Test
    fun `a backend tombstone deletes the local row softly, and one Room lacks is inserted`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val deletedAt = Instant.parse("2026-10-11T08:00:00Z")
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna", null, null, null, deletedAt))
        val gone = remotePerson("Béla", deletedAt = deletedAt)

        assertEquals(applied(inserted = 1, updated = 1), pull())

        assertEquals(deletedAt, personDao.findById(anna, alice)!!.deletedAt)
        assertEquals(deletedAt, personDao.findById(gone, alice)!!.deletedAt)
        assertNull("deleted rows stay out of normal reads", personDao.findActiveById(anna, alice))
    }

    @Test
    fun `the same version is left as it is, without comparing content`() = runTest {
        val anna = syncedPerson("Anna", version = 2)
        val before = personDao.findById(anna, alice)!!
        // Self-pushed rows keep their own timestamps, so even other content is no reason to write.
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Not Anna", null, null, null, null))

        assertEquals(applied(unchanged = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
    }

    @Test
    fun `an older backend version is skipped as stale`() = runTest {
        val anna = syncedPerson("Anna", version = 4)
        val before = personDao.findById(anna, alice)!!
        backend.seed(anna, alice, 3, UUID.randomUUID(), RemotePersonFields("Old Anna", null, null, null, null))

        assertEquals(applied(skippedStale = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
    }

    @Test
    fun `a never-pushed gift deleted locally is not brought back by the backend's copy`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val gift = gifts.addGift(giftFor(anna))
        gifts.deleteGift(gift.id)
        // Its create and delete cancelled out: a local-only tombstone with nothing queued.
        assertNull(operations.findForEntity(SyncEntityType.GIFT, gift.id))
        val before = database.giftDao().findById(gift.id, alice)!!
        assertEquals(RemoteState.LOCAL_ONLY, before.remoteState)
        backend.seed(gift.id, alice, 1, UUID.randomUUID(), giftFields(recipient = anna))

        assertEquals(applied(unchanged = 1, skippedLocalOnly = 1), pull())

        assertEquals(before, database.giftDao().findById(gift.id, alice))
    }

    @Test
    fun `rows the backend does not have are never deleted`() = runTest {
        val synced = syncedPerson("Anna", version = 1)
        backend.rows.remove(synced)
        val localOnly = people.addPerson("Béla", null)
        val before = listOf(personDao.findById(synced, alice), personDao.findById(localOnly.id, alice))

        assertEquals(applied(), pull())

        assertEquals(before, listOf(personDao.findById(synced, alice), personDao.findById(localOnly.id, alice)))
    }

    @Test
    fun `an archived idea is applied with its archive time`() = runTest {
        val anna = remotePerson("Anna")
        val archivedAt = Instant.parse("2026-10-11T09:00:00Z")
        val idea = remoteIdea(anna, archivedAt = archivedAt)

        assertEquals(applied(inserted = 2), pull())

        assertEquals(archivedAt, database.giftIdeaDao().findById(idea, alice)!!.archivedAt)
    }

    // ---- queued local changes always win ----

    @Test
    fun `a queued create is left alone even though the backend has the same id`() = runTest {
        val anna = people.addPerson("Anna", null)
        val before = personDao.findById(anna.id, alice)!!
        backend.seed(anna.id, alice, 1, UUID.randomUUID(), RemotePersonFields("Someone else", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertEquals(before, personDao.findById(anna.id, alice))
    }

    @Test
    fun `a queued update is left alone, however new the backend's version`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna (local)", null)
        val before = personDao.findById(anna, alice)!!
        backend.seed(anna, alice, 5, UUID.randomUUID(), RemotePersonFields("Anna (remote)", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
    }

    @Test
    fun `a queued deletion is left alone while the backend still has the row live`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.deletePerson(anna)
        val before = personDao.findById(anna, alice)!!
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
        assertNotNull(before.deletedAt)
    }

    @Test
    fun `a blocked change is still local intent and is left alone`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna (local)", null)
        val operation = pending(anna)
        operations.blockCurrentMutation(operation.id, operation.mutationId, BlockReason.REJECTED.name)
        val before = personDao.findById(anna, alice)!!
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna (remote)", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
    }

    @Test
    fun `a change in flight with a newer one queued is left alone, mutation slots and version included`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna M1", null)
        val m1 = pending(anna)
        operations.markInFlight(m1.id, m1.mutationId)
        people.updatePerson(anna, "Anna M2", null)
        val queued = pending(anna)
        assertEquals(m1.mutationId, queued.inFlightMutationId)
        val before = personDao.findById(anna, alice)!!
        // The backend even shows M1 as committed: that is the push's evidence to use, not the pull's.
        backend.seed(anna, alice, 2, m1.mutationId, RemotePersonFields("Anna M1", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertEquals(before, personDao.findById(anna, alice))
        assertEquals(queued, pending(anna))
    }

    @Test
    fun `a queued row of an unclaimed or other account still keeps the entity from being pulled`() = runTest {
        val id = UUID.randomUUID()
        operations.insert(
            SyncOperationEntity(
                id = UUID.randomUUID(), ownerUserId = null, entityType = SyncEntityType.PERSON, entityId = id,
                operation = SyncOperation.CREATE, mutationId = UUID.randomUUID(), inFlightMutationId = null, blockedReason = null,
                createdAt = clock.instant, attemptCount = 0, lastAttemptAt = null, lastErrorCode = null,
            ),
        )
        backend.seed(id, alice, 1, UUID.randomUUID(), RemotePersonFields("Anna", null, null, null, null))

        assertEquals(applied(skippedQueued = 1), pull())

        assertNull(personDao.findById(id, alice))
    }

    // ---- references ----

    @Test
    fun `tables are read children first and written parents first`() = runTest {
        val anna = remotePerson("Anna")
        val birthday = remoteOccasion(person = anna)
        val idea = remoteIdea(anna, occasion = birthday)
        val gift = remoteGift(recipient = anna, occasion = birthday, sourceIdea = idea)

        // Room's foreign keys would refuse any child written before its parent.
        assertEquals(applied(inserted = 4), pull())

        assertEquals(listOf("gifts", "gift_ideas", "occasions", "people"), backend.fetches)
        assertEquals(idea, database.giftDao().findById(gift, alice)!!.sourceGiftIdeaId)
        assertEquals(birthday, database.giftIdeaDao().findById(idea, alice)!!.occasionId)
    }

    @Test
    fun `a backend child of a parent with a queued change is applied, and the parent left alone`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna (local)", null)
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna (remote)", null, null, null, null))
        val idea = remoteIdea(anna)

        assertEquals(applied(inserted = 1, skippedQueued = 1), pull())

        assertEquals("Anna (local)", personDao.findById(anna, alice)!!.name)
        assertEquals(anna, database.giftIdeaDao().findById(idea, alice)!!.recipientPersonId)
    }

    @Test
    fun `an idea is checked against its occasion as Room holds it, also when that occasion is queued`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val bela = syncedPerson("Béla", version = 1)
        val birthday = syncedOccasion(person = anna)
        // A queued local change keeps the occasion out of the pull; its person is still Anna's.
        database.occasionDao().update(database.occasionDao().findById(birthday, alice)!!.copy(day = 13))
        SyncOperationQueue(operations, clock).enqueue(alice, SyncEntityType.OCCASION, birthday, SyncOperation.UPDATE, RemoteState.SYNCED)
        val forAnna = remoteIdea(anna, occasion = birthday)

        assertEquals(applied(inserted = 1, unchanged = 2, skippedQueued = 1), pull())
        assertNotNull(database.giftIdeaDao().findById(forAnna, alice))

        val forBela = remoteIdea(bela, occasion = birthday)
        assertEquals(PullResult.Failed(RemoteFailure.SERVER), pull())
        assertNull("nothing applied", database.giftIdeaDao().findById(forBela, alice))
    }

    @Test
    fun `a gift whose occasion belongs to neither its giver nor its recipient fails the pull`() = runTest {
        val anna = remotePerson("Anna")
        val bela = remotePerson("Béla")
        val belasBirthday = remoteOccasion(person = bela)
        val gift = remoteGift(recipient = anna, occasion = belasBirthday)

        assertEquals(PullResult.Failed(RemoteFailure.SERVER), pull())

        listOf(anna, bela).forEach { assertNull(personDao.findById(it, alice)) }
        assertNull(database.giftDao().findById(gift, alice))
    }

    @Test
    fun `an occasion moved to another person by the backend fails the pull`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val bela = syncedPerson("Béla", version = 1)
        val birthday = syncedOccasion(person = anna)
        val before = database.occasionDao().findById(birthday, alice)!!
        backend.seed(birthday, alice, 2, UUID.randomUUID(), occasionFields(person = bela))
        val newcomer = remotePerson("Cecil")

        assertEquals(PullResult.Failed(RemoteFailure.SERVER), pull())

        assertEquals(before, database.occasionDao().findById(birthday, alice))
        assertNull(personDao.findById(newcomer, alice))
    }

    // ---- invalid backend data: nothing is applied ----

    @Test
    fun `an occasion on 31 February fails the whole pull`() = runTest {
        val anna = remotePerson("Anna")
        remoteOccasion(person = anna, day = 31, month = 2)

        assertNothingApplied(anna)
    }

    @Test
    fun `invalid money fails the whole pull`() = runTest {
        val anna = remotePerson("Anna")
        backend.seed(
            UUID.randomUUID(), alice, 1, UUID.randomUUID(),
            giftFields(recipient = anna).copy(priceAmountMinor = 100, priceCurrencyCode = "huf"),
        )

        assertNothingApplied(anna)
    }

    @Test
    fun `a version below 1 fails the whole pull`() = runTest {
        val anna = remotePerson("Anna")
        remotePerson("Béla", version = 0)

        assertNothingApplied(anna)
    }

    // ---- races ----

    @Test
    fun `a local change made after the backend was read is not overwritten`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        backend.seed(anna, alice, 2, UUID.randomUUID(), RemotePersonFields("Anna (remote)", null, null, null, null))
        backend.afterFetch = { table -> if (table == "people") people.updatePerson(anna, "Anna (local)", null) }

        assertEquals(applied(skippedQueued = 1), puller.pull(alicesAccount))
        assertEquals("only the edit queued", SyncOperation.UPDATE, pending(anna).operation)

        val local = personDao.findById(anna, alice)!!
        assertEquals("Anna (local)", local.name)
        assertEquals(1L, local.version)
    }

    @Test
    fun `a push settled after the backend was read leaves Room ahead, and the older row is skipped`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna K.", null)
        // The backend is read at version 1, then the queued change is pushed and settled.
        backend.afterFetch = { table -> if (table == "people") newPusher().push(alicesAccount, pending(anna).id) }

        assertEquals(applied(skippedStale = 1), puller.pull(alicesAccount))

        val local = personDao.findById(anna, alice)!!
        assertEquals(2L, local.version)
        assertEquals("Anna K.", local.name)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna))
    }

    @Test
    fun `a push in flight during the pull keeps its entity, others are applied, and it still settles`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        people.updatePerson(anna, "Anna K.", null)
        val bela = remotePerson("Béla")
        val hold = FakeBackend.Behavior.Hold().also { backend.script += it }
        val push = async { newPusher().push(alicesAccount, pending(anna).id) }
        hold.arrived.await()

        assertEquals(applied(inserted = 1, skippedQueued = 1), pull())

        hold.release.complete(Unit)
        assertEquals(PushOutcome.Completed, push.await())
        assertEquals(2L, personDao.findById(anna, alice)!!.version)
        assertNotNull(personDao.findById(bela, alice))
    }

    @Test
    fun `a switch of account after the backend was read applies nothing`() = runTest {
        listOf(bob, null).forEach { next ->
            val anna = remotePerson("Anna")
            activeOwner.ownerId.value = alice
            backend.afterFetch = { table -> if (table == "people") activeOwner.ownerId.value = next }

            assertEquals(PullResult.OwnerChanged, pull())

            assertNull(personDao.findById(anna, alice))
            backend.rows.remove(anna)
        }
    }

    @Test
    fun `the backend is read with children first, so rows added between reads still arrive with their parents`() = runTest {
        // Right after the first table is read, whichever it is, another device creates a person
        // and then an idea for them, the only order the backend accepts. Read children first, the
        // idea's table comes later and its person's table later still, so both arrive. Read
        // parents first, the people would already have been read without Cecil, and the idea
        // would arrive pointing at nobody.
        var cecil: UUID? = null
        var idea: UUID? = null
        backend.afterFetch = {
            if (backend.fetches.size == 1) {
                cecil = remotePerson("Cecil")
                idea = remoteIdea(cecil!!)
            }
        }

        assertEquals(applied(inserted = 2), pull())

        assertNotNull(personDao.findById(cecil!!, alice))
        assertEquals(cecil, database.giftIdeaDao().findById(idea!!, alice)!!.recipientPersonId)
    }

    // ---- failures ----

    @Test
    fun `a read failing after others succeeded applies nothing`() = runTest {
        val anna = remotePerson("Anna")
        remoteIdea(anna)
        backend.beforeFetch = { table -> if (table == "occasions") throw RemoteException(RemoteFailure.NETWORK) }

        assertEquals(PullResult.Failed(RemoteFailure.NETWORK), pull())

        assertEquals(listOf("gifts", "gift_ideas"), backend.fetches)
        assertNull(personDao.findById(anna, alice))
    }

    @Test
    fun `every remote failure is reported as it is`() = runTest {
        RemoteFailure.entries.forEach { failure ->
            backend.beforeFetch = { throw RemoteException(failure) }
            assertEquals(PullResult.Failed(failure), pull())
        }
    }

    @Test
    fun `cancelling during the reads applies nothing and is not reported as a failure`() = runTest {
        val anna = remotePerson("Anna")
        val reading = CompletableDeferred<Unit>()
        backend.beforeFetch = { table ->
            if (table == "occasions") {
                reading.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        val pull = async { puller.pull(alicesAccount) }
        reading.await()

        pull.cancel()
        pull.join()

        assertTrue(pull.isCancelled)
        assertTrue(pull.getCompletionExceptionOrNull() is CancellationException)
        assertNull(personDao.findById(anna, alice))
    }

    @Test
    fun `a row referring to something Room will not have fails the pull before anything is written`() = runTest {
        val anna = remotePerson("Anna")
        // Only bypassing the backend's foreign keys can produce this. Room's are deferred, so
        // writing it would fail only at commit, which would leave the transaction open.
        remoteGift(recipient = UUID.randomUUID())

        assertNothingApplied(anna)

        // The database is still usable: no transaction was left open.
        val bela = people.addPerson("Béla", null)
        assertNotNull(personDao.findById(bela.id, alice))
    }

    @Test
    fun `a write failing part-way rolls the whole pull back`() = runTest {
        val anna = remotePerson("Anna")
        remoteGift(recipient = anna)
        val failingGifts = object : GiftDao by database.giftDao() {
            override suspend fun insert(gift: GiftEntity) = throw IllegalStateException("disk full")
        }
        val puller = SyncPuller(
            database, backend, operations, personDao, database.occasionDao(), database.giftIdeaDao(), failingGifts, activeOwner,
        )

        val failure = runCatching { puller.pull(alicesAccount) }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
        assertNull("Anna was written before the gift and rolled back with it", personDao.findById(anna, alice))
    }

    @Test
    fun `a local row of another account with a pulled id fails the pull instead of being overwritten`() = runTest {
        val shared = UUID.randomUUID()
        val bobsRow = PersonEntity(shared, bob, "Bob's Anna", null, null, null, 1, RemoteState.SYNCED, clock.instant, clock.instant, null)
        personDao.insert(bobsRow)
        backend.seed(shared, alice, 1, UUID.randomUUID(), RemotePersonFields("Alice's Anna", null, null, null, null))
        val other = remotePerson("Béla")

        val failure = runCatching { puller.pull(alicesAccount) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalStateException && "another owner" in failure.message!!)
        assertEquals(bobsRow, personDao.findByIdForAnyOwner(shared))
        assertNull(personDao.findById(other, alice))
    }

    // ---- repeating ----

    @Test
    fun `pulling the same backend again changes nothing`() = runTest {
        val anna = remotePerson("Anna")
        val birthday = remoteOccasion(person = anna)
        val idea = remoteIdea(anna, occasion = birthday)
        val gift = remoteGift(recipient = anna, sourceIdea = idea)
        assertEquals(applied(inserted = 4), pull())
        val rows = listOf(
            personDao.findById(anna, alice), database.occasionDao().findById(birthday, alice),
            database.giftIdeaDao().findById(idea, alice), database.giftDao().findById(gift, alice),
        )

        assertEquals(applied(unchanged = 4), pull())

        assertEquals(
            rows,
            listOf(
                personDao.findById(anna, alice), database.occasionDao().findById(birthday, alice),
                database.giftIdeaDao().findById(idea, alice), database.giftDao().findById(gift, alice),
            ),
        )
    }

    // ---- fixtures ----

    /** Pulls for Alice and checks that the pull queued nothing and changed no queued operation. */
    private suspend fun pull(): PullResult {
        val queue = allQueued()
        return puller.pull(alicesAccount).also { assertEquals("a pull changed the queue", queue, allQueued()) }
    }

    private suspend fun allQueued() = (listOf(alice, bob).flatMap { operations.getAllForOwner(it) }).sortedBy { it.id }

    private suspend fun assertNothingApplied(anna: UUID) {
        assertEquals(PullResult.Failed(RemoteFailure.SERVER), pull())
        assertNull(personDao.findById(anna, alice))
    }

    private fun applied(
        inserted: Int = 0, updated: Int = 0, unchanged: Int = 0, skippedQueued: Int = 0, skippedStale: Int = 0,
        skippedLocalOnly: Int = 0,
    ) = PullResult.Applied(inserted, updated, unchanged, skippedQueued, skippedStale, skippedLocalOnly)

    private suspend fun pending(id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(SyncEntityType.PERSON, id)) { "Nothing pending" }

    private fun remotePerson(name: String, version: Long = 1, deletedAt: Instant? = null): UUID = UUID.randomUUID().also {
        backend.seed(it, alice, version, UUID.randomUUID(), RemotePersonFields(name, null, null, null, deletedAt))
    }

    private fun remoteOccasion(person: UUID?, day: Int = 12, month: Int = 5): UUID = UUID.randomUUID().also {
        backend.seed(it, alice, 1, UUID.randomUUID(), occasionFields(person, day, month))
    }

    private fun remoteIdea(recipient: UUID, occasion: UUID? = null, archivedAt: Instant? = null): UUID = UUID.randomUUID().also {
        backend.seed(
            it, alice, 1, UUID.randomUUID(),
            RemoteGiftIdeaFields(recipient, occasion, "Scarf", GiftIdeaStatus.IDEA, null, null, null, null, null, null, null, archivedAt, null),
        )
    }

    private fun remoteGift(recipient: UUID, occasion: UUID? = null, sourceIdea: UUID? = null): UUID = UUID.randomUUID().also {
        backend.seed(it, alice, 1, UUID.randomUUID(), giftFields(recipient).copy(occasionId = occasion, sourceGiftIdeaId = sourceIdea))
    }

    private fun occasionFields(person: UUID?, day: Int = 12, month: Int = 5) =
        RemoteOccasionFields(person, OccasionType.BIRTHDAY, null, day, month, null, true, null)

    private fun giftFields(recipient: UUID) = RemoteGiftFields(
        GiftDirection.GIVEN, null, recipient, null, null, "Scarf", LocalDate.of(2026, 5, 12), null, null, null, null, null, null,
    )

    private fun giftFor(person: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = person, occasionId = null, name = "Scarf",
        givenAt = LocalDate.of(2026, 5, 12), price = null, productUrl = null, note = null,
    )

    /** A person Room and the backend both have at [version], with nothing queued. */
    private suspend fun syncedPerson(name: String, version: Long): UUID {
        val person = people.addPerson(name, null).also { clock.advanceBy(Duration.ofSeconds(1)) }
        val entity = personDao.findById(person.id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)
        personDao.update(entity)
        operations.delete(pending(person.id))
        backend.seed(person.id, alice, version, UUID.randomUUID(), entity.toRemoteFields())
        return person.id
    }

    /** An occasion of [person] Room and the backend both have at version 1, with nothing queued. */
    private suspend fun syncedOccasion(person: UUID): UUID {
        val id = UUID.randomUUID()
        val entity = app.gifty.data.local.entity.OccasionEntity(
            id, alice, person, OccasionType.BIRTHDAY, null, 12, 5, null, true, 1, RemoteState.SYNCED, clock.instant, clock.instant, null,
        )
        database.occasionDao().insert(entity)
        backend.seed(id, alice, 1, UUID.randomUUID(), entity.toRemoteFields())
        return id
    }

    private fun newPusher() = SyncPusher(
        database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
        database.giftDao(),
        SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(),
        ),
        backend, backend, clock,
    )

    private companion object {
        // FakeBackend's created_at and updated_at for every row.
        val REMOTE_TIME: Instant = Instant.parse("2026-10-10T12:00:00Z")
    }
}
