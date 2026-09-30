package app.gifty.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Someone the user gives gifts to or receives gifts from.
 *
 * A person is not an account. [linkedUserId] optionally connects them to a Gifty user,
 * and the signed-in user is never represented as a person.
 */
data class Person(
    val id: UUID,
    val name: String,
    val relationship: String?,
    val photoPath: String?,
    val linkedUserId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
