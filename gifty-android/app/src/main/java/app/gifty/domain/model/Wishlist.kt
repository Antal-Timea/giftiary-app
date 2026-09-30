package app.gifty.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A list of wishes, either the user's own ([personId] is null) or kept for someone else.
 */
data class Wishlist(
    val id: UUID,
    val personId: UUID?,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
