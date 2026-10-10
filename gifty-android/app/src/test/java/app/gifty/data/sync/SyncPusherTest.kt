package app.gifty.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.SyncReconciler
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.toRemoteFields
import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.sync.FakeBackend.Behavior
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.GiftDetails
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Pushing one operation, with real Room, repositories and queue, against [FakeBackend]. Each test
 * checks the queue and the entity afterwards, not just the returned outcome.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPusherTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-10T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(alice)
    private lateinit var database: GiftyDatabase
    private lateinit var backend: FakeBackend
    private lateinit var pusher: SyncPusher
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl

    private val operations get() = database.syncOperationDao()
    private val alicesAccount = RemoteAccount.capture(alice, token(alice))
    private val bobsAccount = RemoteAccount.capture(bob, token(bob))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, activeOwner, clock)
        occasions = OccasionRepositoryImpl(database, database.occasionDao(), database.personDao(), queue, activeOwner, clock)
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue,
            activeOwner, clock,
        )
        gifts = GiftRepositoryImpl(database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock)
        backend = FakeBackend(database)
        val reconciler = SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(),
        )
        pusher = SyncPusher(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(), reconciler, backend, backend, clock,
        )
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- starting ----

    @Test
    fun `the mutation is in flight and its attempt counted before the request is made`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        val hold = Behavior.Hold()
        backend.script += hold

        val push = async { pusher.push(alicesAccount, create.id) }
        hold.arrived.await()
        val during = pending(SyncEntityType.PERSON, anna.id)
        hold.release.complete(Unit)

        assertEquals(create.mutationId, during.inFlightMutationId)
        assertEquals(create.mutationId, during.mutationId)
        assertEquals(1, during.attemptCount)
        assertEquals(clock.instant, during.lastAttemptAt)
        assertEquals(PushOutcome.Completed, push.await())
    }

    @Test
    fun `a blocked mutation is never sent`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        operations.blockCurrentMutation(create.id, create.mutationId, BlockReason.REJECTED.name)

        assertEquals(PushOutcome.Blocked(BlockReason.REJECTED), pusher.push(alicesAccount, create.id))

        assertTrue(backend.writes.isEmpty())
        assertNull(pending(SyncEntityType.PERSON, anna.id).inFlightMutationId)
    }

    @Test
    fun `an operation of another account, or one that is gone, is left alone`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)

        assertEquals(PushOutcome.Stale, pusher.push(bobsAccount, create.id))
        assertEquals(PushOutcome.Stale, pusher.push(alicesAccount, UUID.randomUUID()))

        assertTrue(backend.writes.isEmpty())
        assertEquals(create, pending(SyncEntityType.PERSON, anna.id))
    }

    // ---- committed ----

    @Test
    fun `a create of each entity is inserted with its mutation id and completes at version 1`() = runTest {
        val created = createOneOfEach()

        created.forEach { (type, id) ->
            val create = pending(type, id)

            assertEquals("$type", PushOutcome.Completed, pusher.push(alicesAccount, create.id))

            val write = backend.writes.last()
            assertTrue(write.insert)
            assertEquals(id, write.entityId)
            assertEquals(create.mutationId, write.mutationId)
            assertEquals(alice, write.owner)
            assertNull(operations.findForEntity(type, id))
            assertEquals(1L to RemoteState.SYNCED, versionOf(type, id))
        }
        assertEquals(4, backend.writes.size)
    }

    @Test
    fun `an update is sent against the entity's version and completes at the next one`() = runTest {
        val anna = syncedPerson(version = 3)
        people.updatePerson(anna, "Anna Kovács", "Sister")
        val update = pending(SyncEntityType.PERSON, anna)

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, update.id))

        val write = backend.writes.single()
        assertFalse(write.insert)
        assertEquals(3L, write.expectedVersion)
        assertEquals("Anna Kovács", (write.fields as RemotePersonFields).name)
        assertEquals(4L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna))
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna))
    }

    @Test
    fun `a deletion is sent as an update carrying the tombstone`() = runTest {
        val anna = syncedPerson(version = 2)
        people.deletePerson(anna)
        val delete = pending(SyncEntityType.PERSON, anna)
        assertEquals(SyncOperation.DELETE, delete.operation)

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, delete.id))

        val write = backend.writes.single()
        assertFalse(write.insert)
        val deletedAt = database.personDao().findById(anna, alice)!!.deletedAt
        assertNotNull(deletedAt)
        assertEquals(deletedAt, (write.fields as RemotePersonFields).deletedAt)
        assertEquals(3L, backend.rows.getValue(anna).version)
        assertNotNull(database.personDao().findById(anna, alice))
    }

    // ---- a local change during the request ----

    @Test
    fun `an edit during an update leaves the edit queued, unsent, after the update commits`() = runTest {
        val anna = syncedPerson(version = 3)
        people.updatePerson(anna, "Anna Kovács", "Sister")
        val update = pending(SyncEntityType.PERSON, anna)
        val hold = Behavior.Hold()
        backend.script += hold

        val push = async { pusher.push(alicesAccount, update.id) }
        hold.arrived.await()
        people.updatePerson(anna, "Anna K.", "Big sister")
        val edit = pending(SyncEntityType.PERSON, anna)
        hold.release.complete(Unit)

        assertEquals(PushOutcome.NewerChangePending, push.await())
        assertEquals(1, backend.writes.size)
        val after = pending(SyncEntityType.PERSON, anna)
        assertEquals(edit.mutationId, after.mutationId)
        assertNotEquals(update.mutationId, after.mutationId)
        assertNull(after.inFlightMutationId)
        assertEquals(SyncOperation.UPDATE, after.operation)
        assertEquals(4L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna))
        assertEquals("Anna K.", people.getPerson(anna)?.name)
    }

    @Test
    fun `an edit during a create leaves an update queued once the create commits`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        val hold = Behavior.Hold()
        backend.script += hold

        val push = async { pusher.push(alicesAccount, create.id) }
        hold.arrived.await()
        people.updatePerson(anna.id, "Anna K.", null)
        val edit = pending(SyncEntityType.PERSON, anna.id)
        hold.release.complete(Unit)

        assertEquals(PushOutcome.NewerChangePending, push.await())
        val after = pending(SyncEntityType.PERSON, anna.id)
        assertEquals(edit.copy(operation = SyncOperation.UPDATE, inFlightMutationId = null), after)
        assertEquals(1L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))
        assertEquals("Anna K.", people.getPerson(anna.id)?.name)
        assertEquals(1, backend.writes.size)
    }

    @Test
    fun `a gift deleted during its create keeps the deletion queued once the create commits`() = runTest {
        val anna = syncedPerson(version = 1)
        val gift = gifts.addGift(giftFor(anna))
        val create = pending(SyncEntityType.GIFT, gift.id)
        val hold = Behavior.Hold()
        backend.script += hold

        val push = async { pusher.push(alicesAccount, create.id) }
        hold.arrived.await()
        gifts.deleteGift(gift.id)
        val deletion = pending(SyncEntityType.GIFT, gift.id)
        hold.release.complete(Unit)

        assertEquals(PushOutcome.NewerChangePending, push.await())
        val after = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(deletion.copy(operation = SyncOperation.DELETE, inFlightMutationId = null), after)
        val entity = database.giftDao().findById(gift.id, alice)!!
        assertNotNull(entity.deletedAt)
        assertEquals(1L, entity.version)
    }

    // ---- no answer ----

    @Test
    fun `a write whose answer was lost is found in the ledger and not sent again`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += Behavior.LoseResponse

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, create.id))

        assertEquals(1, backend.writes.size)
        assertEquals(1L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a write that never arrived is sent once more with the same mutation id`() = runTest {
        val anna = syncedPerson(version = 3)
        people.updatePerson(anna, "Anna Kovács", "Sister")
        val update = pending(SyncEntityType.PERSON, anna)
        backend.script += Behavior.LoseRequest

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, update.id))

        assertEquals(2, backend.writes.size)
        backend.writes.forEach {
            assertEquals(update.mutationId, it.mutationId)
            assertEquals(3L, it.expectedVersion)
        }
        assertEquals(4L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna))
    }

    @Test
    fun `a write that committed late is recognised when its resend is refused`() = runTest {
        // Create: the resend finds the id taken, by the same mutation.
        val anna = people.addPerson("Anna", null)
        backend.script += Behavior.CommitLater
        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, pending(SyncEntityType.PERSON, anna.id).id))
        assertEquals(1L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))

        // Update: the resend no longer matches the version, which the first copy moved on.
        people.updatePerson(anna.id, "Anna K.", null)
        backend.script += Behavior.CommitLater
        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, pending(SyncEntityType.PERSON, anna.id).id))
        assertEquals(2L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))
        assertEquals(4, backend.writes.size)
    }

    @Test
    fun `without an answer twice the mutation stays in flight, unblocked, for a later push`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)

        assertEquals(PushOutcome.Unresolved(RemoteFailure.NETWORK), pusher.push(alicesAccount, create.id))

        val after = pending(SyncEntityType.PERSON, anna.id)
        assertEquals(create.mutationId, after.inFlightMutationId)
        assertEquals(create.mutationId, after.mutationId)
        assertNull(after.blockedReason)
        assertEquals(2, after.attemptCount)
        assertEquals(RemoteFailure.NETWORK.name, after.lastErrorCode)
        assertEquals(listOf(create.mutationId, create.mutationId), backend.writes.map { it.mutationId })
        assertEquals(0L to RemoteState.LOCAL_ONLY, versionOf(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a later push first looks for the mutation it left in flight`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        assertEquals(PushOutcome.Unresolved(RemoteFailure.NETWORK), pusher.push(alicesAccount, create.id))
        // One of the lost sends did reach the database after all.
        backend.recordCommit(anna.id, alice, version = 1, mutationId = create.mutationId, fields = personFields("Anna"))

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, create.id))

        assertEquals(2, backend.writes.size)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertEquals(1L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a later push sends a mutation left in flight once more if it never arrived`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest, Behavior.LoseRequest)
        pusher.push(alicesAccount, create.id)

        assertEquals(PushOutcome.Unresolved(RemoteFailure.NETWORK), pusher.push(alicesAccount, create.id))
        assertEquals(3, backend.writes.size)

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, create.id))
        assertEquals(4, backend.writes.size)
        assertTrue(backend.writes.all { it.mutationId == create.mutationId })
    }

    @Test
    fun `a resend after an edit carries the newer content under the same mutation, and the edit stays queued`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        pusher.push(alicesAccount, create.id)
        people.updatePerson(anna.id, "Anna K.", null)
        val edit = pending(SyncEntityType.PERSON, anna.id)

        assertEquals(PushOutcome.NewerChangePending, pusher.push(alicesAccount, create.id))

        val resend = backend.writes.last()
        assertEquals(create.mutationId, resend.mutationId)
        assertTrue(resend.insert)
        assertEquals("Anna K.", (resend.fields as RemotePersonFields).name)
        assertEquals(edit.copy(operation = SyncOperation.UPDATE, inFlightMutationId = null, attemptCount = 0, lastAttemptAt = null), pending(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `when even the evidence cannot be read, the mutation stays in flight`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += Behavior.LoseResponse
        backend.readFailure = RemoteFailure.NETWORK

        assertEquals(PushOutcome.Unresolved(RemoteFailure.NETWORK), pusher.push(alicesAccount, create.id))

        assertEquals(create.mutationId, pending(SyncEntityType.PERSON, anna.id).inFlightMutationId)
        assertEquals(1, backend.writes.size)
    }

    // ---- duplicates ----

    @Test
    fun `a duplicate whose row carries the mutation is the mutation's own commit`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        // The ledger lookup raced the commit; the row already shows it.
        backend.seed(anna.id, alice, version = 1, lastMutationId = create.mutationId, fields = personFields("Anna"))
        backend.script += Behavior.Fail(RemoteFailure.DUPLICATE)

        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, create.id))
        assertEquals(1L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a create refused for another unique key is blocked, not taken as committed`() = runTest {
        val anna = syncedPerson(version = 1)
        val gift = gifts.addGift(giftFor(anna))
        val create = pending(SyncEntityType.GIFT, gift.id)
        // E.g. the gift's source idea is already used by another gift.
        backend.script += Behavior.Fail(RemoteFailure.DUPLICATE)

        assertEquals(PushOutcome.Blocked(BlockReason.REJECTED), pusher.push(alicesAccount, create.id))

        val after = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(create.mutationId, after.mutationId)
        assertNull(after.inFlightMutationId)
        assertEquals(BlockReason.REJECTED.name, after.blockedReason)
        assertEquals(0L to RemoteState.LOCAL_ONLY, versionOf(SyncEntityType.GIFT, gift.id))
        assertEquals(1, backend.writes.size)
    }

    @Test
    fun `a create whose id was taken by another mutation is a conflict`() = runTest {
        val anna = people.addPerson("Anna", null)
        backend.seed(anna.id, alice, version = 1, lastMutationId = UUID.randomUUID(), fields = personFields("Someone"))

        assertEquals(
            PushOutcome.Blocked(BlockReason.REMOTE_ID_TAKEN),
            pusher.push(alicesAccount, pending(SyncEntityType.PERSON, anna.id).id),
        )
        assertEquals("Anna", people.getPerson(anna.id)?.name)
    }

    // ---- no match ----

    @Test
    fun `an update that matched nothing because the row moved on is a conflict, and nothing local is overwritten`() = runTest {
        val anna = syncedPerson(version = 3)
        people.updatePerson(anna, "Anna Kovács", "Sister")
        val update = pending(SyncEntityType.PERSON, anna)
        // Another device updated Anna meanwhile.
        backend.rows.getValue(anna).apply {
            version = 5
            lastMutationId = UUID.randomUUID()
        }

        assertEquals(PushOutcome.Blocked(BlockReason.REMOTE_NEWER), pusher.push(alicesAccount, update.id))

        assertEquals(3L to RemoteState.SYNCED, versionOf(SyncEntityType.PERSON, anna))
        assertEquals("Anna Kovács", people.getPerson(anna)?.name)
        val after = pending(SyncEntityType.PERSON, anna)
        assertEquals(BlockReason.REMOTE_NEWER.name, after.blockedReason)
        assertNull(after.inFlightMutationId)
    }

    @Test
    fun `an update of a row the backend no longer shows is a conflict`() = runTest {
        val anna = syncedPerson(version = 3)
        people.updatePerson(anna, "Anna Kovács", "Sister")
        backend.rows.remove(anna)

        assertEquals(
            PushOutcome.Blocked(BlockReason.REMOTE_MISSING),
            pusher.push(alicesAccount, pending(SyncEntityType.PERSON, anna).id),
        )
    }

    // ---- other failures ----

    @Test
    fun `refused values block the mutation`() = runTest {
        val anna = people.addPerson("Anna", null)
        backend.script += Behavior.Fail(RemoteFailure.CONSTRAINT_VIOLATION)

        assertEquals(
            PushOutcome.Blocked(BlockReason.REJECTED),
            pusher.push(alicesAccount, pending(SyncEntityType.PERSON, anna.id).id),
        )
        assertEquals(1, backend.writes.size)
    }

    @Test
    fun `server, permission and session failures leave the mutation in flight and unblocked`() = runTest {
        mapOf(
            RemoteFailure.SERVER to PushOutcome.Unresolved(RemoteFailure.SERVER),
            RemoteFailure.FORBIDDEN to PushOutcome.Unresolved(RemoteFailure.FORBIDDEN),
            RemoteFailure.UNAUTHENTICATED to PushOutcome.SessionRequired(RemoteFailure.UNAUTHENTICATED),
            RemoteFailure.ACCOUNT_MISMATCH to PushOutcome.SessionRequired(RemoteFailure.ACCOUNT_MISMATCH),
        ).forEach { (failure, expected) ->
            val person = people.addPerson("Person ${failure.name}", null)
            val create = pending(SyncEntityType.PERSON, person.id)
            backend.script += Behavior.Fail(failure)

            assertEquals("$failure", expected, pusher.push(alicesAccount, create.id))

            val after = pending(SyncEntityType.PERSON, person.id)
            assertEquals(create.mutationId, after.inFlightMutationId)
            assertNull(after.blockedReason)
            assertEquals(failure.name, after.lastErrorCode)
        }
    }

    @Test
    fun `a cancelled push leaves the mutation in flight, and the next push settles it`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        val hold = Behavior.Hold(then = Behavior.LoseRequest)
        backend.script += hold

        val push = async { pusher.push(alicesAccount, create.id) }
        hold.arrived.await()
        push.cancel()
        val failure = runCatching { push.await() }.exceptionOrNull()

        assertTrue("got $failure", failure is CancellationException)
        assertEquals(create.mutationId, pending(SyncEntityType.PERSON, anna.id).inFlightMutationId)
        assertEquals(PushOutcome.Completed, pusher.push(alicesAccount, create.id))
        assertTrue(backend.writes.all { it.mutationId == create.mutationId })
    }

    // ---- races ----

    @Test
    fun `a push started for one account stays that account's while another account pushes`() = runTest {
        val annasCreate = pending(SyncEntityType.PERSON, people.addPerson("Anna", null).id)
        activeOwner.ownerId.value = bob
        val bela = people.addPerson("Béla", null)
        val belasCreate = pending(SyncEntityType.PERSON, bela.id)
        val hold = Behavior.Hold()
        backend.script += hold

        val alicesPush = async { pusher.push(alicesAccount, annasCreate.id) }
        hold.arrived.await()
        assertEquals(PushOutcome.Completed, pusher.push(bobsAccount, belasCreate.id))
        hold.release.complete(Unit)

        assertEquals(PushOutcome.Completed, alicesPush.await())
        val (alicesWrite, bobsWrite) = backend.writes.partition { it.entityId == annasCreate.entityId }
        assertEquals(token(alice), alicesWrite.single().token)
        assertEquals(alice, alicesWrite.single().owner)
        assertEquals(token(bob), bobsWrite.single().token)
        assertEquals(alice, database.personDao().findById(annasCreate.entityId, alice)?.ownerUserId)
        assertNull(database.personDao().findById(annasCreate.entityId, bob))
    }

    @Test
    fun `a late refusal of a mutation never blocks the newer change`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        val hold = Behavior.Hold(then = Behavior.Fail(RemoteFailure.CONSTRAINT_VIOLATION))
        backend.script += hold

        val push = async { pusher.push(alicesAccount, create.id) }
        hold.arrived.await()
        people.updatePerson(anna.id, "Anna K.", null)
        val edit = pending(SyncEntityType.PERSON, anna.id)
        hold.release.complete(Unit)

        assertEquals(PushOutcome.NewerChangePending, push.await())
        assertEquals(
            edit.copy(inFlightMutationId = null, attemptCount = 0, lastAttemptAt = null, lastErrorCode = null),
            pending(SyncEntityType.PERSON, anna.id),
        )
        assertNull(pending(SyncEntityType.PERSON, anna.id).blockedReason)
    }

    @Test
    fun `a newer change is never sent while an older mutation is unresolved`() = runTest {
        val anna = people.addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        pusher.push(alicesAccount, create.id)
        people.updatePerson(anna.id, "Anna K.", null)
        val edit = pending(SyncEntityType.PERSON, anna.id)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        pusher.push(alicesAccount, create.id)

        assertTrue(backend.writes.none { it.mutationId == edit.mutationId })
        assertEquals(create.mutationId, pending(SyncEntityType.PERSON, anna.id).inFlightMutationId)
    }

    // ---- fixtures ----

    private suspend fun pending(type: SyncEntityType, id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(type, id)) { "No pending $type operation" }

    private suspend fun versionOf(type: SyncEntityType, id: UUID): Pair<Long, RemoteState> = when (type) {
        SyncEntityType.PERSON -> database.personDao().findById(id, alice)!!.let { it.version to it.remoteState }
        SyncEntityType.OCCASION -> database.occasionDao().findById(id, alice)!!.let { it.version to it.remoteState }
        SyncEntityType.GIFT_IDEA -> database.giftIdeaDao().findById(id, alice)!!.let { it.version to it.remoteState }
        SyncEntityType.GIFT -> database.giftDao().findById(id, alice)!!.let { it.version to it.remoteState }
        else -> error("Not synced: $type")
    }

    /** A person the backend already has at [version], with nothing pending locally. */
    private suspend fun syncedPerson(version: Long): UUID {
        val person = people.addPerson("Anna", "Sister")
        val dao = database.personDao()
        val entity = dao.findById(person.id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)
        dao.update(entity)
        operations.findForEntity(SyncEntityType.PERSON, person.id)?.let { operations.delete(it) }
        backend.seed(person.id, alice, version, UUID.randomUUID(), entity.toRemoteFields())
        return person.id
    }

    private fun personFields(name: String) = RemotePersonFields(name, null, null, null, null)

    private fun giftFor(personId: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = personId, occasionId = null,
        name = "Scarf", givenAt = LocalDate.of(2026, 3, 12), price = Money(9900, "HUF"), productUrl = null, note = null,
    )

    private suspend fun createOneOfEach(): List<Pair<SyncEntityType, UUID>> {
        val anna = people.addPerson("Anna", null)
        val occasion = occasions.addOccasion(
            anna.id, OccasionDetails(OccasionType.BIRTHDAY, title = null, day = 12, month = 3, year = null, repeatsYearly = true),
        )
        val idea = ideas.addGiftIdea(
            GiftIdeaDetails(anna.id, occasionId = null, name = "Book", status = GiftIdeaStatus.IDEA, estimatedPrice = null,
                actualPrice = null, productUrl = null, note = null),
        )
        val gift = gifts.addGift(giftFor(anna.id))
        return listOf(
            SyncEntityType.PERSON to anna.id,
            SyncEntityType.OCCASION to occasion.id,
            SyncEntityType.GIFT_IDEA to idea.id,
            SyncEntityType.GIFT to gift.id,
        )
    }
}
