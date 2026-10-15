package app.gifty.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * An occasion with its person. The relation loads the person regardless of soft deletion and
 * ownership; the query that returns these must check both.
 */
data class OccasionWithPerson(
    @Embedded val occasion: OccasionEntity,
    @Relation(parentColumn = "personId", entityColumn = "id")
    val person: PersonEntity?,
)
