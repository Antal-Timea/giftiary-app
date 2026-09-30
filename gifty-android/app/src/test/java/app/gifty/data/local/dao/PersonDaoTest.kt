package app.gifty.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
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

    private lateinit var database: GiftyDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
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
        assertEquals(1, dao.softDelete(bela.id, deletedAt))

        assertEquals(listOf(anna), dao.observeActive().first())
        assertEquals(anna, dao.findActiveById(anna.id))
        assertNull(dao.findActiveById(bela.id))
    }

    @Test
    fun `soft-deleting an already deleted person updates nothing`() = runTest {
        val anna = person("Anna")
        dao.insert(anna)

        assertEquals(1, dao.softDelete(anna.id, Instant.parse("2026-06-01T12:00:00Z")))
        assertEquals(0, dao.softDelete(anna.id, Instant.parse("2026-06-02T12:00:00Z")))
    }

    @Test
    fun `active people are ordered by name ignoring case`() = runTest {
        val names = listOf("carol", "Anna", "bob")
        names.forEach { dao.insert(person(it)) }

        assertEquals(listOf("Anna", "bob", "carol"), dao.observeActive().first().map { it.name })
    }

    private fun person(name: String) = PersonEntity(
        id = UUID.randomUUID(),
        ownerUserId = OWNER_USER_ID,
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
    }
}
