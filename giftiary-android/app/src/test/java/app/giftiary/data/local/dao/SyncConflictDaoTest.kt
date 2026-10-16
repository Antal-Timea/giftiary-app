package app.giftiary.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.SyncConflictEntity
import app.giftiary.data.local.entity.SyncEntityType
import kotlinx.coroutines.flow.first
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
class SyncConflictDaoTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private lateinit var database: GiftiaryDatabase
    private lateinit var dao: SyncConflictDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        dao = database.syncConflictDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `unseen conflicts are read per owner, newest first`() = runTest {
        val older = conflict(alice, Instant.parse("2026-05-01T12:00:00Z"))
        val newer = conflict(alice, Instant.parse("2026-05-02T12:00:00Z"))
        val bobs = conflict(bob, Instant.parse("2026-05-03T12:00:00Z"))
        listOf(older, bobs, newer).forEach { dao.insert(it) }

        assertEquals(listOf(newer, older), dao.observeUnseen(alice).first())
        assertEquals(listOf(bobs), dao.observeUnseen(bob).first())
    }

    @Test
    fun `a conflict is marked seen once, and only by its owner`() = runTest {
        val conflict = conflict(alice, Instant.parse("2026-05-01T12:00:00Z")).also { dao.insert(it) }
        val seenAt = Instant.parse("2026-05-01T13:00:00Z")

        assertEquals(0, dao.markSeen(conflict.id, bob, seenAt))
        assertEquals(1, dao.markSeen(conflict.id, alice, seenAt))
        assertEquals(0, dao.markSeen(conflict.id, alice, seenAt.plusSeconds(60)))

        assertEquals(emptyList<SyncConflictEntity>(), dao.observeUnseen(alice).first())
    }

    private fun conflict(owner: UUID, createdAt: Instant) = SyncConflictEntity(
        id = UUID.randomUUID(),
        ownerUserId = owner,
        entityType = SyncEntityType.PERSON,
        entityId = UUID.randomUUID(),
        mutationId = UUID.randomUUID(),
        baseVersion = 3,
        remoteVersion = 5,
        localSnapshotJson = """{"name":"Anna"}""",
        createdAt = createdAt,
        seenAt = null,
    )
}
