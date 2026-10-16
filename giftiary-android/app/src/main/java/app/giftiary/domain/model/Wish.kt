package app.giftiary.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A single wish on a [Wishlist].
 *
 * Reservations are deliberately not part of a wish: they are server-authoritative state
 * shared between invited users and must stay hidden from the wishlist owner.
 */
data class Wish(
    val id: UUID,
    val wishlistId: UUID,
    val name: String,
    val note: String?,
    val estimatedPrice: Money?,
    val productUrl: String?,
    val photoPath: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
