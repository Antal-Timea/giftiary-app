package app.gifty.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.testing.MutableClock
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SyncOperationQueueTest {

    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00Z"))
    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private lateinit var database: GiftyDatabase
    private lateinit var dao: SyncOperationDao
    private lateinit var queue: SyncOperationQueue

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        dao = database.syncOperationDao()
        queue = SyncOperationQueue(dao, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- mutation ids ----

    @Test
    fun `a new operation gets its own mutation id and nothing in flight or blocked`() = runTest {
        val anna = UUID.randomUUID()
        val bela = UUID.randomUUID()

        queue.enqueue(alice, SyncEntityType.PERSON, anna, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(alice, SyncEntityType.PERSON, bela, SyncOperation.UPDATE, RemoteState.SYNCED)

        val first = pending(SyncEntityType.PERSON, anna)
        val second = pending(SyncEntityType.PERSON, bela)
        assertNotEquals(first.mutationId, second.mutationId)
        listOf(first, second).forEach {
            assertNull(it.inFlightMutationId)
            assertNull(it.blockedReason)
        }
    }

    @Test
    fun `every change folded into a pending operation is a new mutation`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val created = pending(SyncEntityType.PERSON, personId)

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)
        val firstEdit = pending(SyncEntityType.PERSON, personId)
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)
        val secondEdit = pending(SyncEntityType.PERSON, personId)

        assertEquals(3, setOf(created.mutationId, firstEdit.mutationId, secondEdit.mutationId).size)
        assertEquals(created.copy(mutationId = secondEdit.mutationId), secondEdit)
    }

    @Test
    fun `update after create keeps the original create in its queue position`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val create = pending(SyncEntityType.PERSON, personId)

        clock.advanceBy(Duration.ofMinutes(5))
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)

        val coalesced = pending(SyncEntityType.PERSON, personId)
        assertEquals(create.copy(mutationId = coalesced.mutationId), coalesced)
    }

    @Test
    fun `delete after update replaces the operation and resets failed attempts`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val update = pending(SyncEntityType.PERSON, personId)
        dao.update(update.copy(attemptCount = 3, lastAttemptAt = clock.instant, lastErrorCode = "timeout"))

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.SYNCED)

        val delete = pending(SyncEntityType.PERSON, personId)
        assertNotEquals(update.mutationId, delete.mutationId)
        assertEquals(update.copy(operation = SyncOperation.DELETE, mutationId = delete.mutationId), delete)
    }

    // ---- a mutation in flight ----

    @Test
    fun `an edit while an update is in flight is a new mutation and keeps the one in flight`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val sent = sendCurrentMutation(SyncEntityType.PERSON, personId)

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)

        val edited = pending(SyncEntityType.PERSON, personId)
        assertEquals(SyncOperation.UPDATE, edited.operation)
        assertNotEquals(sent.mutationId, edited.mutationId)
        assertEquals(sent.mutationId, edited.inFlightMutationId)
        assertEquals(sent.copy(mutationId = edited.mutationId), edited)
    }

    @Test
    fun `a delete while an update is in flight keeps the one in flight`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val sent = sendCurrentMutation(SyncEntityType.PERSON, personId)

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.SYNCED)

        val deleted = pending(SyncEntityType.PERSON, personId)
        assertEquals(SyncOperation.DELETE, deleted.operation)
        assertNotEquals(sent.mutationId, deleted.mutationId)
        assertEquals(sent.mutationId, deleted.inFlightMutationId)
    }

    @Test
    fun `an edit while a create is in flight stays a create and keeps the one in flight`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val sent = sendCurrentMutation(SyncEntityType.PERSON, personId)

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)

        val edited = pending(SyncEntityType.PERSON, personId)
        assertEquals(SyncOperation.CREATE, edited.operation)
        assertNotEquals(sent.mutationId, edited.mutationId)
        assertEquals(sent.mutationId, edited.inFlightMutationId)
    }

    @Test
    fun `deleting a gift whose create is in flight keeps the create, because it may have committed`() = runTest {
        val giftId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.GIFT, giftId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val sent = sendCurrentMutation(SyncEntityType.GIFT, giftId)

        queue.enqueue(alice, SyncEntityType.GIFT, giftId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        val tombstone = pending(SyncEntityType.GIFT, giftId)
        assertEquals(SyncOperation.CREATE, tombstone.operation)
        assertNotEquals(sent.mutationId, tombstone.mutationId)
        assertEquals(sent.mutationId, tombstone.inFlightMutationId)
    }

    @Test
    fun `only the current payload can be marked in flight, and only one at a time`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val first = pending(SyncEntityType.PERSON, personId)

        assertEquals(0, dao.markInFlight(first.id, UUID.randomUUID()))
        assertEquals(1, dao.markInFlight(first.id, first.mutationId))
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val second = pending(SyncEntityType.PERSON, personId)

        assertEquals(0, dao.markInFlight(second.id, second.mutationId))
        assertEquals(0, dao.clearInFlight(second.id, second.mutationId))
        assertEquals(first.mutationId, pending(SyncEntityType.PERSON, personId).inFlightMutationId)
        assertEquals(1, dao.clearInFlight(second.id, first.mutationId))
        assertEquals(1, dao.markInFlight(second.id, second.mutationId))
    }

    // ---- blocked operations ----

    @Test
    fun `a new edit unblocks the operation, as a new mutation, keeping the one in flight`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val sent = sendCurrentMutation(SyncEntityType.PERSON, personId)
        dao.setBlockedReason(sent.id, "rejected")

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)

        val edited = pending(SyncEntityType.PERSON, personId)
        assertNull(edited.blockedReason)
        assertNotEquals(sent.mutationId, edited.mutationId)
        assertEquals(sent.mutationId, edited.inFlightMutationId)
    }

    // ---- deleting what the backend has never seen ----

    @Test
    fun `deleting a local-only entity that others may reference keeps its create as a tombstone`() = runTest {
        listOf(SyncEntityType.PERSON, SyncEntityType.OCCASION, SyncEntityType.GIFT_IDEA).forEach { entityType ->
            val entityId = UUID.randomUUID()
            queue.enqueue(alice, entityType, entityId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
            val create = pending(entityType, entityId)

            queue.enqueue(alice, entityType, entityId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

            val tombstone = pending(entityType, entityId)
            assertEquals("$entityType", create.copy(mutationId = tombstone.mutationId), tombstone)
            assertNotEquals("$entityType", create.mutationId, tombstone.mutationId)
        }
    }

    @Test
    fun `deleting a local-only gift removes its pending create, since nothing references a gift`() = runTest {
        val giftId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.GIFT, giftId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)

        queue.enqueue(alice, SyncEntityType.GIFT, giftId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.GIFT, giftId))
    }

    // ---- owners ----

    @Test
    fun `a new operation belongs to the owner it was enqueued for`() = runTest {
        val personId = UUID.randomUUID()

        queue.enqueue(bob, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)

        assertEquals(bob, dao.findForEntity(SyncEntityType.PERSON, personId)?.ownerUserId)
    }

    @Test
    fun `coalescing keeps the owner of the pending operation`() = runTest {
        val created = UUID.randomUUID()
        val updated = UUID.randomUUID()
        val deleted = UUID.randomUUID()
        val synced = UUID.randomUUID()
        queue.enqueue(bob, SyncEntityType.PERSON, created, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(bob, SyncEntityType.PERSON, updated, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(bob, SyncEntityType.PERSON, deleted, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(bob, SyncEntityType.PERSON, synced, SyncOperation.CREATE, RemoteState.SYNCED)

        queue.enqueue(bob, SyncEntityType.PERSON, created, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(bob, SyncEntityType.PERSON, updated, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(bob, SyncEntityType.PERSON, deleted, SyncOperation.DELETE, RemoteState.SYNCED)
        queue.enqueue(bob, SyncEntityType.PERSON, synced, SyncOperation.DELETE, RemoteState.SYNCED)

        mapOf(
            created to SyncOperation.CREATE,
            updated to SyncOperation.UPDATE,
            deleted to SyncOperation.DELETE,
            synced to SyncOperation.DELETE,
        ).forEach { (id, operation) ->
            val pending = pending(SyncEntityType.PERSON, id)
            assertEquals(operation, pending.operation)
            assertEquals(bob, pending.ownerUserId)
        }
    }

    @Test
    fun `an operation cannot be taken over by another owner`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val pending = dao.findForEntity(SyncEntityType.PERSON, personId)

        val takeover = runCatching {
            queue.enqueue(bob, SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.SYNCED)
        }

        assertTrue(takeover.exceptionOrNull() is IllegalStateException)
        assertEquals(pending, dao.findForEntity(SyncEntityType.PERSON, personId))
    }

    @Test
    fun `an operation migrated without an owner stays without one when coalesced`() = runTest {
        // Pre-v2 operations get their owner from the legacy claim, never from a later write.
        val created = unclaimed(SyncOperation.CREATE)
        val updated = unclaimed(SyncOperation.UPDATE)
        val deleted = unclaimed(SyncOperation.CREATE)

        queue.enqueue(alice, SyncEntityType.PERSON, created.entityId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(alice, SyncEntityType.PERSON, updated.entityId, SyncOperation.DELETE, RemoteState.SYNCED)
        queue.enqueue(alice, SyncEntityType.PERSON, deleted.entityId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        val createdNow = pending(SyncEntityType.PERSON, created.entityId)
        assertEquals(created.copy(mutationId = createdNow.mutationId), createdNow)
        val updatedNow = pending(SyncEntityType.PERSON, updated.entityId)
        assertEquals(updated.copy(operation = SyncOperation.DELETE, mutationId = updatedNow.mutationId), updatedNow)
        val deletedNow = pending(SyncEntityType.PERSON, deleted.entityId)
        assertEquals(deleted.copy(mutationId = deletedNow.mutationId), deletedNow)
    }

    private suspend fun unclaimed(operation: SyncOperation) = SyncOperationEntity(
        id = UUID.randomUUID(),
        ownerUserId = null,
        entityType = SyncEntityType.PERSON,
        entityId = UUID.randomUUID(),
        operation = operation,
        mutationId = UUID.randomUUID(),
        inFlightMutationId = null,
        blockedReason = null,
        createdAt = clock.instant,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    ).also { dao.insert(it) }

    @Test
    fun `operations for different entities do not interfere`() = runTest {
        val anna = UUID.randomUUID()
        val bela = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.GIFT, anna, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(alice, SyncEntityType.PERSON, bela, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(alice, SyncEntityType.WISH, anna, SyncOperation.DELETE, RemoteState.SYNCED)
        val belasUpdate = pending(SyncEntityType.PERSON, bela)
        val wishDelete = pending(SyncEntityType.WISH, anna)

        queue.enqueue(alice, SyncEntityType.GIFT, anna, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.GIFT, anna))
        assertEquals(belasUpdate, pending(SyncEntityType.PERSON, bela))
        assertEquals(wishDelete, pending(SyncEntityType.WISH, anna))
    }

    private suspend fun pending(entityType: SyncEntityType, entityId: UUID): SyncOperationEntity =
        checkNotNull(dao.findForEntity(entityType, entityId)) { "No pending $entityType operation for $entityId" }

    /** What sync will do before sending: the current payload becomes the mutation in flight. */
    private suspend fun sendCurrentMutation(entityType: SyncEntityType, entityId: UUID): SyncOperationEntity {
        val operation = pending(entityType, entityId)
        assertEquals(1, dao.markInFlight(operation.id, operation.mutationId))
        return pending(entityType, entityId).also { assertEquals(operation.mutationId, it.inFlightMutationId) }
    }
}
