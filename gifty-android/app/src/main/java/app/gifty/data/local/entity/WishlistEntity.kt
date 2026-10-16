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
        // Without a person (null personId) the key does not apply. The update cascade lets the
        // legacy claim move a person's wishlists, and through them their wishes, with the person.
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id", "ownerUserId"],
            childColumns = ["personId", "ownerUserId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    // (id, ownerUserId) is the target of the wishes' same-owner foreign key.
    indices = [
        Index("ownerUserId"),
        Index(value = ["personId", "ownerUserId"]),
        Index(value = ["id", "ownerUserId"], unique = true),
    ],
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
