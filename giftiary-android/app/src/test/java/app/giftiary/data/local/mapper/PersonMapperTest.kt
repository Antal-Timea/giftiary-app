package app.giftiary.data.local.mapper

import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PersonMapperTest {

    private val ownerUserId = UUID.randomUUID()

    private val person = Person(
        id = UUID.randomUUID(),
        name = "Peter",
        relationship = "Brother",
        photoPath = "people/peter.jpg",
        linkedUserId = UUID.randomUUID(),
        createdAt = Instant.parse("2026-01-10T08:00:00Z"),
        updatedAt = Instant.parse("2026-02-01T09:30:00Z"),
    )

    @Test
    fun `new entity is local-only, unversioned and active`() {
        val entity = person.toNewEntity(ownerUserId)

        assertEquals(ownerUserId, entity.ownerUserId)
        assertEquals(0L, entity.version)
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `entity maps back to the same person`() {
        assertEquals(person, person.toNewEntity(ownerUserId).toDomain())
    }
}
