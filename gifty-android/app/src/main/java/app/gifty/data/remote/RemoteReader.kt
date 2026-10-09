package app.gifty.data.remote

import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteSyncMutation
import java.util.UUID

/**
 * Reads the backend's rows of one captured account. Every call is made with that account's token
 * only, and every row returned belongs to it.
 *
 * All methods throw [RemoteException] on failure and never retry: a retry must be decided by
 * sync, which knows whether the account is still the one it started with.
 */
interface RemoteReader {

    /** All of the account's people, deleted ones (tombstones) included, ordered by id. */
    suspend fun fetchPeople(account: RemoteAccount): List<RemotePerson>

    /** All of the account's occasions, deleted ones included, ordered by id. */
    suspend fun fetchOccasions(account: RemoteAccount): List<RemoteOccasion>

    /** All of the account's gift ideas, archived and deleted ones included, ordered by id. */
    suspend fun fetchGiftIdeas(account: RemoteAccount): List<RemoteGiftIdea>

    /** All of the account's gifts, deleted ones included, ordered by id. */
    suspend fun fetchGifts(account: RemoteAccount): List<RemoteGift>

    /**
     * The ledger entry proving that [mutationId] committed, or null if there is none yet.
     *
     * Null is not proof that the mutation will never commit: a request carrying it may still be
     * in flight on the server.
     */
    suspend fun findMutation(account: RemoteAccount, mutationId: UUID): RemoteSyncMutation?
}
