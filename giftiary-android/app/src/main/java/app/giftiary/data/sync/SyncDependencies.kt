package app.giftiary.data.sync

import app.giftiary.data.local.dao.GiftDao
import app.giftiary.data.local.dao.GiftIdeaDao
import app.giftiary.data.local.dao.OccasionDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.dao.WishDao
import app.giftiary.data.local.dao.WishlistDao
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import java.util.UUID
import javax.inject.Inject

/**
 * The backend's foreign keys, as the runner needs them: which rows an entity's write requires to
 * exist remotely already.
 *
 * Person ← Occasion (person, optional); Person and Occasion ← GiftIdea (recipient, required;
 * occasion, optional); Person, Occasion and GiftIdea ← Gift (giver, recipient, occasion and source
 * idea, all optional); Person ← Wishlist (person, optional); Wishlist ← Wish (required). The graph
 * is acyclic, so Person → Occasion → GiftIdea → Gift → Wishlist → Wish is a valid order.
 */
class SyncDependencies @Inject constructor(
    private val people: PersonDao,
    private val occasions: OccasionDao,
    private val giftIdeas: GiftIdeaDao,
    private val gifts: GiftDao,
    private val wishlists: WishlistDao,
    private val wishes: WishDao,
) {

    /**
     * The parents the entity currently references that the backend is not known to have yet; it
     * may be pushed when this is empty. Read from the entity as it is now, since any send carries
     * the current content.
     *
     * A parent is known remotely when it is SYNCED: only a proven commit or a backend row makes it
     * so, and rows are never hard-deleted, so tombstones and parents with pending changes count.
     * A LOCAL_ONLY parent never does, even with a create in flight that may have committed: that
     * is not proven until the parent is settled. A missing entity has no parents here; pushing it
     * then reports what is wrong.
     */
    suspend fun unknownParents(ownerUserId: UUID, type: SyncEntityType, id: UUID): List<EntityRef> =
        parentsOf(ownerUserId, type, id).filterNot { isKnownRemotely(ownerUserId, it) }

    private suspend fun parentsOf(ownerUserId: UUID, type: SyncEntityType, id: UUID): List<EntityRef> = when (type) {
        SyncEntityType.PERSON -> emptyList()
        SyncEntityType.OCCASION -> occasions.findById(id, ownerUserId)?.let { listOfNotNull(it.personId?.let(::person)) }
        SyncEntityType.GIFT_IDEA -> giftIdeas.findById(id, ownerUserId)?.let {
            listOfNotNull(person(it.recipientPersonId), it.occasionId?.let(::occasion))
        }
        SyncEntityType.GIFT -> gifts.findById(id, ownerUserId)?.let {
            listOfNotNull(
                it.giverPersonId?.let(::person),
                it.recipientPersonId?.let(::person),
                it.occasionId?.let(::occasion),
                it.sourceGiftIdeaId?.let { idea -> EntityRef(SyncEntityType.GIFT_IDEA, idea) },
            )
        }
        SyncEntityType.WISHLIST -> wishlists.findById(id, ownerUserId)?.let { listOfNotNull(it.personId?.let(::person)) }
        SyncEntityType.WISH -> wishes.findById(id, ownerUserId)?.let { listOf(EntityRef(SyncEntityType.WISHLIST, it.wishlistId)) }
    }.orEmpty()

    private suspend fun isKnownRemotely(ownerUserId: UUID, parent: EntityRef): Boolean {
        val (remoteState, version) = when (parent.type) {
            SyncEntityType.PERSON -> people.findById(parent.id, ownerUserId)?.let { it.remoteState to it.version }
            SyncEntityType.OCCASION -> occasions.findById(parent.id, ownerUserId)?.let { it.remoteState to it.version }
            SyncEntityType.GIFT_IDEA -> giftIdeas.findById(parent.id, ownerUserId)?.let { it.remoteState to it.version }
            SyncEntityType.WISHLIST -> wishlists.findById(parent.id, ownerUserId)?.let { it.remoteState to it.version }
            else -> throw IllegalArgumentException("${parent.type} is never referenced")
        } ?: return false
        return remoteState == RemoteState.SYNCED && version >= 1
    }

    private fun person(id: UUID) = EntityRef(SyncEntityType.PERSON, id)

    private fun occasion(id: UUID) = EntityRef(SyncEntityType.OCCASION, id)
}

data class EntityRef(val type: SyncEntityType, val id: UUID)
