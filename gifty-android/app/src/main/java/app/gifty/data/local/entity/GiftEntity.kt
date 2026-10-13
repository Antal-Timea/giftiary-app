package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gifty.domain.model.GiftDirection
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "gift",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["giverPersonId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipientPersonId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = OccasionEntity::class,
            parentColumns = ["id"],
            childColumns = ["occasionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = GiftIdeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceGiftIdeaId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("ownerUserId"),
        Index("giverPersonId"),
        Index("recipientPersonId"),
        Index("occasionId"),
        Index("sourceGiftIdeaId"),
    ],
)
data class GiftEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val direction: GiftDirection,
    val giverPersonId: UUID?,
    val recipientPersonId: UUID?,
    val occasionId: UUID?,
    val sourceGiftIdeaId: UUID?,
    val name: String,
    val givenAt: LocalDate,
    val priceAmountMinor: Long?,
    val priceCurrencyCode: String?,
    val photoPath: String?,
    val productUrl: String?,
    val note: String?,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
