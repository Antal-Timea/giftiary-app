package app.gifty.data.local.mapper

import app.gifty.data.local.entity.RemoteState
import app.gifty.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun `applying changes keeps sync metadata and creation time`() {
        val synced = person.toNewEntity(ownerUserId).copy(version = 7, remoteState = RemoteState.SYNCED)
        val edited = person.copy(
            name = "Péter",
            relationship = null,
            updatedAt = Instant.parse("2026-03-01T10:00:00Z"),
        )

        val updated = synced.withChangesFrom(edited)

        assertEquals(edited, updated.toDomain())
        assertEquals(7L, updated.version)
        assertEquals(RemoteState.SYNCED, updated.remoteState)
        assertEquals(ownerUserId, updated.ownerUserId)
        assertEquals(synced.createdAt, updated.createdAt)
    }

    @Test
    fun `applying changes from a different person is rejected`() {
        val entity = person.toNewEntity(ownerUserId)

        assertThrows(IllegalArgumentException::class.java) {
            entity.withChangesFrom(person.copy(id = UUID.randomUUID()))
        }
    }
}
