package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.gifty.data.local.entity.WishEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner, which for a wish is always its wishlist's
 * owner, so one user's wishes can never be read or changed on behalf of another.
 */
@Dao
interface WishDao {

    @Query(
        """
        SELECT * FROM wish
        WHERE ownerUserId = :ownerUserId AND wishlistId = :wishlistId AND deletedAt IS NULL
        ORDER BY createdAt, id
        """,
    )
    fun observeActiveForWishlist(ownerUserId: UUID, wishlistId: UUID): Flow<List<WishEntity>>

    @Query("SELECT * FROM wish WHERE ownerUserId = :ownerUserId AND wishlistId = :wishlistId AND deletedAt IS NULL")
    suspend fun findActiveForWishlist(ownerUserId: UUID, wishlistId: UUID): List<WishEntity>

    @Query("SELECT * FROM wish WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun findActiveById(id: UUID, ownerUserId: UUID): WishEntity?

    /** Includes soft-deleted rows; for sync. */
    @Query("SELECT * FROM wish WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun findById(id: UUID, ownerUserId: UUID): WishEntity?

    /** Whichever account's row has [id]; for sync, which must tell another account's row from none. */
    @Query("SELECT * FROM wish WHERE id = :id")
    suspend fun findByIdForAnyOwner(id: UUID): WishEntity?

    /** Fails if the id already exists, or if the wishlist is not this owner's. */
    @Insert
    suspend fun insert(wish: WishEntity)

    @Update
    suspend fun update(wish: WishEntity): Int

    /** Returns the number of updated rows, 0 if the wish is missing, already deleted or not owned. */
    @Query(
        """
        UPDATE wish SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, ownerUserId: UUID, deletedAt: Instant): Int

    /** As for every synced table: adopts the committed [version], only from the version before it. */
    @Query(
        """
        UPDATE wish SET version = :version, remoteState = 'SYNCED'
        WHERE id = :id AND ownerUserId = :ownerUserId AND version = :version - 1
        """,
    )
    suspend fun adoptRemoteVersion(id: UUID, ownerUserId: UUID, version: Long): Int
}
