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

@Dao
interface PersonDao {

    @Query("SELECT * FROM person WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE, id")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE id = :id AND deletedAt IS NULL")
    suspend fun findActiveById(id: UUID): PersonEntity?

    /** Fails if the id already exists, which for a locally created person indicates a bug. */
    @Insert
    suspend fun insert(person: PersonEntity)

    /** Returns the number of updated rows, 0 if the person does not exist. */
    @Update
    suspend fun update(person: PersonEntity): Int

    /** Writes a person regardless of whether it exists, e.g. when applying backend state. */
    @Upsert
    suspend fun upsert(person: PersonEntity)

    /** Returns the number of updated rows, 0 if the person is missing or already deleted. */
    @Query(
        """
        UPDATE person SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, deletedAt: Instant): Int
}
