package app.giftiary.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SyncOperationDaoTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private lateinit var database: GiftiaryDatabase
    private lateinit var dao: SyncOperationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        dao = database.syncOperationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a second pending operation for the same entity is rejected`() = runTest {
        val personId = UUID.randomUUID()
        val create = operation(SyncEntityType.PERSON, personId, SyncOperation.CREATE)
        dao.insert(create)

        val duplicate = runCatching { dao.insert(operation(SyncEntityType.PERSON, personId, SyncOperation.DELETE)) }

        assertTrue(duplicate.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(listOf(create), dao.getPending(alice, limit = 10))
    }

    @Test
    fun `same id under different entity types are separate operations`() = runTest {
        val entityId = UUID.randomUUID()
        val person = operation(SyncEntityType.PERSON, entityId, SyncOperation.CREATE)
        val wish = operation(SyncEntityType.WISH, entityId, SyncOperation.UPDATE)
        dao.insert(person)
        dao.insert(wish)

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
        listOf(newer, newest, older).forEach { dao.insert(it) }

        assertEquals(listOf(older, newer), dao.getPending(alice, limit = 2))
    }

    @Test
    fun `pending operations are read per owner and unowned ones by nobody`() = runTest {
        val alicesOlder = operation(
            SyncEntityType.PERSON, UUID.randomUUID(), SyncOperation.CREATE, Instant.parse("2026-05-01T00:00:00Z"),
        )
        val alicesNewer = operation(
            SyncEntityType.GIFT, UUID.randomUUID(), SyncOperation.CREATE, Instant.parse("2026-05-03T00:00:00Z"),
        )
        val bobs = operation(SyncEntityType.PERSON, UUID.randomUUID(), SyncOperation.UPDATE, owner = bob)
        val unowned = operation(SyncEntityType.OCCASION, UUID.randomUUID(), SyncOperation.CREATE, owner = null)
        listOf(alicesNewer, unowned, bobs, alicesOlder).forEach { dao.insert(it) }

        assertEquals(listOf(alicesOlder, alicesNewer), dao.getPending(alice, limit = 10))
        assertEquals(listOf(bobs), dao.getPending(bob, limit = 10))
        assertEquals(unowned, dao.findForEntity(SyncEntityType.OCCASION, unowned.entityId))
    }

    @Test
    fun `mutation state is stored exactly as written`() = runTest {
        val sent = UUID.randomUUID()
        val blocked = operation(SyncEntityType.GIFT_IDEA, UUID.randomUUID(), SyncOperation.UPDATE)
            .copy(inFlightMutationId = sent, blockedReason = "rejected")
        dao.insert(blocked)

        assertEquals(blocked, dao.findForEntity(SyncEntityType.GIFT_IDEA, blocked.entityId))
    }

    private fun operation(
        entityType: SyncEntityType,
        entityId: UUID,
        operation: SyncOperation,
        createdAt: Instant = Instant.parse("2026-05-01T12:00:00Z"),
        owner: UUID? = alice,
    ) = SyncOperationEntity(
        id = UUID.randomUUID(),
        ownerUserId = owner,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        mutationId = UUID.randomUUID(),
        inFlightMutationId = null,
        blockedReason = null,
        createdAt = createdAt,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    )
}
