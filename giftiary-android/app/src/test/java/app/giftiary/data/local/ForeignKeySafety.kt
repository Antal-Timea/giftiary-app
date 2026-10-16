package app.giftiary.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.domain.model.OccasionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.Instant
import java.util.UUID

/**
 * What immediate foreign keys guarantee: a write referring to a row that does not exist fails at
 * that statement, its transaction rolls back, and the database stays usable. A deferred key fails
 * only at commit instead, which on Android leaves the transaction open.
 */
object ForeignKeySafety {

    suspend fun assertMissingReferenceRollsBackCleanly(database: GiftiaryDatabase, owner: UUID) {
        val valid = person(owner)
        val dangling = occasion(owner, personId = UUID.randomUUID())
        var reachedAfterInvalidInsert = false

        val failure = runCatching {
            database.withTransaction {
                database.personDao().insert(valid)
                database.occasionDao().insert(dangling)
                reachedAfterInvalidInsert = true
            }
        }.exceptionOrNull()

        assertTrue("$failure", failure is SQLiteConstraintException)
        assertFalse("the insert itself must fail, not the commit", reachedAfterInvalidInsert)
        assertNull("rolled back with the transaction", database.personDao().findById(valid.id, owner))
        assertNull(database.occasionDao().findById(dangling.id, owner))

        // Still usable: the next transaction commits.
        val next = person(owner)
        val birthday = occasion(owner, personId = next.id)
        database.withTransaction {
            database.personDao().insert(next)
            database.occasionDao().insert(birthday)
        }
        assertNotNull(database.occasionDao().findById(birthday.id, owner))
    }

    private fun person(owner: UUID) =
        PersonEntity(UUID.randomUUID(), owner, "Anna", null, null, null, 0, RemoteState.LOCAL_ONLY, NOW, NOW, null)

    private fun occasion(owner: UUID, personId: UUID) = OccasionEntity(
        UUID.randomUUID(), owner, personId, OccasionType.BIRTHDAY, null, 12, 5, null, true, 0, RemoteState.LOCAL_ONLY, NOW, NOW,
        null,
    )

    private val NOW: Instant = Instant.parse("2026-10-12T12:00:00Z")
}
