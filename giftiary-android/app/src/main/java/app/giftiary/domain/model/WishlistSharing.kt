package app.giftiary.domain.model

import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * The secret of a wishlist invite. Whoever holds it can join the list, so it lives only in memory:
 * never in Room, preferences, saved state or logs, and [toString] never shows it.
 */
class InviteToken private constructor(val value: String) {

    override fun equals(other: Any?): Boolean = other is InviteToken && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "InviteToken(redacted)"

    companion object {

        /** 32 random bytes, base64url without padding, as the server issues them. */
        val FORMAT = Regex("[A-Za-z0-9_-]{43}")

        fun of(value: String): InviteToken? = if (FORMAT.matches(value)) InviteToken(value) else null
    }
}

/** A just-created invite. Its token is shown once: the server keeps only a hash of it. */
data class CreatedInvite(val id: UUID, val token: InviteToken, val expiresAt: Instant)

/** Who can open an own wishlist: open invites and members. Nothing about reservations. */
data class WishlistAccess(val invites: List<ActiveInvite>, val members: List<WishlistMember>)

data class ActiveInvite(val id: UUID, val createdAt: Instant, val expiresAt: Instant)

/** [displayName] is null when the member has none; it is never replaced by their email. */
data class WishlistMember(val userId: UUID, val displayName: String?, val joinedAt: Instant)

/**
 * A wishlist someone else shared with the user. [personName] names who a list kept for a person is
 * for; null for the owner's own lists.
 */
data class SharedWishlistSummary(val id: UUID, val name: String, val ownerDisplayName: String?, val personName: String? = null)

/** [personName] names who a list kept for a person is for; null for the owner's own lists. */
data class SharedWishlist(
    val id: UUID,
    val name: String,
    val ownerDisplayName: String?,
    val wishes: List<SharedWish>,
    val personName: String? = null,
)

/**
 * A wish of a shared list. [reservation] is read for the reservations still to come; null when
 * the server reports a state this version does not know.
 */
data class SharedWish(
    val id: UUID,
    val name: String,
    val note: String?,
    val estimatedPrice: Money?,
    val productUrl: String?,
    val reservation: ReservationState?,
)

enum class ReservationState { AVAILABLE, RESERVED_BY_ME, RESERVED_BY_OTHER }

/**
 * An invite of an own wishlist to an email address. [state] is all the owner learns: pending, or
 * not accepted (declined and expired alike), never whether the address has an account.
 */
data class EmailInvite(
    val id: UUID,
    val email: String,
    val state: EmailInviteState,
    val createdAt: Instant,
    val expiresAt: Instant,
)

enum class EmailInviteState { PENDING, NOT_ACCEPTED }

// The server's own check (create_recipient_invite), so what passes here is not refused as invalid.
private val EMAIL_SHAPE = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")

/** The address as the server keeps it, trimmed and lowercased, or null if it is not one. */
fun normalizedEmail(input: String): String? =
    input.trim().lowercase(Locale.ROOT).takeIf { it.length <= 254 && EMAIL_SHAPE.matches(it) }
