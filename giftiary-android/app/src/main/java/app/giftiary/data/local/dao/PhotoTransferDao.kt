package app.giftiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.giftiary.data.local.entity.PhotoTransferEntity
import app.giftiary.data.local.entity.PhotoTransferKind
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PhotoTransferDao {

    /** A newer decision about the same photo replaces the older one. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(transfer: PhotoTransferEntity)

    @Query("SELECT * FROM photo_transfer WHERE path = :path AND ownerUserId = :ownerUserId")
    suspend fun find(path: String, ownerUserId: UUID): PhotoTransferEntity?

    /** One owner's transfers of one kind, oldest first. */
    @Query("SELECT * FROM photo_transfer WHERE ownerUserId = :ownerUserId AND kind = :kind ORDER BY createdAt, path")
    suspend fun getAll(ownerUserId: UUID, kind: PhotoTransferKind): List<PhotoTransferEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM photo_transfer WHERE path = :path AND ownerUserId = :ownerUserId AND kind = :kind)")
    fun observeExists(path: String, ownerUserId: UUID, kind: PhotoTransferKind): Flow<Boolean>

    /** Whether any of the owner's gifts, gift ideas, people or wishes, deleted ones included, names the photo. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM gift WHERE ownerUserId = :ownerUserId AND photoPath = :path) " +
            "OR EXISTS(SELECT 1 FROM gift_idea WHERE ownerUserId = :ownerUserId AND photoPath = :path) " +
            "OR EXISTS(SELECT 1 FROM person WHERE ownerUserId = :ownerUserId AND photoPath = :path) " +
            "OR EXISTS(SELECT 1 FROM wish WHERE ownerUserId = :ownerUserId AND photoPath = :path)",
    )
    suspend fun isNamed(path: String, ownerUserId: UUID): Boolean

    @Query("SELECT path FROM photo_transfer WHERE ownerUserId = :ownerUserId")
    suspend fun getPaths(ownerUserId: UUID): List<String>

    @Query("DELETE FROM photo_transfer WHERE path = :path AND ownerUserId = :ownerUserId")
    suspend fun delete(path: String, ownerUserId: UUID)

    @Query(
        "UPDATE photo_transfer SET attemptCount = attemptCount + 1, lastErrorCode = :errorCode " +
            "WHERE path = :path AND ownerUserId = :ownerUserId",
    )
    suspend fun recordFailure(path: String, ownerUserId: UUID, errorCode: String)
}
