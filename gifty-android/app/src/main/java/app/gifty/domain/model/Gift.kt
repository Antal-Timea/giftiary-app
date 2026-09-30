package app.gifty.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A gift that actually changed hands.
 *
 * A null [giverPersonId] or [recipientPersonId] stands for the signed-in user, who is not a
 * [Person]. Both may be set to record gifts between other people, e.g. Grandma to Martin.
 */
data class Gift(
    val id: UUID,
    val direction: GiftDirection,
    val giverPersonId: UUID?,
    val recipientPersonId: UUID?,
    val occasionId: UUID?,
    val sourceGiftIdeaId: UUID?,
    val name: String,
    val givenAt: LocalDate,
    val price: Money?,
    val photoPath: String?,
    val productUrl: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class GiftDirection {
    GIVEN,
    RECEIVED,
}
