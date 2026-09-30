package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gifty.domain.model.GiftIdeaStatus
import java.time.Instant
import java.util.UUID

/**
 * [archivedAt] is set when the idea has been turned into a gift; unlike [deletedAt]
 * the idea still exists as the source of that gift.
 */
@Entity(
    tableName = "gift_idea",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipientPersonId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        ),
        ForeignKey(
            entity = OccasionEntity::class,
            parentColumns = ["id"],
            childColumns = ["occasionId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        ),
    ],
    indices = [Index("recipientPersonId"), Index("occasionId")],
)
data class GiftIdeaEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val recipientPersonId: UUID,
    val occasionId: UUID?,
    val name: String,
    val status: GiftIdeaStatus,
    val estimatedPriceAmountMinor: Long?,
    val estimatedPriceCurrencyCode: String?,
    val actualPriceAmountMinor: Long?,
    val actualPriceCurrencyCode: String?,
    val photoPath: String?,
    val productUrl: String?,
    val note: String?,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
    val deletedAt: Instant?,
)
