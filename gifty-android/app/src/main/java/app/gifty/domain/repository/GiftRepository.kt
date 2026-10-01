package app.gifty.domain.repository

import app.gifty.domain.model.Gift
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.Money
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.Person
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * Gifts are stored on the device first; every write succeeds locally regardless of network
 * availability. Inputs are expected to be validated already.
 *
 * Gifts are history: deleting a person or occasion never deletes the gifts that refer to them.
 */
interface GiftRepository {

    /** Active gifts, newest first. */
    fun observeGifts(): Flow<List<GiftEntry>>

    /** Active gifts the person gave or received, newest first. */
    fun observeGiftsForPerson(personId: UUID): Flow<List<GiftEntry>>

    /** Emits null while the gift does not exist or after it has been deleted. */
    fun observeGift(id: UUID): Flow<GiftEntry?>

    /** Returns null if the gift does not exist or has been deleted. */
    suspend fun getGift(id: UUID): GiftEntry?

    /**
     * @throws PersonNotFoundException if a participant is not one of the user's active people.
     * @throws OccasionNotFoundException if the occasion is not one of the user's active occasions
     * for these participants.
     */
    suspend fun addGift(details: GiftDetails): Gift

    /**
     * Newly assigned people and occasions must be active; references the gift already had are
     * kept even if they have since been deleted, so old gifts stay editable.
     *
     * @throws GiftNotFoundException if the gift does not exist or has been deleted.
     * @throws PersonNotFoundException if a newly assigned participant is not available.
     * @throws OccasionNotFoundException if the occasion is not available for the participants.
     */
    suspend fun updateGift(id: UUID, details: GiftDetails)

    /** Deleting a gift that is already gone is a no-op. */
    suspend fun deleteGift(id: UUID)
}

/** The user-editable part of a [Gift]. */
data class GiftDetails(
    val direction: GiftDirection,
    val giverPersonId: UUID?,
    val recipientPersonId: UUID?,
    val occasionId: UUID?,
    val name: String,
    val givenAt: LocalDate,
    val price: Money?,
    val productUrl: String?,
    val note: String?,
)

/**
 * A gift with the people and occasion it refers to, for display. These may have been deleted
 * since; a historical gift still shows who it was for.
 */
data class GiftEntry(
    val gift: Gift,
    val giver: Person?,
    val recipient: Person?,
    val occasion: Occasion?,
)

class GiftNotFoundException(id: UUID) : Exception("Gift $id does not exist")
