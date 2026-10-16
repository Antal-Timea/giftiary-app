package app.gifty.data.sync

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.dao.WishDao
import app.gifty.data.local.dao.WishlistDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteException
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteReader
import app.gifty.data.remote.RemoteRowValidator
import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteWish
import app.gifty.data.remote.dto.RemoteWishlist
import app.gifty.data.remote.toEntity
import app.gifty.data.user.ActiveOwner
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings Room up to the backend for one account: every row of the account is read, checked, and
 * applied in one transaction, or nothing is. There is no cursor; the next pull reads everything
 * again, and since rows are compared by version, repeating a pull, or one cut short by process
 * death, changes nothing more.
 *
 * Local changes always win here: an entity with any queued operation is left alone, whatever the
 * operation's state, and it is the push that settles it, on its own evidence. Otherwise a row is
 * written only if the backend has a newer version than Room, and only through the DAOs, so pulling
 * never queues anything. Rows the backend does not have are never deleted, and only a backend
 * tombstone deletes.
 */
@Singleton
class SyncPuller @Inject constructor(
    private val database: GiftyDatabase,
    private val reader: RemoteReader,
    private val operations: SyncOperationDao,
    private val people: PersonDao,
    private val occasions: OccasionDao,
    private val giftIdeas: GiftIdeaDao,
    private val gifts: GiftDao,
    private val wishlists: WishlistDao,
    private val wishes: WishDao,
    private val activeOwner: ActiveOwner,
) {

    suspend fun pull(account: RemoteAccount): PullResult {
        val owner = account.ownerUserId
        return try {
            val remote = fetch(account)
            if (activeOwner.ownerId.value != owner) return PullResult.OwnerChanged
            database.withTransaction {
                // Checked again where it cannot interleave with a write. A switch during the
                // transaction cannot mix accounts either: every row written belongs to this one.
                if (activeOwner.ownerId.value != owner) PullResult.OwnerChanged else apply(owner, remote)
            }
        } catch (e: RemoteException) {
            PullResult.Failed(e.failure)
        }
    }

    /**
     * Reads every table, then checks every row, before anything is written.
     *
     * The tables are read one after another, not as one snapshot, so children come first: any
     * row a child refers to existed when the child was written and, since rows are never removed
     * and never change owner, is still there when its own table is read later.
     */
    private suspend fun fetch(account: RemoteAccount): Snapshot {
        val wishes = reader.fetchWishes(account)
        val wishlists = reader.fetchWishlists(account)
        val gifts = reader.fetchGifts(account)
        val giftIdeas = reader.fetchGiftIdeas(account)
        val occasions = reader.fetchOccasions(account)
        val people = reader.fetchPeople(account)
        people.forEach(RemoteRowValidator::validate)
        occasions.forEach(RemoteRowValidator::validate)
        giftIdeas.forEach(RemoteRowValidator::validate)
        gifts.forEach(RemoteRowValidator::validate)
        wishlists.forEach(RemoteRowValidator::validate)
        wishes.forEach(RemoteRowValidator::validate)
        return Snapshot(people, occasions, giftIdeas, gifts, wishlists, wishes)
    }

    /**
     * Decides every row and checks the outcome before the first write, then writes parents before
     * children.
     *
     * Room's foreign keys are deferred, so a missing parent would only fail the commit, and on
     * Android a failed commit leaves the transaction open instead of rolling it back. Every
     * reference of a row about to be written is therefore checked here, against Room as it will be
     * after this pull: one that would point at nothing fails the pull before anything is written.
     */
    private suspend fun apply(owner: UUID, remote: Snapshot): PullResult.Applied {
        val personPlan = remote.people.map { person ->
            val local = people.findByIdForAnyOwner(person.id)
            plan(owner, SyncEntityType.PERSON, person, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
        }
        val peopleAfter = personPlan.associate { it.row.id to it.kept }

        // The person of each occasion Room will have after this pull.
        val occasionPersons = HashMap<UUID, UUID?>()
        val occasionPlan = remote.occasions.map { occasion ->
            val local = occasions.findByIdForAnyOwner(occasion.id)
            val planned = plan(owner, SyncEntityType.OCCASION, occasion, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
            if (local != null) {
                RemoteRowValidator.requireSamePerson(occasion, local.personId)
                occasionPersons[occasion.id] = local.personId
            } else if (planned.kept) {
                occasionPersons[occasion.id] = occasion.personId
            }
            if (planned.writes) requirePerson(owner, occasion.personId, peopleAfter)
            planned
        }

        val ideaPlan = remote.giftIdeas.map { idea ->
            val local = giftIdeas.findByIdForAnyOwner(idea.id)
            val planned = plan(owner, SyncEntityType.GIFT_IDEA, idea, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
            if (planned.writes) {
                requirePerson(owner, idea.recipientPersonId, peopleAfter)
                idea.occasionId?.let { RemoteRowValidator.requireOccasionFits(idea, occasionPerson(owner, it, occasionPersons)) }
            }
            planned
        }
        val ideasAfter = ideaPlan.associate { it.row.id to it.kept }

        val giftPlan = remote.gifts.map { gift ->
            val local = gifts.findByIdForAnyOwner(gift.id)
            val planned = plan(owner, SyncEntityType.GIFT, gift, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
            if (planned.writes) {
                requirePerson(owner, gift.giverPersonId, peopleAfter)
                requirePerson(owner, gift.recipientPersonId, peopleAfter)
                gift.occasionId?.let { RemoteRowValidator.requireOccasionFits(gift, occasionPerson(owner, it, occasionPersons)) }
                gift.sourceGiftIdeaId?.let { if (!(ideasAfter[it] ?: (giftIdeas.findById(it, owner) != null))) throw unusable() }
            }
            planned
        }

        val wishlistPlan = remote.wishlists.map { wishlist ->
            val local = wishlists.findByIdForAnyOwner(wishlist.id)
            val planned = plan(owner, SyncEntityType.WISHLIST, wishlist, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
            if (local != null) RemoteRowValidator.requireSamePerson(wishlist, local.personId)
            if (planned.writes) requirePerson(owner, wishlist.personId, peopleAfter)
            planned
        }
        val wishlistsAfter = wishlistPlan.associate { it.row.id to it.kept }

        val wishPlan = remote.wishes.map { wish ->
            val local = wishes.findByIdForAnyOwner(wish.id)
            val planned = plan(owner, SyncEntityType.WISH, wish, local?.let { Local(it.ownerUserId, it.version, it.remoteState) })
            if (local != null) RemoteRowValidator.requireSameWishlist(wish, local.wishlistId)
            // Room's same-owner key needs the wishlist, this owner's, before the wish is written.
            if (planned.writes && !(wishlistsAfter[wish.wishlistId] ?: (wishlists.findById(wish.wishlistId, owner) != null))) {
                throw unusable()
            }
            planned
        }

        personPlan.forEach { write(it.decision, { people.insert(it.row.toEntity()) }, { people.update(it.row.toEntity()) }) }
        occasionPlan.forEach { write(it.decision, { occasions.insert(it.row.toEntity()) }, { occasions.update(it.row.toEntity()) }) }
        ideaPlan.forEach { write(it.decision, { giftIdeas.insert(it.row.toEntity()) }, { giftIdeas.update(it.row.toEntity()) }) }
        giftPlan.forEach { write(it.decision, { gifts.insert(it.row.toEntity()) }, { gifts.update(it.row.toEntity()) }) }
        wishlistPlan.forEach { write(it.decision, { wishlists.insert(it.row.toEntity()) }, { wishlists.update(it.row.toEntity()) }) }
        wishPlan.forEach { write(it.decision, { wishes.insert(it.row.toEntity()) }, { wishes.update(it.row.toEntity()) }) }

        val decisions = (personPlan + occasionPlan + ideaPlan + giftPlan + wishlistPlan + wishPlan).groupingBy { it.decision }.eachCount()
        return PullResult.Applied(
            inserted = decisions[Decision.INSERT] ?: 0,
            updated = decisions[Decision.UPDATE] ?: 0,
            unchanged = decisions[Decision.UNCHANGED] ?: 0,
            skippedQueued = decisions[Decision.QUEUED] ?: 0,
            skippedStale = decisions[Decision.STALE] ?: 0,
            skippedLocalOnly = decisions[Decision.LOCAL_ONLY] ?: 0,
        )
    }

    private suspend fun <R : RemoteRow> plan(owner: UUID, type: SyncEntityType, row: R, local: Local?): Planned<R> {
        val decision = decide(owner, type, row, local)
        return Planned(row, decision, kept = local != null || decision == Decision.INSERT)
    }

    private suspend fun decide(owner: UUID, type: SyncEntityType, row: RemoteRow, local: Local?): Decision {
        // Room ids are not per account. Another account's row with this id is neither "none" nor
        // something to overwrite; ids are random, so this means the local data is wrong.
        check(local == null || local.ownerUserId == owner) { "A local $type has a pulled id but another owner" }
        // Owner-agnostic on purpose: any queued operation for the entity, even an unclaimed
        // legacy one, is local intent the backend has not seen.
        if (operations.findForEntity(type, row.id) != null) return Decision.QUEUED
        return when {
            local == null -> Decision.INSERT
            // Known only locally with nothing queued, e.g. a gift created and deleted before it was
            // ever pushed: a backend copy must not bring it back.
            local.remoteState == RemoteState.LOCAL_ONLY || local.version == 0L -> Decision.LOCAL_ONLY
            row.version > local.version -> Decision.UPDATE
            row.version == local.version -> Decision.UNCHANGED
            // Room got ahead after the backend was read, e.g. a push settled meanwhile.
            else -> Decision.STALE
        }
    }

    /** The person of [occasionId] as Room will hold it; an occasion Room will not have fails the pull. */
    private suspend fun occasionPerson(owner: UUID, occasionId: UUID, after: Map<UUID, UUID?>): UUID? {
        if (after.containsKey(occasionId)) return after[occasionId]
        return (occasions.findById(occasionId, owner) ?: throw unusable()).personId
    }

    /** Fails the pull unless person [id], if any, is one Room will have after it. */
    private suspend fun requirePerson(owner: UUID, id: UUID?, after: Map<UUID, Boolean>) {
        if (id != null && !(after[id] ?: (people.findById(id, owner) != null))) throw unusable()
    }

    private fun unusable() = RemoteException(RemoteFailure.SERVER)

    private suspend fun write(decision: Decision, insert: suspend () -> Unit, update: suspend () -> Int) {
        when (decision) {
            Decision.INSERT -> insert()
            Decision.UPDATE -> check(update() == 1) { "A pulled row was not updated" }
            else -> Unit
        }
    }

    private class Snapshot(
        val people: List<RemotePerson>,
        val occasions: List<RemoteOccasion>,
        val giftIdeas: List<RemoteGiftIdea>,
        val gifts: List<RemoteGift>,
        val wishlists: List<RemoteWishlist>,
        val wishes: List<RemoteWish>,
    )

    private class Local(val ownerUserId: UUID, val version: Long, val remoteState: RemoteState)

    /** A row and what happens to it; [kept] says whether Room has it after the pull. */
    private class Planned<R : RemoteRow>(val row: R, val decision: Decision, val kept: Boolean) {
        val writes get() = decision == Decision.INSERT || decision == Decision.UPDATE
    }

    private enum class Decision { INSERT, UPDATE, UNCHANGED, QUEUED, STALE, LOCAL_ONLY }
}
