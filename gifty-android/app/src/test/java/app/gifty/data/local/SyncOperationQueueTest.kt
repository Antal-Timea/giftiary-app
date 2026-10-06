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

    @Test
    fun `update after create keeps the original create in its queue position`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val create = dao.findForEntity(SyncEntityType.PERSON, personId)

        clock.advanceBy(Duration.ofMinutes(5))
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)

        assertEquals(create, dao.findForEntity(SyncEntityType.PERSON, personId))
    }

    @Test
    fun `delete after update replaces the operation and resets failed attempts`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val update = dao.findForEntity(SyncEntityType.PERSON, personId)!!
        dao.update(update.copy(attemptCount = 3, lastAttemptAt = clock.instant, lastErrorCode = "timeout"))

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.SYNCED)

        assertEquals(
            update.copy(operation = SyncOperation.DELETE),
            dao.findForEntity(SyncEntityType.PERSON, personId),
        )
    }

    @Test
    fun `delete after create of a local-only entity removes the pending create`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)

        queue.enqueue(alice, SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.PERSON, personId))
    }

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
            val pending = dao.findForEntity(SyncEntityType.PERSON, id)!!
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

        queue.enqueue(alice, SyncEntityType.PERSON, created.entityId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(alice, SyncEntityType.PERSON, updated.entityId, SyncOperation.DELETE, RemoteState.SYNCED)

        assertEquals(created, dao.findForEntity(SyncEntityType.PERSON, created.entityId))
        assertEquals(
            updated.copy(operation = SyncOperation.DELETE),
            dao.findForEntity(SyncEntityType.PERSON, updated.entityId),
        )
    }

    @Test
    fun `a local-only create migrated without an owner is still removed by a delete`() = runTest {
        val created = unclaimed(SyncOperation.CREATE)

        queue.enqueue(alice, SyncEntityType.PERSON, created.entityId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.PERSON, created.entityId))
    }

    private suspend fun unclaimed(operation: SyncOperation) = SyncOperationEntity(
        id = UUID.randomUUID(),
        ownerUserId = null,
        entityType = SyncEntityType.PERSON,
        entityId = UUID.randomUUID(),
        operation = operation,
        createdAt = clock.instant,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    ).also { dao.insert(it) }

    @Test
    fun `operations for different entities do not interfere`() = runTest {
        val anna = UUID.randomUUID()
        val bela = UUID.randomUUID()
        queue.enqueue(alice, SyncEntityType.PERSON, anna, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(alice, SyncEntityType.PERSON, bela, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(alice, SyncEntityType.WISH, anna, SyncOperation.DELETE, RemoteState.SYNCED)

        queue.enqueue(alice, SyncEntityType.PERSON, anna, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.PERSON, anna))
        assertEquals(SyncOperation.UPDATE, dao.findForEntity(SyncEntityType.PERSON, bela)?.operation)
        assertEquals(SyncOperation.DELETE, dao.findForEntity(SyncEntityType.WISH, anna)?.operation)
    }
}
