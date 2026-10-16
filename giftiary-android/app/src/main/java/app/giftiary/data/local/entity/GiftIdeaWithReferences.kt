package app.giftiary.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A gift idea with the rows it refers to. Relations load referenced rows regardless of soft
 * deletion, which is what display needs, but they do not check ownership; callers must.
 */
data class GiftIdeaWithReferences(
    @Embedded val idea: GiftIdeaEntity,
    @Relation(parentColumn = "recipientPersonId", entityColumn = "id")
    val recipient: PersonEntity?,
    @Relation(parentColumn = "occasionId", entityColumn = "id")
    val occasion: OccasionEntity?,
)
