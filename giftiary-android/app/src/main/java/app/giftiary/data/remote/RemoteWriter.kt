package app.giftiary.data.remote

import app.giftiary.data.remote.dto.RemoteGiftFields
import app.giftiary.data.remote.dto.RemoteGiftIdeaFields
import app.giftiary.data.remote.dto.RemoteOccasionFields
import app.giftiary.data.remote.dto.RemotePersonFields
import app.giftiary.data.remote.dto.RemoteWishFields
import app.giftiary.data.remote.dto.RemoteWishlistFields
import java.util.UUID

/**
 * Writes rows of one captured account, each write identified by a mutation id the caller
 * supplies. The writer never creates mutation ids and never retries: a retry must reuse the same
 * mutation id, and only sync can decide whether one is due.
 *
 * Inserts create a row with the caller's id and the captured account as owner. Updates are
 * compare-and-set: they replace the row's whole client-decided state only if it is still at
 * [expectedVersion]. Deleting is an update that sets the tombstone; there is no hard delete.
 *
 * An update's expectedVersion must be a real backend version (1 or more); anything else throws
 * IllegalArgumentException before any request is made.
 *
 * Failures are [RemoteException]s. [RemoteFailure.NETWORK] after a write proves nothing: the
 * mutation may have committed or may still commit, which only the mutation ledger can tell.
 */
interface RemoteWriter {

    suspend fun insertPerson(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemotePersonFields): Committed

    suspend fun insertOccasion(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteOccasionFields): Committed

    suspend fun insertGiftIdea(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftIdeaFields): Committed

    suspend fun insertGift(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftFields): Committed

    suspend fun insertWishlist(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteWishlistFields): Committed

    suspend fun insertWish(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteWishFields): Committed

    suspend fun updatePerson(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemotePersonFields,
    ): UpdateOutcome

    suspend fun updateOccasion(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteOccasionFields,
    ): UpdateOutcome

    suspend fun updateGiftIdea(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteGiftIdeaFields,
    ): UpdateOutcome

    suspend fun updateGift(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteGiftFields,
    ): UpdateOutcome

    suspend fun updateWishlist(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteWishlistFields,
    ): UpdateOutcome

    suspend fun updateWish(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteWishFields,
    ): UpdateOutcome
}

sealed interface UpdateOutcome

/** The server returned the written row carrying this write's mutation id, at the [version] its trigger set. */
data class Committed(val version: Long) : UpdateOutcome

/**
 * The update matched no row: the row is not at the expected version any more, does not exist, or
 * is not visible to the account. Which of these it was is not known from this response alone.
 */
data object NoMatch : UpdateOutcome
