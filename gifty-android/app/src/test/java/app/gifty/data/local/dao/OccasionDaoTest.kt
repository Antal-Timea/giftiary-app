package app.gifty.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.domain.model.OccasionType
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
class OccasionDaoTest {

    private lateinit var database: GiftyDatabase
    private lateinit var dao: OccasionDao

    private val owner = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()
    private val anna = person(owner)
    private val bela = person(owner)
    private val strangersPerson = person(otherOwner)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        dao = database.occasionDao()
        listOf(anna, bela, strangersPerson).forEach { database.personDao().insert(it) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `all of an owner's active occasions come with their person, never through a deleted or foreign one`() = runTest {
        val annasBirthday = occasion(anna, day = 12, month = 3)
        val christmas = occasion(anna, day = 24, month = 12).copy(personId = null, type = OccasionType.CHRISTMAS)
        val deletedOccasion = occasion(anna, day = 1, month = 1)
        val belasBirthday = occasion(bela, day = 4, month = 11)
        // An owner's row pointing at another owner's person must not reveal that person.
        val throughStranger = occasion(strangersPerson, day = 2, month = 2).copy(ownerUserId = owner)
        val strangers = occasion(strangersPerson, day = 5, month = 5)
        listOf(annasBirthday, christmas, deletedOccasion, belasBirthday, throughStranger, strangers).forEach { dao.insert(it) }
        dao.softDelete(deletedOccasion.id, owner, DELETED_AT)
        database.personDao().softDelete(bela.id, owner, DELETED_AT)

        val rows = dao.observeActiveWithPerson(owner).first()

        assertEquals(listOf(annasBirthday, christmas), rows.map { it.occasion })
        assertEquals(listOf(anna, null), rows.map { it.person })
        assertEquals(listOf(strangers), dao.observeActiveWithPerson(otherOwner).first().map { it.occasion })
    }

    @Test
    fun `observes only the active occasions of the requested owner and person`() = runTest {
        val annasBirthday = occasion(anna, day = 12, month = 3)
        val deleted = occasion(anna, day = 1, month = 1)
        listOf(annasBirthday, deleted, occasion(bela, 4, 11), occasion(strangersPerson, 5, 5)).forEach { dao.insert(it) }
        dao.softDelete(deleted.id, owner, Instant.parse("2026-06-01T00:00:00Z"))

        assertEquals(listOf(annasBirthday), dao.observeActiveForPerson(owner, anna.id).first())
        assertEquals(emptyList<OccasionEntity>(), dao.observeActiveForPerson(owner, strangersPerson.id).first())
    }

    @Test
    fun `occasions are ordered by calendar date, not by insertion or year`() = runTest {
        val christmas = occasion(anna, day = 24, month = 12)
        val birthday = occasion(anna, day = 12, month = 3, year = 1990)
        val nameDay = occasion(anna, day = 26, month = 7)
        val anniversary = occasion(anna, day = 1, month = 7, year = 2015)
        listOf(christmas, birthday, nameDay, anniversary).forEach { dao.insert(it) }

        assertEquals(
            listOf(birthday, anniversary, nameDay, christmas),
            dao.observeActiveForPerson(owner, anna.id).first(),
        )
    }

    @Test
    fun `another owner's occasion cannot be found or deleted`() = runTest {
        val theirs = occasion(strangersPerson, day = 5, month = 5)
        dao.insert(theirs)

        assertNull(dao.findActiveById(theirs.id, owner))
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

    private fun occasion(person: PersonEntity, day: Int, month: Int, year: Int? = null) = OccasionEntity(
        id = UUID.randomUUID(),
        ownerUserId = person.ownerUserId,
        personId = person.id,
        type = OccasionType.BIRTHDAY,
        title = null,
        day = day,
        month = month,
        year = year,
        repeatsYearly = true,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        deletedAt = null,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-05-01T12:00:00Z")
        val DELETED_AT: Instant = Instant.parse("2026-06-01T00:00:00Z")
    }
}
