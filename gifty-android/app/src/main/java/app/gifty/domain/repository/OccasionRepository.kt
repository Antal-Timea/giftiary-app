package app.gifty.domain.repository

import app.gifty.domain.model.Occasion
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Occasions are stored on the device first; every write succeeds locally regardless of network
 * availability. Inputs are expected to be validated already.
 */
interface OccasionRepository {

    /** The person's active occasions in calendar order (month, day). */
    fun observeOccasionsForPerson(personId: UUID): Flow<List<Occasion>>

    /** All active occasions with their people; an occasion whose person was deleted is left out. */
    fun observeOccasions(): Flow<List<OccasionEntry>>

    /** The same occasions as [observeOccasions], read once for the current account; null without one. */
    suspend fun getOccasionSnapshot(): OccasionSnapshot?

    /** Returns null if the occasion does not exist or has been deleted. */
    suspend fun getOccasion(id: UUID): Occasion?

    /** @throws PersonNotFoundException if [personId] is not one of the user's active people. */
    suspend fun addOccasion(personId: UUID?, details: OccasionDetails): Occasion

    /**
     * @throws OccasionNotFoundException if the occasion does not exist or has been deleted.
     * @throws PersonNotFoundException if its person is no longer one of the user's active people.
     */
    suspend fun updateOccasion(id: UUID, details: OccasionDetails)

    /** Deleting an occasion that is already gone is a no-op. */
    suspend fun deleteOccasion(id: UUID)
}

/** The user-editable part of an [Occasion]. */
data class OccasionDetails(
    val type: OccasionType,
    val title: String?,
    val day: Int,
    val month: Int,
    val year: Int?,
    val repeatsYearly: Boolean,
)

data class OccasionEntry(
    val occasion: Occasion,
    /** Null for an occasion of the user's own, such as Christmas. */
    val person: Person?,
)

/** Occasions read for one account; [ownerId] says whose, so a caller can tell if the account changed since. */
data class OccasionSnapshot(
    val ownerId: UUID,
    val entries: List<OccasionEntry>,
)

class OccasionNotFoundException(id: UUID) : Exception("Occasion $id does not exist")
