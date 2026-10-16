package app.giftiary.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A plan for a gift. Once given, it becomes a [Gift] and the idea is archived,
 * which is why there is no "given" status.
 */
data class GiftIdea(
    val id: UUID,
    val recipientPersonId: UUID,
    val occasionId: UUID?,
    val name: String,
    val status: GiftIdeaStatus,
    val estimatedPrice: Money?,
    val actualPrice: Money?,
    val photoPath: String?,
    val productUrl: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class GiftIdeaStatus {
    IDEA,
    PLANNED,
    PURCHASED,
}
