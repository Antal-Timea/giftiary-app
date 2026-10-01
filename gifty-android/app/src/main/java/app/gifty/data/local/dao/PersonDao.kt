package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.gifty.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner so one user's people can never be read or
 * changed on behalf of another.
 */
@Dao
interface PersonDao {

    @Query(
        """
        SELECT * FROM person
        WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL
        ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun observeActive(ownerUserId: UUID): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    fun observeActiveById(id: UUID, ownerUserId: UUID): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun findActiveById(id: UUID, ownerUserId: UUID): PersonEntity?

    /** Includes soft-deleted rows; only for checking references existing records already have. */
    @Query("SELECT * FROM person WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun findById(id: UUID, ownerUserId: UUID): PersonEntity?

    /** Fails if the id already exists, which for a locally created person indicates a bug. */
    @Insert
    suspend fun insert(person: PersonEntity)

    /** Returns the number of updated rows, 0 if the person does not exist. */
    @Update
    suspend fun update(person: PersonEntity): Int

    /** Writes a person regardless of whether it exists, e.g. when applying backend state. */
    @Upsert
    suspend fun upsert(person: PersonEntity)

    /** Returns the number of updated rows, 0 if the person is missing, already deleted or not owned. */
    @Query(
        """
        UPDATE person SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, ownerUserId: UUID, deletedAt: Instant): Int
}
