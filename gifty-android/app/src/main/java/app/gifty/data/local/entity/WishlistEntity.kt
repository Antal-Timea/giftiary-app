package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "wishlist",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("ownerUserId"), Index("personId")],
)
data class WishlistEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val personId: UUID?,
    val name: String,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
