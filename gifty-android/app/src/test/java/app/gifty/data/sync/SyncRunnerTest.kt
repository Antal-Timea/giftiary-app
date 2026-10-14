package app.gifty.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.SyncReconciler
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncConflictEntity
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.toRemoteFields
import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.sync.FakeBackend.Behavior
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Running an account's queue, with real Room, repositories and [SyncPusher], against
 * [FakeBackend]. Requests are counted per entity, so a test sees exactly what was sent.
 */
@RunWith(RobolectricTestRunner::class)
class SyncRunnerTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-11T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(alice)
    private val alicesAccount = RemoteAccount.capture(alice, token(alice))
    private lateinit var database: GiftyDatabase
    private lateinit var backend: FakeBackend
    private lateinit var runner: SyncRunner
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {})
        occasions = OccasionRepositoryImpl(
            database, database.occasionDao(), database.personDao(), queue, activeOwner, clock, SyncRequester {},
        )
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue,
            activeOwner, clock,
            SyncRequester {},
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {},
        )
        backend = FakeBackend(database)
        runner = newRunner()
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- dependencies ----

    @Test
    fun `a person and an idea for them sync in one run, the person first`() = runTest {
        val anna = addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        val result = runner.run(alicesAccount)

        assertEquals(listOf(anna.id, idea.id), backend.writes.map { it.entityId })
        assertEquals(SyncRunResult(pushed = 2, settled = 2, conflictsSettled = 0, stoppedBecause = null, workRemains = false, needsAttention = false), result)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
    }

    @Test
    fun `a whole chain of person, occasion, idea and the gift it became syncs in order in one run`() = runTest {
        val anna = addPerson("Anna", null)
        val birthday = occasions.addOccasion(anna.id, birthdayOn(12, 3))
        val idea = ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = birthday.id))
        // Created before its parents' ids are even remote; the queue holds the gift last anyway.
        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 3, 12), null)

        val result = runner.run(alicesAccount)

        assertEquals(listOf(anna.id, birthday.id, idea.id, gift.id), backend.writes.map { it.entityId })
        assertEquals(4, result.settled)
        assertFalse(result.workRemains)
    }

    @Test
    fun `a child of a local-only parent whose create did not go through is not sent`() = runTest {
        val anna = addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        val result = runner.run(alicesAccount)

        assertEquals(listOf(anna.id), backend.writes.map { it.entityId })
        assertEquals(1, result.pushed)
        assertTrue(result.workRemains)
        assertNull(pending(SyncEntityType.GIFT_IDEA, idea.id).inFlightMutationId)
    }

    @Test
    fun `a child waits while its parent's create is in flight and unresolved`() = runTest {
        val anna = addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)

        val result = runner.run(alicesAccount)

        assertEquals("only the parent's two sends", listOf(anna.id, anna.id), backend.writes.map { it.entityId })
        assertEquals(1, result.pushed)
        assertEquals(RemoteState.LOCAL_ONLY, remoteStateOf(SyncEntityType.PERSON, anna.id))
        assertFalse(result.needsAttention)
        assertTrue(backend.writes.none { it.entityId == idea.id })
    }

    @Test
    fun `a synced parent with a change of its own pending does not hold back its child`() = runTest {
        val anna = syncedPerson("Anna")
        people.updatePerson(anna, "Anna K.", null)
        val idea = ideas.addGiftIdea(ideaFor(anna))
        // The parent's own update does not go through this time.
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        val result = runner.run(alicesAccount)

        assertEquals(listOf(anna, idea.id), backend.writes.map { it.entityId })
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
        assertEquals(1, result.settled)
    }

    @Test
    fun `a deleted synced parent is still a valid parent`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = ideas.addGiftIdea(ideaFor(anna))
        people.deletePerson(anna)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        runner.run(alicesAccount)

        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
    }

    @Test
    fun `a blocked local-only parent holds back its child without a single request for it`() = runTest {
        val anna = addPerson("Anna", null)
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        val create = pending(SyncEntityType.PERSON, anna.id)
        operations.blockCurrentMutation(create.id, create.mutationId, BlockReason.REJECTED.name)

        val result = runner.run(alicesAccount)

        assertTrue(backend.writes.isEmpty())
        assertEquals(0, result.pushed)
        assertTrue(result.needsAttention)
        assertTrue(result.workRemains)
        assertNull(pending(SyncEntityType.GIFT_IDEA, idea.id).inFlightMutationId)
    }

    @Test
    fun `a blocked synced parent still exists remotely and does not hold back its child`() = runTest {
        val anna = syncedPerson("Anna")
        people.updatePerson(anna, "Anna K.", null)
        val update = pending(SyncEntityType.PERSON, anna)
        operations.blockCurrentMutation(update.id, update.mutationId, BlockReason.REJECTED.name)
        val idea = ideas.addGiftIdea(ideaFor(anna))

        val result = runner.run(alicesAccount)

        assertEquals(listOf(idea.id), backend.writes.map { it.entityId })
        assertTrue(result.needsAttention)
    }

    @Test
    fun `an absent optional reference is no dependency`() = runTest {
        val bela = addPerson("Béla", null)
        val christmas = occasions.addOccasion(
            personId = null,
            details = OccasionDetails(OccasionType.CHRISTMAS, title = null, day = 24, month = 12, year = null, repeatsYearly = true),
        )
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        runner.run(alicesAccount)

        assertEquals(listOf(bela.id, christmas.id), backend.writes.map { it.entityId })
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.OCCASION, christmas.id))
    }

    @Test
    fun `whether a child may go is decided from the parent it references when its turn comes`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = ideas.addGiftIdea(ideaFor(anna))
        val bela = addPerson("Béla", null)
        val hold = Behavior.Hold(then = Behavior.Fail(RemoteFailure.SERVER))
        backend.script += hold

        val run = async { runner.run(alicesAccount) }
        hold.arrived.await()
        // While Béla's create is on its way, the idea is given to Béla instead of Anna.
        ideas.updateGiftIdea(idea.id, ideaFor(bela.id))
        hold.release.complete(Unit)
        run.await()

        assertTrue(backend.writes.none { it.entityId == idea.id })
        assertEquals(RemoteState.LOCAL_ONLY, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
    }

    @Test
    fun `a parent queued after its child still goes first, so the child syncs in the same run`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = ideas.addGiftIdea(ideaFor(anna))
        val ideaQueuedAt = pending(SyncEntityType.GIFT_IDEA, idea.id).createdAt
        clock.advanceBy(Duration.ofSeconds(1))
        val bela = addPerson("Béla", null)
        // The idea now belongs to Béla, whose create was queued after the idea's.
        ideas.updateGiftIdea(idea.id, ideaFor(bela.id))
        assertTrue(ideaQueuedAt < pending(SyncEntityType.PERSON, bela.id).createdAt)
        assertEquals(ideaQueuedAt, pending(SyncEntityType.GIFT_IDEA, idea.id).createdAt)

        val result = runner.run(alicesAccount)

        assertEquals(listOf(bela.id, idea.id), backend.writes.map { it.entityId })
        assertEquals(2, result.pushed)
        assertEquals(2, result.settled)
        assertFalse(result.workRemains)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
    }

    @Test
    fun `with only blocked work left, nothing remains to run but the user is needed`() = runTest {
        val anna = addPerson("Anna", null)
        val create = pending(SyncEntityType.PERSON, anna.id)
        operations.blockCurrentMutation(create.id, create.mutationId, BlockReason.REJECTED.name)

        val result = runner.run(alicesAccount)

        assertEquals(0, result.pushed)
        assertTrue(backend.writes.isEmpty())
        assertFalse(result.workRemains)
        assertTrue(result.needsAttention)
        assertEquals(BlockReason.REJECTED.name, pending(SyncEntityType.PERSON, anna.id).blockedReason)
    }

    @Test
    fun `wishlist and wish operations are outside sync and left alone`() = runTest {
        // Nothing in the app queues them yet; inserted directly to pin that the runner ignores them.
        listOf(SyncEntityType.WISHLIST, SyncEntityType.WISH).forEach { type ->
            operations.insert(
                SyncOperationEntity(
                    id = UUID.randomUUID(), ownerUserId = alice, entityType = type, entityId = UUID.randomUUID(),
                    operation = SyncOperation.CREATE, mutationId = UUID.randomUUID(), inFlightMutationId = null,
                    blockedReason = null, createdAt = clock.instant, attemptCount = 0, lastAttemptAt = null,
                    lastErrorCode = null,
                ),
            )
        }
        val anna = addPerson("Anna", null)

        val result = runner.run(alicesAccount)

        assertEquals("only the person", 1, result.pushed)
        assertEquals(listOf(anna.id), backend.writes.map { it.entityId })
        assertFalse(result.workRemains)
        assertFalse(result.needsAttention)
        assertEquals(2, operations.getAllForOwner(alice).count { it.entityType in setOf(SyncEntityType.WISHLIST, SyncEntityType.WISH) })
    }

    // ---- mark as given ----

    @Test
    fun `a gift of a synced idea goes ahead while the idea's archive update is still pending`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = syncedIdea(anna)
        val gift = ideas.markAsGiven(idea, LocalDate.of(2026, 3, 12), null)
        assertEquals(SyncOperation.UPDATE, pending(SyncEntityType.GIFT_IDEA, idea).operation)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        runner.run(alicesAccount)

        assertEquals(listOf(idea, gift.id), backend.writes.map { it.entityId })
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT, gift.id))
        assertEquals(RemoteFailure.SERVER.name, pending(SyncEntityType.GIFT_IDEA, idea).lastErrorCode)
    }

    @Test
    fun `a gift of a local-only idea goes only after the idea has reached the backend`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = ideas.addGiftIdea(ideaFor(anna))
        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 3, 12), null)
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.GIFT_IDEA, idea.id).operation)

        runner.run(alicesAccount)

        assertEquals(listOf(idea.id, gift.id), backend.writes.map { it.entityId })
        assertTrue(backend.writes.first().insert)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT, gift.id))
    }

    @Test
    fun `a gift waits while its idea's create is unresolved`() = runTest {
        val anna = syncedPerson("Anna")
        val idea = ideas.addGiftIdea(ideaFor(anna))
        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 3, 12), null)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)

        runner.run(alicesAccount)

        assertTrue(backend.writes.none { it.entityId == gift.id })
        assertEquals(RemoteState.LOCAL_ONLY, remoteStateOf(SyncEntityType.GIFT, gift.id))
    }

    // ---- fairness ----

    @Test
    fun `a person lost on the network holds back only its own children`() = runTest {
        assertOnlyOwnBranchHeldBack(listOf(Behavior.LoseRequest, Behavior.LoseRequest), RemoteFailure.NETWORK)
    }

    @Test
    fun `a person refused by a server error holds back only its own children`() = runTest {
        assertOnlyOwnBranchHeldBack(listOf(Behavior.Fail(RemoteFailure.SERVER)), RemoteFailure.SERVER)
    }

    private suspend fun assertOnlyOwnBranchHeldBack(failing: List<Behavior>, failure: RemoteFailure) {
        val p1 = addPerson("P1", null)
        val p1Idea = ideas.addGiftIdea(ideaFor(p1.id))
        val p2 = addPerson("P2", null)
        val g2 = ideas.addGiftIdea(ideaFor(p2.id))
        backend.script += failing

        val result = runner.run(alicesAccount)

        assertNull(result.stoppedBecause)
        assertEquals(failure.name, pending(SyncEntityType.PERSON, p1.id).lastErrorCode)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.PERSON, p2.id))
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, g2.id))
        assertTrue(backend.writes.none { it.entityId == p1Idea.id })
        assertEquals(listOf(p2.id, g2.id), backend.writes.map { it.entityId }.filter { it != p1.id })
    }

    // ---- each operation at most once per run ----

    @Test
    fun `an operation that stays unresolved is pushed once per run, whatever the reason`() = runTest {
        // Unknown: the update matched nothing, yet the backend shows an older version.
        val unknown = syncedPerson("Unknown", version = 3)
        backend.rows.getValue(unknown).version = 2
        people.updatePerson(unknown, "Unknown K.", null)
        clock.advanceBy(Duration.ofSeconds(1))
        // A missing reference: the parent is known locally, yet absent remotely.
        val parent = syncedPerson("Parent")
        backend.rows.remove(parent)
        val orphan = ideas.addGiftIdea(ideaFor(parent))
        val network = addPerson("Network", null)
        val server = addPerson("Server", null)
        // Scripted in push order: people by age (unknown, network, server), then the idea.
        backend.script += listOf(
            Behavior.Normal,
            Behavior.LoseRequest, Behavior.LoseRequest,
            Behavior.Fail(RemoteFailure.SERVER),
        )

        val result = runner.run(alicesAccount)

        assertEquals(4, result.pushed)
        assertEquals(1, writesFor(unknown))
        assertEquals("the push's own two sends, not a second push", 2, writesFor(network.id))
        assertEquals(1, writesFor(server.id))
        assertEquals(1, writesFor(orphan.id))
        assertEquals(RemoteFailure.MISSING_REFERENCE.name, pending(SyncEntityType.GIFT_IDEA, orphan.id).lastErrorCode)
        listOf(unknown to SyncEntityType.PERSON, network.id to SyncEntityType.PERSON, server.id to SyncEntityType.PERSON,
            orphan.id to SyncEntityType.GIFT_IDEA).forEach { (id, type) ->
            assertNull("never blocked", pending(type, id).blockedReason)
        }
        assertFalse("nothing here needs the user", result.needsAttention)
        assertTrue(result.workRemains)
    }

    @Test
    fun `refused values of a mutation that may still be pending elsewhere are pushed once and reported, never blocked`() = runTest {
        val anna = addPerson("Anna", null)
        // An earlier attempt left the create in flight with its outcome unknown.
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        newPusher().push(alicesAccount, pending(SyncEntityType.PERSON, anna.id).id)
        backend.script += Behavior.Fail(RemoteFailure.CONSTRAINT_VIOLATION)

        val result = runner.run(alicesAccount)

        assertEquals(1, result.pushed)
        assertEquals(3, writesFor(anna.id))
        val after = pending(SyncEntityType.PERSON, anna.id)
        assertNull(after.blockedReason)
        assertEquals(after.mutationId, after.inFlightMutationId)
        assertTrue(result.needsAttention)
    }

    @Test
    fun `a newer change left by a settled mutation waits for the next run`() = runTest {
        val anna = syncedPerson("Anna")
        people.updatePerson(anna, "Anna K.", null)
        val hold = Behavior.Hold()
        backend.script += hold

        val run = async { runner.run(alicesAccount) }
        hold.arrived.await()
        people.updatePerson(anna, "Anna Kovács", "Sister")
        val edit = pending(SyncEntityType.PERSON, anna)
        hold.release.complete(Unit)
        val first = run.await()

        assertEquals(1, first.pushed)
        assertEquals(1, first.settled)
        assertTrue(first.workRemains)
        assertEquals(1, writesFor(anna))
        assertEquals(edit.mutationId, pending(SyncEntityType.PERSON, anna).mutationId)

        val second = runner.run(alicesAccount)

        assertEquals(1, second.pushed)
        assertEquals(edit.mutationId, backend.writes.last().mutationId)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna))
        assertEquals("Anna Kovács", (backend.rows.getValue(anna).fields as RemotePersonFields).name)
    }

    // ---- budget ----

    @Test
    fun `a run makes at most a hundred pushes, and the next run carries on`() = runTest {
        val everyone = (1..105).map { addPerson("Person $it", null).id }

        val first = runner.run(alicesAccount)

        assertEquals(SyncRunner.MAX_PUSHES, first.pushed)
        assertEquals(100, backend.writes.size)
        assertTrue(first.workRemains)
        val untouched = everyone.filter { id -> backend.writes.none { it.entityId == id } }
        assertEquals(5, untouched.size)
        untouched.forEach { assertNull(pending(SyncEntityType.PERSON, it).inFlightMutationId) }

        val second = runner.run(alicesAccount)

        assertEquals(5, second.pushed)
        assertFalse(second.workRemains)
    }

    // ---- one run at a time ----

    @Test
    fun `a run started while another is in progress returns at once and pushes nothing`() = runTest {
        val anna = addPerson("Anna", null)
        addPerson("Béla", null)
        val hold = Behavior.Hold()
        backend.script += hold

        val first = async { runner.run(alicesAccount) }
        hold.arrived.await()
        val second = runner.run(alicesAccount)

        assertEquals(StopReason.ALREADY_RUNNING, second.stoppedBecause)
        assertEquals(0, second.pushed)
        assertEquals(listOf(anna.id), backend.writes.map { it.entityId })
        hold.release.complete(Unit)
        assertEquals(2, first.await().settled)
    }

    @Test
    fun `a cancelled run lets the cancellation through, touches nothing more and does not stay locked`() = runTest {
        val anna = addPerson("Anna", null)
        val bela = addPerson("Béla", null)
        val hold = Behavior.Hold()
        backend.script += hold

        val run = async { runner.run(alicesAccount) }
        hold.arrived.await()
        run.cancel()

        assertTrue(runCatching { run.await() }.exceptionOrNull() is CancellationException)
        assertTrue(backend.writes.none { it.entityId == bela.id })
        assertEquals(pending(SyncEntityType.PERSON, anna.id).mutationId, pending(SyncEntityType.PERSON, anna.id).inFlightMutationId)
        assertNull(pending(SyncEntityType.PERSON, bela.id).inFlightMutationId)
        assertEquals("not left locked", 2, runner.run(alicesAccount).settled)
    }

    // ---- accounts ----

    @Test
    fun `a run pushes only its account's operations, all with its account`() = runTest {
        val anna = addPerson("Anna", null)
        activeOwner.ownerId.value = bob
        val bobsPerson = addPerson("Bob's friend", null)
        activeOwner.ownerId.value = alice

        val result = runner.run(alicesAccount)

        assertEquals(1, result.pushed)
        assertEquals(listOf(anna.id), backend.writes.map { it.entityId })
        assertEquals(token(alice), backend.writes.single().token)
        assertEquals(alice, backend.writes.single().owner)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, bobsPerson.id)?.inFlightMutationId)
    }

    @Test
    fun `a run stops when the active account changes, keeping what it settled`() = runTest {
        val anna = addPerson("Anna", null)
        val bela = addPerson("Béla", null)
        val hold = Behavior.Hold()
        backend.script += hold

        val run = async { runner.run(alicesAccount) }
        hold.arrived.await()
        activeOwner.ownerId.value = bob
        hold.release.complete(Unit)
        val result = run.await()

        assertEquals(StopReason.OWNER_CHANGED, result.stoppedBecause)
        assertEquals(1, result.settled)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.PERSON, anna.id))
        assertTrue(backend.writes.none { it.entityId == bela.id })
        assertNull(pending(SyncEntityType.PERSON, bela.id).inFlightMutationId)
    }

    // ---- what ends a run ----

    @Test
    fun `an unusable session ends the run, leaving the rest untouched`() = runTest {
        assertRunEnds(RemoteFailure.UNAUTHENTICATED, StopReason.SESSION_REQUIRED)
    }

    @Test
    fun `a refused permission ends the run, leaving the rest untouched`() = runTest {
        assertRunEnds(RemoteFailure.FORBIDDEN, StopReason.PERMISSION)
    }

    private suspend fun assertRunEnds(failure: RemoteFailure, reason: StopReason) {
        val first = addPerson("First", null)
        val second = addPerson("Second", null)
        backend.script += Behavior.Fail(failure)

        val result = runner.run(alicesAccount)

        assertEquals(reason, result.stoppedBecause)
        assertEquals(1, result.pushed)
        assertEquals(listOf(first.id), backend.writes.map { it.entityId })
        val stopped = pending(SyncEntityType.PERSON, first.id)
        assertEquals("left in flight, as the push decided", stopped.mutationId, stopped.inFlightMutationId)
        assertNull(stopped.blockedReason)
        assertNull(pending(SyncEntityType.PERSON, second.id).inFlightMutationId)
    }

    // ---- a new runner after process death ----

    @Test
    fun `a fresh runner after process death settles what the last one left in flight, from evidence first`() = runTest {
        val anna = addPerson("Anna", null)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        runner.run(alicesAccount)
        val inFlight = pending(SyncEntityType.PERSON, anna.id)
        assertEquals(inFlight.mutationId, inFlight.inFlightMutationId)
        // One of the lost sends did reach the database.
        backend.recordCommit(anna.id, alice, version = 1, mutationId = inFlight.mutationId, fields = personFields())
        val writesBefore = backend.writes.size

        val result = newRunner().run(alicesAccount)

        assertEquals(1, result.settled)
        assertEquals("settled from evidence, not sent again", writesBefore, backend.writes.size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.PERSON, anna.id))
    }

    // ---- conflicts ----

    @Test
    fun `a conflict is counted as settled, and the backend's row arrives with the next pull`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.updatePerson(anna, "Anna K.", null)
        movedOnRemotely(anna, version = 5) { (it as RemotePersonFields).copy(name = "Anna (other device)") }

        val result = runner.run(alicesAccount)

        assertEquals(SyncRunResult(pushed = 1, settled = 1, conflictsSettled = 1, stoppedBecause = null, workRemains = false, needsAttention = false), result)
        assertEquals("Anna K.", people.getPerson(anna)?.name)

        newPuller().pull(alicesAccount)

        assertEquals("Anna (other device)", people.getPerson(anna)?.name)
        assertEquals(5L, database.personDao().findById(anna, alice)!!.version)
        assertTrue(conflicts().single().localSnapshotJson.contains("\"name\":\"Anna K.\""))
    }

    @Test
    fun `a local deletion that loses comes back from the backend, and is kept as the conflict`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.deletePerson(anna)
        movedOnRemotely(anna, version = 4) { (it as RemotePersonFields).copy(name = "Anna (other device)") }

        assertEquals(1, runner.run(alicesAccount).conflictsSettled)
        newPuller().pull(alicesAccount)

        val after = database.personDao().findById(anna, alice)!!
        assertNull(after.deletedAt)
        assertEquals("Anna (other device)", after.name)
        assertFalse(conflicts().single().localSnapshotJson.contains("\"deleted_at\":null"))
    }

    @Test
    fun `a local edit that loses to a deletion on the backend is deleted, and is kept as the conflict`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.updatePerson(anna, "Anna K.", null)
        val deletedAt = Instant.parse("2026-10-11T08:00:00Z")
        movedOnRemotely(anna, version = 4) { (it as RemotePersonFields).copy(deletedAt = deletedAt) }

        assertEquals(1, runner.run(alicesAccount).conflictsSettled)
        newPuller().pull(alicesAccount)

        assertEquals(deletedAt, database.personDao().findById(anna, alice)!!.deletedAt)
        val snapshot = conflicts().single().localSnapshotJson
        assertTrue(snapshot, snapshot.contains("\"name\":\"Anna K.\"") && snapshot.contains("\"deleted_at\":null"))
    }

    @Test
    fun `a conflicted parent's new child is still pushed in the same run`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.updatePerson(anna, "Anna K.", null)
        movedOnRemotely(anna, version = 4) { it }
        val idea = ideas.addGiftIdea(ideaFor(anna))

        val result = runner.run(alicesAccount)

        assertEquals(1, result.conflictsSettled)
        assertEquals(2, result.settled)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT_IDEA, idea.id))
        assertNull(operations.findForEntity(SyncEntityType.GIFT_IDEA, idea.id))
    }

    @Test
    fun `marking an idea given while another device edited it records the gift, and the idea reopens from the backend`() = runTest {
        // Documents the per-entity outcome of a compound change; a guard against giving the idea twice is a later phase's.
        val anna = syncedPerson("Anna", version = 1)
        val idea = syncedIdea(anna)
        movedOnRemotely(idea, version = 2) { (it as RemoteGiftIdeaFields).copy(note = "From the other device") }
        val gift = ideas.markAsGiven(idea, LocalDate.of(2026, 10, 11), null)

        val result = runner.run(alicesAccount)
        newPuller().pull(alicesAccount)

        assertEquals(1, result.conflictsSettled)
        assertEquals(RemoteState.SYNCED, remoteStateOf(SyncEntityType.GIFT, gift.id))
        assertEquals(idea, backend.rows.getValue(gift.id).let { (it.fields as RemoteGiftFields).sourceGiftIdeaId })
        val reopened = database.giftIdeaDao().findById(idea, alice)!!
        assertNull(reopened.archivedAt)
        assertEquals("From the other device", reopened.note)
        assertFalse(conflicts().single().localSnapshotJson.contains("\"archived_at\":null"))
    }

    @Test
    fun `a conflict blocked by an earlier version is settled by a run, without a write and without waiting on parents`() = runTest {
        val anna = syncedPerson("Anna", version = 1)
        val idea = syncedIdea(anna)
        ideas.updateGiftIdea(idea, ideaFor(anna).copy(name = "Blue scarf"))
        val blocked = pending(SyncEntityType.GIFT_IDEA, idea)
        operations.blockCurrentMutation(blocked.id, blocked.mutationId, BlockReason.REMOTE_NEWER.name)
        movedOnRemotely(idea, version = 3) { it }
        // Its person no longer counts as known remotely, and nothing will push it.
        database.personDao().run { update(findById(anna, alice)!!.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY)) }

        val result = runner.run(alicesAccount)

        assertEquals(SyncRunResult(pushed = 1, settled = 1, conflictsSettled = 1, stoppedBecause = null, workRemains = false, needsAttention = false), result)
        assertTrue(backend.writes.isEmpty())
        assertEquals(blocked.mutationId, conflicts().single().id)
    }

    @Test
    fun `a blocked conflict the backend gives no proof for stays blocked and is not counted as settled`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.updatePerson(anna, "Anna K.", null)
        val blocked = pending(SyncEntityType.PERSON, anna)
        operations.blockCurrentMutation(blocked.id, blocked.mutationId, BlockReason.REMOTE_NEWER.name)

        val result = runner.run(alicesAccount)

        assertEquals(SyncRunResult(pushed = 1, settled = 0, conflictsSettled = 0, stoppedBecause = null, workRemains = false, needsAttention = true), result)
        assertEquals(BlockReason.REMOTE_NEWER.name, pending(SyncEntityType.PERSON, anna).blockedReason)
        assertTrue(conflicts().isEmpty())
    }

    @Test
    fun `repeated runs settle a conflict once`() = runTest {
        val anna = syncedPerson("Anna", version = 3)
        people.updatePerson(anna, "Anna K.", null)
        movedOnRemotely(anna, version = 4) { it }

        val results = List(3) { runner.run(alicesAccount) }

        assertEquals(listOf(1, 0, 0), results.map { it.conflictsSettled })
        assertEquals(1, conflicts().size)
        assertEquals(1, backend.writes.size)
    }

    // ---- attention ----

    @Test
    fun `attention is reported for blocked work and children it holds back, not for failures that may pass`() = runTest {
        // Unknown and a lost network alone need no one.
        val unknown = syncedPerson("Unknown", version = 3)
        backend.rows.getValue(unknown).version = 2
        people.updatePerson(unknown, "Unknown K.", null)
        clock.advanceBy(Duration.ofSeconds(1))
        val offline = addPerson("Offline", null)
        backend.script += listOf(Behavior.Normal, Behavior.LoseRequest, Behavior.LoseRequest)

        val quiet = runner.run(alicesAccount)

        assertFalse(quiet.needsAttention)
        assertNull(pending(SyncEntityType.PERSON, unknown).blockedReason)
        assertNull(pending(SyncEntityType.PERSON, offline.id).blockedReason)

        // A parent that nothing will push: its queue row is gone.
        val orphanParent = addPerson("No queue row", null)
        operations.delete(pending(SyncEntityType.PERSON, orphanParent.id))
        ideas.addGiftIdea(ideaFor(orphanParent.id))

        assertTrue(runner.run(alicesAccount).needsAttention)
    }

    // ---- fixtures ----

    private fun newPusher() = SyncPusher(
        database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
        database.giftDao(),
        SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(), database.syncConflictDao(), clock,
        ),
        backend, backend, clock,
    )

    private fun newPuller() = SyncPuller(
        database, backend, database.syncOperationDao(), database.personDao(), database.occasionDao(),
        database.giftIdeaDao(), database.giftDao(), activeOwner,
    )

    private suspend fun conflicts(): List<SyncConflictEntity> = database.syncConflictDao().observeUnseen(alice).first()

    /** Another device's update of the row, to [version], with [change] to its fields. */
    private fun movedOnRemotely(id: UUID, version: Long, change: (Any) -> Any) {
        backend.rows.getValue(id).apply {
            this.version = version
            lastMutationId = UUID.randomUUID()
            fields = change(fields)
        }
    }

    private fun newRunner() = SyncRunner(
        database.syncOperationDao(),
        SyncDependencies(database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao()),
        newPusher(),
        activeOwner,
    )

    /** Each person gets a later createdAt than the one before, so their push order is known. */
    private suspend fun addPerson(name: String, relationship: String?) =
        people.addPerson(name, relationship).also { clock.advanceBy(Duration.ofSeconds(1)) }

    private suspend fun pending(type: SyncEntityType, id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(type, id)) { "No pending $type operation" }

    private fun writesFor(id: UUID) = backend.writes.count { it.entityId == id }

    private suspend fun remoteStateOf(type: SyncEntityType, id: UUID): RemoteState = when (type) {
        SyncEntityType.PERSON -> database.personDao().findById(id, alice)!!.remoteState
        SyncEntityType.OCCASION -> database.occasionDao().findById(id, alice)!!.remoteState
        SyncEntityType.GIFT_IDEA -> database.giftIdeaDao().findById(id, alice)!!.remoteState
        SyncEntityType.GIFT -> database.giftDao().findById(id, alice)!!.remoteState
        else -> error("Not synced: $type")
    }

    /** A person the backend already has at [version], with nothing pending locally. */
    private suspend fun syncedPerson(name: String, version: Long = 1): UUID {
        val person = addPerson(name, null)
        val dao = database.personDao()
        val entity = dao.findById(person.id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)
        dao.update(entity)
        operations.delete(pending(SyncEntityType.PERSON, person.id))
        backend.seed(person.id, alice, version, UUID.randomUUID(), entity.toRemoteFields())
        return person.id
    }

    /** An idea for [personId] the backend already has at version 1, with nothing pending locally. */
    private suspend fun syncedIdea(personId: UUID): UUID {
        val idea = ideas.addGiftIdea(ideaFor(personId))
        val dao = database.giftIdeaDao()
        val entity = dao.findById(idea.id, alice)!!.copy(version = 1, remoteState = RemoteState.SYNCED)
        dao.update(entity)
        operations.delete(pending(SyncEntityType.GIFT_IDEA, idea.id))
        backend.seed(idea.id, alice, 1, UUID.randomUUID(), entity.toRemoteFields())
        return idea.id
    }

    private fun ideaFor(personId: UUID) = GiftIdeaDetails(
        recipientPersonId = personId, occasionId = null, name = "Scarf", status = GiftIdeaStatus.PURCHASED,
        estimatedPrice = null, actualPrice = null, productUrl = null, note = null,
    )

    private fun birthdayOn(day: Int, month: Int) =
        OccasionDetails(OccasionType.BIRTHDAY, title = null, day = day, month = month, year = null, repeatsYearly = true)

    private fun personFields() = RemotePersonFields("Someone", null, null, null, null)
}
