package app.gifty.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SyncOperationDaoTest {

    private lateinit var database: GiftyDatabase
    private lateinit var dao: SyncOperationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        dao = database.syncOperationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert keeps a single pending operation per entity`() = runTest {
        val personId = UUID.randomUUID()
        val create = operation(SyncEntityType.PERSON, personId, SyncOperation.CREATE)
            .copy(attemptCount = 2, lastErrorCode = "network")
        val delete = operation(SyncEntityType.PERSON, personId, SyncOperation.DELETE)
        dao.upsert(create)
        dao.upsert(delete)

        assertEquals(delete, dao.findForEntity(SyncEntityType.PERSON, personId))
        assertEquals(listOf(delete), dao.getPending(limit = 10))
    }

    @Test
    fun `same id under different entity types are separate operations`() = runTest {
        val entityId = UUID.randomUUID()
        val person = operation(SyncEntityType.PERSON, entityId, SyncOperation.CREATE)
        val wish = operation(SyncEntityType.WISH, entityId, SyncOperation.UPDATE)
        dao.upsert(person)
        dao.upsert(wish)

        assertEquals(person, dao.findForEntity(SyncEntityType.PERSON, entityId))
        assertEquals(wish, dao.findForEntity(SyncEntityType.WISH, entityId))
    }

    @Test
    fun `pending operations are returned oldest first up to the limit`() = runTest {
        val (older, newer, newest) = listOf("2026-05-01", "2026-05-02", "2026-05-03").map { day ->
            operation(
                entityType = SyncEntityType.GIFT,
                entityId = UUID.randomUUID(),
                operation = SyncOperation.CREATE,
                createdAt = Instant.parse("${day}T00:00:00Z"),
            )
        }
        listOf(newer, newest, older).forEach { dao.upsert(it) }

        assertEquals(listOf(older, newer), dao.getPending(limit = 2))
    }

    private fun operation(
        entityType: SyncEntityType,
        entityId: UUID,
        operation: SyncOperation,
        createdAt: Instant = Instant.parse("2026-05-01T12:00:00Z"),
    ) = SyncOperationEntity(
        id = UUID.randomUUID(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        createdAt = createdAt,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    )
}
