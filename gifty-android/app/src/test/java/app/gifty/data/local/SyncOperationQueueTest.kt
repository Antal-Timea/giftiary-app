package app.gifty.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        val create = dao.findForEntity(SyncEntityType.PERSON, personId)

        clock.advanceBy(Duration.ofMinutes(5))
        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.LOCAL_ONLY)

        assertEquals(create, dao.findForEntity(SyncEntityType.PERSON, personId))
    }

    @Test
    fun `delete after update replaces the operation and resets failed attempts`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.UPDATE, RemoteState.SYNCED)
        val update = dao.findForEntity(SyncEntityType.PERSON, personId)!!
        dao.update(update.copy(attemptCount = 3, lastAttemptAt = clock.instant, lastErrorCode = "timeout"))

        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.SYNCED)

        assertEquals(
            update.copy(operation = SyncOperation.DELETE),
            dao.findForEntity(SyncEntityType.PERSON, personId),
        )
    }

    @Test
    fun `delete after create of a local-only entity removes the pending create`() = runTest {
        val personId = UUID.randomUUID()
        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)

        queue.enqueue(SyncEntityType.PERSON, personId, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.PERSON, personId))
    }

    @Test
    fun `operations for different entities do not interfere`() = runTest {
        val anna = UUID.randomUUID()
        val bela = UUID.randomUUID()
        queue.enqueue(SyncEntityType.PERSON, anna, SyncOperation.CREATE, RemoteState.LOCAL_ONLY)
        queue.enqueue(SyncEntityType.PERSON, bela, SyncOperation.UPDATE, RemoteState.SYNCED)
        queue.enqueue(SyncEntityType.WISH, anna, SyncOperation.DELETE, RemoteState.SYNCED)

        queue.enqueue(SyncEntityType.PERSON, anna, SyncOperation.DELETE, RemoteState.LOCAL_ONLY)

        assertNull(dao.findForEntity(SyncEntityType.PERSON, anna))
        assertEquals(SyncOperation.UPDATE, dao.findForEntity(SyncEntityType.PERSON, bela)?.operation)
        assertEquals(SyncOperation.DELETE, dao.findForEntity(SyncEntityType.WISH, anna)?.operation)
    }
}
