package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gifty.domain.model.OccasionType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "occasion",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        ),
    ],
    indices = [Index("ownerUserId"), Index("personId")],
)
data class OccasionEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val personId: UUID?,
    val type: OccasionType,
    val title: String?,
    val day: Int,
    val month: Int,
    val year: Int?,
    val repeatsYearly: Boolean,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
