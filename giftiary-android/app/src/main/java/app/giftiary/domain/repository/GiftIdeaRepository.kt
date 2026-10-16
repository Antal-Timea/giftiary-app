package app.giftiary.domain.repository

import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * Gift ideas are plans, stored on the device first. An idea that has been given is archived and
 * leaves the active lists; the resulting [Gift] keeps a reference to it. An idea that a gift, even
 * a deleted one, was made from counts as given too, also if syncing with another device reopened
 * it.
 *
 * Deleting a person or occasion never deletes the ideas that refer to them.
 */
interface GiftIdeaRepository {

    /** Active ideas (neither given nor deleted), most recently changed first. */
    fun observeGiftIdeas(): Flow<List<GiftIdeaEntry>>

    /** Active ideas for the person, most recently changed first. */
    fun observeGiftIdeasForPerson(personId: UUID): Flow<List<GiftIdeaEntry>>

    /** Emits null while the idea is not active: missing, deleted or already given. */
    fun observeGiftIdea(id: UUID): Flow<GiftIdeaEntry?>

    /** Returns null if the idea is not active: missing, deleted or already given. */
    suspend fun getGiftIdea(id: UUID): GiftIdeaEntry?

    /**
     * @throws PersonNotFoundException if the recipient is not one of the user's active people.
     * @throws OccasionNotFoundException if the occasion is not one of the recipient's active occasions.
     */
    suspend fun addGiftIdea(details: GiftIdeaDetails): GiftIdea

    /**
     * Newly assigned people and occasions must be active; references the idea already had are
     * kept even if they have since been deleted.
     *
     * @throws GiftIdeaNotFoundException if the idea is not active.
     * @throws PersonNotFoundException if a newly assigned recipient is not available.
     * @throws OccasionNotFoundException if the occasion is not available for the recipient.
     */
    suspend fun updateGiftIdea(id: UUID, details: GiftIdeaDetails)

    /** @throws GiftIdeaNotFoundException if the idea is not active. */
    suspend fun updateStatus(id: UUID, status: GiftIdeaStatus)

    /** Deleting an idea that is already gone is a no-op. */
    suspend fun deleteGiftIdea(id: UUID)

    /**
     * Records the idea as an actual gift the user gave and archives the idea, all at once.
     * The gift copies the idea's details and refers back to it.
     *
     * @throws GiftIdeaNotFoundException if the idea is missing or deleted.
     * @throws GiftIdeaAlreadyGivenException if the idea has already been given.
     */
    suspend fun markAsGiven(id: UUID, givenAt: LocalDate, price: Money?): Gift
}

/** The user-editable part of a [GiftIdea]. */
data class GiftIdeaDetails(
    val recipientPersonId: UUID,
    val occasionId: UUID?,
    val name: String,
    val status: GiftIdeaStatus,
    val estimatedPrice: Money?,
    val actualPrice: Money?,
    val productUrl: String?,
    val note: String?,
)

/**
 * An idea with the person and occasion it refers to, for display. These may have been deleted
 * since; an idea still shows who it was meant for.
 */
data class GiftIdeaEntry(
    val idea: GiftIdea,
    val recipient: Person?,
    val occasion: Occasion?,
)

class GiftIdeaNotFoundException(id: UUID) : Exception("Gift idea $id does not exist")

class GiftIdeaAlreadyGivenException(id: UUID) : Exception("Gift idea $id has already been given")
