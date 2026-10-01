package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "wish",
    foreignKeys = [
        ForeignKey(
            entity = WishlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["wishlistId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        ),
    ],
    indices = [Index("wishlistId")],
)
data class WishEntity(
    @PrimaryKey val id: UUID,
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
