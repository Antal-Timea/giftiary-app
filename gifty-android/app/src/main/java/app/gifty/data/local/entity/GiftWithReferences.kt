package app.gifty.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A gift with the rows it refers to. Relations load referenced rows regardless of soft deletion,
 * which is what historical display needs, but they do not check ownership; callers must.
 */
data class GiftWithReferences(
    @Embedded val gift: GiftEntity,
    @Relation(parentColumn = "giverPersonId", entityColumn = "id")
    val giver: PersonEntity?,
    @Relation(parentColumn = "recipientPersonId", entityColumn = "id")
    val recipient: PersonEntity?,
    @Relation(parentColumn = "occasionId", entityColumn = "id")
    val occasion: OccasionEntity?,
)
