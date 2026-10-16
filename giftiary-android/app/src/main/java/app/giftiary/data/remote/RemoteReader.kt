package app.giftiary.data.remote

import app.giftiary.data.remote.dto.RemoteGift
import app.giftiary.data.remote.dto.RemoteGiftIdea
import app.giftiary.data.remote.dto.RemoteOccasion
import app.giftiary.data.remote.dto.RemotePerson
import app.giftiary.data.remote.dto.RemoteSyncMutation
import app.giftiary.data.remote.dto.RemoteWish
import app.giftiary.data.remote.dto.RemoteWishlist
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

    /** All of the account's wishlists, deleted ones included, ordered by id. */
    suspend fun fetchWishlists(account: RemoteAccount): List<RemoteWishlist>

    /** All of the account's wishes, deleted ones included, ordered by id. */
    suspend fun fetchWishes(account: RemoteAccount): List<RemoteWish>

    /** The account's person [id] as the backend has it now, tombstoned or not; null if it has none visible. */
    suspend fun findPerson(account: RemoteAccount, id: UUID): RemotePerson?

    /** The account's occasion [id] as the backend has it now; null if it has none visible. */
    suspend fun findOccasion(account: RemoteAccount, id: UUID): RemoteOccasion?

    /** The account's gift idea [id] as the backend has it now; null if it has none visible. */
    suspend fun findGiftIdea(account: RemoteAccount, id: UUID): RemoteGiftIdea?

    /** The account's gift [id] as the backend has it now; null if it has none visible. */
    suspend fun findGift(account: RemoteAccount, id: UUID): RemoteGift?

    /** The account's wishlist [id] as the backend has it now; null if it has none visible. */
    suspend fun findWishlist(account: RemoteAccount, id: UUID): RemoteWishlist?

    /** The account's wish [id] as the backend has it now; null if it has none visible. */
    suspend fun findWish(account: RemoteAccount, id: UUID): RemoteWish?

    /**
     * The account's gift made from idea [giftIdeaId], tombstoned or not; null if it has none
     * visible. The backend allows one per idea, so anything else is an error.
     */
    suspend fun findGiftBySourceIdea(account: RemoteAccount, giftIdeaId: UUID): RemoteGift?

    /**
     * The ledger entry proving that [mutationId] committed, or null if there is none yet.
     *
     * Null is not proof that the mutation will never commit: a request carrying it may still be
     * in flight on the server.
     */
    suspend fun findMutation(account: RemoteAccount, mutationId: UUID): RemoteSyncMutation?
}
