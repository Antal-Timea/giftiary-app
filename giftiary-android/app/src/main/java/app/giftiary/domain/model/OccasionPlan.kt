package app.giftiary.domain.model

/**
 * What is being planned for an occasion: its open gift ideas, and how many of them are already
 * bought. It says what exists, not whether planning is done; gifts already given are history and
 * are not counted, since they may be from an earlier year.
 */
data class OccasionPlan(
    val ideas: Int,
    val bought: Int,
)
