package app.giftiary.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "wish",
    foreignKeys = [
        // A wish always belongs to its wishlist's owner. Owners never change, so the update
        // cascade (kept from the schema history) never fires. Deletion is never cascaded.
        ForeignKey(
            entity = WishlistEntity::class,
            parentColumns = ["id", "ownerUserId"],
            childColumns = ["wishlistId", "ownerUserId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("ownerUserId"), Index(value = ["wishlistId", "ownerUserId"])],
)
data class WishEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val wishlistId: UUID,
    val name: String,
    val note: String?,
    val estimatedPriceAmountMinor: Long?,
    val estimatedPriceCurrencyCode: String?,
    val productUrl: String?,
    val photoPath: String?,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
