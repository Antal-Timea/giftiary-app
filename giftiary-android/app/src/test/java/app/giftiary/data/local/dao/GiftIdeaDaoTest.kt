package app.giftiary.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.GiftIdeaStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class GiftIdeaDaoTest {

    private lateinit var database: GiftiaryDatabase
    private lateinit var dao: GiftIdeaDao

    private val owner = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()
    private val anna = person(owner)
    private val bela = person(owner)
    private val stranger = person(otherOwner)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        dao = database.giftIdeaDao()
        listOf(anna, bela, stranger).forEach { database.personDao().insert(it) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `active ideas exclude deleted, archived and other owners' ideas`() = runTest {
        val active = idea(anna)
        val deleted = idea(anna)
        val archived = idea(anna).copy(archivedAt = Instant.parse("2026-06-01T00:00:00Z"))
        val theirs = idea(stranger)
        listOf(active, deleted, archived, theirs).forEach { dao.insert(it) }
        dao.softDelete(deleted.id, owner, Instant.parse("2026-06-01T00:00:00Z"))

        assertEquals(listOf(active.id), dao.observeActive(owner).first().map { it.idea.id })
        assertNull(dao.findActiveById(archived.id, owner))
        assertEquals(archived, dao.findNotDeletedById(archived.id, owner))
        assertNull(dao.findNotDeletedById(deleted.id, owner))
    }

    @Test
    fun `most recently changed ideas come first, then the newest, then by id`() = runTest {
        val older = idea(anna, updatedAt = "2026-01-01T00:00:00Z")
        val newer = idea(anna, updatedAt = "2026-03-01T00:00:00Z")
        val sameEditOlderCreation = idea(bela, updatedAt = "2026-02-01T00:00:00Z", createdAt = "2025-01-01T00:00:00Z")
        val sameEditNewerCreation = idea(bela, updatedAt = "2026-02-01T00:00:00Z", createdAt = "2025-06-01T00:00:00Z")
        val tieA = idea(anna, updatedAt = "2025-01-01T00:00:00Z", id = UUID.fromString("00000000-0000-0000-0000-00000000000a"))
        val tieB = idea(anna, updatedAt = "2025-01-01T00:00:00Z", id = UUID.fromString("00000000-0000-0000-0000-00000000000b"))
        listOf(tieB, older, sameEditOlderCreation, newer, tieA, sameEditNewerCreation).forEach { dao.insert(it) }

        assertEquals(
            listOf(newer, sameEditNewerCreation, sameEditOlderCreation, older, tieA, tieB).map { it.id },
            dao.observeActive(owner).first().map { it.idea.id },
        )
    }

    @Test
    fun `a person's ideas are those meant for them`() = runTest {
        val forAnna = idea(anna)
        val forBela = idea(bela)
        listOf(forAnna, forBela).forEach { dao.insert(it) }

        assertEquals(listOf(forAnna.id), dao.observeActiveForPerson(owner, anna.id).first().map { it.idea.id })
    }

    @Test
    fun `another owner's idea cannot be found or deleted`() = runTest {
        val theirs = idea(stranger)
        dao.insert(theirs)

        assertNull(dao.findActiveById(theirs.id, owner))
        assertNull(dao.observeActiveById(theirs.id, owner).first())
        assertEquals(0, dao.softDelete(theirs.id, owner, Instant.parse("2026-06-01T00:00:00Z")))
        assertEquals(theirs, dao.findActiveById(theirs.id, otherOwner))
    }

    private fun person(ownerUserId: UUID) = PersonEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        name = "Someone",
        relationship = null,
        photoPath = null,
        linkedUserId = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    private fun idea(
        recipient: PersonEntity,
        updatedAt: String = "2026-01-01T00:00:00Z",
        createdAt: String = "2025-01-01T00:00:00Z",
        id: UUID = UUID.randomUUID(),
    ) = GiftIdeaEntity(
        id = id,
        ownerUserId = recipient.ownerUserId,
        recipientPersonId = recipient.id,
        occasionId = null,
        name = "Scarf",
        status = GiftIdeaStatus.IDEA,
        estimatedPriceAmountMinor = null,
        estimatedPriceCurrencyCode = null,
        actualPriceAmountMinor = null,
        actualPriceCurrencyCode = null,
        photoPath = null,
        productUrl = null,
        note = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        archivedAt = null,
        deletedAt = null,
    )
}
