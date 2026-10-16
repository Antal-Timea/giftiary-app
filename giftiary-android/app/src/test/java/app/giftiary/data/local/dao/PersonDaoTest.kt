package app.giftiary.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
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
class PersonDaoTest {

    private lateinit var database: GiftiaryDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        dao = database.personDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `soft-deleted people are excluded from active reads`() = runTest {
        val anna = person("Anna")
        val bela = person("Béla")
        dao.insert(anna)
        dao.insert(bela)

        val deletedAt = Instant.parse("2026-06-01T12:00:00Z")
        assertEquals(1, dao.softDelete(bela.id, OWNER_USER_ID, deletedAt))

        assertEquals(listOf(anna), dao.observeActive(OWNER_USER_ID).first())
        assertEquals(anna, dao.findActiveById(anna.id, OWNER_USER_ID))
        assertNull(dao.findActiveById(bela.id, OWNER_USER_ID))
    }

    @Test
    fun `soft-deleting an already deleted person updates nothing`() = runTest {
        val anna = person("Anna")
        dao.insert(anna)

        assertEquals(1, dao.softDelete(anna.id, OWNER_USER_ID, Instant.parse("2026-06-01T12:00:00Z")))
        assertEquals(0, dao.softDelete(anna.id, OWNER_USER_ID, Instant.parse("2026-06-02T12:00:00Z")))
    }

    @Test
    fun `active people are ordered by name ignoring case`() = runTest {
        val names = listOf("carol", "Anna", "bob")
        names.forEach { dao.insert(person(it)) }

        assertEquals(listOf("Anna", "bob", "carol"), dao.observeActive(OWNER_USER_ID).first().map { it.name })
    }

    @Test
    fun `people of another owner are never read or deleted`() = runTest {
        val anna = person("Anna")
        val strangersAnna = person("Anna", ownerUserId = OTHER_USER_ID)
        dao.insert(anna)
        dao.insert(strangersAnna)

        assertEquals(listOf(anna), dao.observeActive(OWNER_USER_ID).first())
        assertNull(dao.findActiveById(strangersAnna.id, OWNER_USER_ID))
        assertEquals(0, dao.softDelete(strangersAnna.id, OWNER_USER_ID, Instant.parse("2026-06-01T12:00:00Z")))
        assertEquals(strangersAnna, dao.findActiveById(strangersAnna.id, OTHER_USER_ID))
    }

    private fun person(name: String, ownerUserId: UUID = OWNER_USER_ID) = PersonEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        name = name,
        relationship = null,
        photoPath = null,
        linkedUserId = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = Instant.parse("2026-05-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T12:00:00Z"),
        deletedAt = null,
    )

    private companion object {
        val OWNER_USER_ID: UUID = UUID.randomUUID()
        val OTHER_USER_ID: UUID = UUID.randomUUID()
    }
}
