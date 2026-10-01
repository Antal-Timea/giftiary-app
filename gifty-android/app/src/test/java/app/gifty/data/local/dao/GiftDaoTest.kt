package app.gifty.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.domain.model.GiftDirection
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
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class GiftDaoTest {

    private lateinit var database: GiftyDatabase
    private lateinit var dao: GiftDao

    private val owner = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()
    private val anna = person(owner)
    private val bela = person(owner)
    private val stranger = person(otherOwner)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        dao = database.giftDao()
        listOf(anna, bela, stranger).forEach { database.personDao().insert(it) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observes only the owner's active gifts`() = runTest {
        val mine = given(to = anna, on = "2026-03-12")
        val deleted = given(to = anna, on = "2026-03-13")
        val theirs = given(to = stranger, on = "2026-03-14", ownerUserId = otherOwner)
        listOf(mine, deleted, theirs).forEach { dao.insert(it) }
        dao.softDelete(deleted.id, owner, Instant.parse("2026-06-01T00:00:00Z"))

        assertEquals(listOf(mine.id), dao.observeActive(owner).first().map { it.gift.id })
    }

    @Test
    fun `newest gifts come first, then the most recently changed, then by id`() = runTest {
        val older = given(to = anna, on = "2025-11-04")
        val newest = given(to = anna, on = "2026-03-12")
        val sameDayEarlierEdit = given(to = bela, on = "2026-01-01", updatedAt = "2026-01-01T10:00:00Z")
        val sameDayLaterEdit = given(to = bela, on = "2026-01-01", updatedAt = "2026-01-02T10:00:00Z")
        val tieA = given(to = anna, on = "2025-01-01", id = UUID.fromString("00000000-0000-0000-0000-00000000000a"))
        val tieB = given(to = anna, on = "2025-01-01", id = UUID.fromString("00000000-0000-0000-0000-00000000000b"))
        listOf(tieB, older, sameDayEarlierEdit, newest, tieA, sameDayLaterEdit).forEach { dao.insert(it) }

        assertEquals(
            listOf(newest, sameDayLaterEdit, sameDayEarlierEdit, older, tieA, tieB).map { it.id },
            dao.observeActive(owner).first().map { it.gift.id },
        )
    }

    @Test
    fun `a person's gifts include those they gave and those they received`() = runTest {
        val toAnna = given(to = anna, on = "2026-03-12")
        val fromAnna = received(from = anna, on = "2026-02-01")
        val annaToBela = given(to = bela, on = "2026-01-01").copy(giverPersonId = anna.id)
        val unrelated = given(to = bela, on = "2025-12-24")
        listOf(toAnna, fromAnna, annaToBela, unrelated).forEach { dao.insert(it) }

        assertEquals(
            listOf(toAnna, fromAnna, annaToBela).map { it.id },
            dao.observeActiveForPerson(owner, anna.id).first().map { it.gift.id },
        )
    }

    @Test
    fun `references resolve even after the person is deleted`() = runTest {
        val gift = given(to = anna, on = "2026-03-12")
        dao.insert(gift)
        database.personDao().softDelete(anna.id, owner, Instant.parse("2026-06-01T00:00:00Z"))

        assertEquals(anna.id, dao.observeActiveById(gift.id, owner).first()?.recipient?.id)
    }

    @Test
    fun `another owner's gift cannot be found or deleted`() = runTest {
        val theirs = given(to = stranger, on = "2026-03-14", ownerUserId = otherOwner)
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
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        deletedAt = null,
    )

    private fun given(
        to: PersonEntity,
        on: String,
        ownerUserId: UUID = owner,
        updatedAt: String = "2026-01-01T00:00:00Z",
        id: UUID = UUID.randomUUID(),
    ) = gift(GiftDirection.GIVEN, giver = null, recipient = to.id, on, ownerUserId, updatedAt, id)

    private fun received(from: PersonEntity, on: String) =
        gift(GiftDirection.RECEIVED, giver = from.id, recipient = null, on, owner, "2026-01-01T00:00:00Z", UUID.randomUUID())

    private fun gift(
        direction: GiftDirection,
        giver: UUID?,
        recipient: UUID?,
        on: String,
        ownerUserId: UUID,
        updatedAt: String,
        id: UUID,
    ) = GiftEntity(
        id = id,
        ownerUserId = ownerUserId,
        direction = direction,
        giverPersonId = giver,
        recipientPersonId = recipient,
        occasionId = null,
        sourceGiftIdeaId = null,
        name = "Book",
        givenAt = LocalDate.parse(on),
        priceAmountMinor = null,
        priceCurrencyCode = null,
        photoPath = null,
        productUrl = null,
        note = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = CREATED_AT,
        updatedAt = Instant.parse(updatedAt),
        deletedAt = null,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
