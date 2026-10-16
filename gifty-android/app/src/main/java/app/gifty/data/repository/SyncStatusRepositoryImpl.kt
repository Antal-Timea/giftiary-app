package app.gifty.data.repository

import androidx.room.withTransaction
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncConflictDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.dao.WishDao
import app.gifty.data.local.dao.WishlistDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.sync.BlockReason
import app.gifty.data.user.ActiveOwner
import app.gifty.data.user.scoped
import app.gifty.domain.model.AttentionItem
import app.gifty.domain.model.AttentionKind
import app.gifty.domain.model.SyncItem
import app.gifty.domain.model.SyncItemType
import app.gifty.domain.model.SyncStatus
import app.gifty.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class SyncStatusRepositoryImpl @Inject constructor(
    private val database: GiftyDatabase,
    private val operations: SyncOperationDao,
    private val conflicts: SyncConflictDao,
    private val people: PersonDao,
    private val occasions: OccasionDao,
    private val giftIdeas: GiftIdeaDao,
    private val gifts: GiftDao,
    private val wishlists: WishlistDao,
    private val wishes: WishDao,
    private val activeOwner: ActiveOwner,
    private val clock: Clock,
) : SyncStatusRepository {

    override fun observeSyncStatus(): Flow<SyncStatus> = activeOwner.scoped(SyncStatus(0, emptyList())) { owner ->
        // An item's name, and whether it can be opened, may change while it still needs attention,
        // so the status is read again whenever any table behind it changes, not just the queue.
        database.invalidationTracker.createFlow(*TABLES)
            .map { read(owner) }
            .distinctUntilChanged()
    }

    override suspend fun dismissConflict(id: UUID) {
        val ownerUserId = activeOwner.requireOwnerId()
        conflicts.markSeen(id, ownerUserId, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS))
    }

    /** One consistent read of everything the status shows. */
    private suspend fun read(owner: UUID): SyncStatus = database.withTransaction {
        val queue = operations.getAllForOwner(owner).filter { it.entityType in SHOWN_TYPES }
        // A legacy REMOTE_NEWER block is settled by the next push, so it is still waiting.
        val (blocked, waiting) = queue.partition {
            it.blockedReason != null && it.blockedReason != BlockReason.REMOTE_NEWER.name
        }
        val conflicted = conflicts.getUnseen(owner).filter { it.entityType in SHOWN_TYPES }.map { conflict ->
            // Only a create can have version 0 as its base: the duplicate of a gift given elsewhere.
            val kind = if (conflict.baseVersion == 0L) AttentionKind.DUPLICATE_GIFT_REMOVED else AttentionKind.REPLACED_BY_OTHER_DEVICE
            val (item, openable) = lookUp(conflict.entityType, conflict.entityId, owner)
            AttentionItem(conflict.id, kind, item, canOpen = kind == AttentionKind.REPLACED_BY_OTHER_DEVICE && openable)
        }
        val stuck = blocked.map { operation ->
            val kind = when (operation.blockedReason) {
                BlockReason.REMOTE_MISSING.name, BlockReason.REMOTE_ID_TAKEN.name -> AttentionKind.UNAVAILABLE
                else -> AttentionKind.REFUSED
            }
            val (item, openable) = lookUp(operation.entityType, operation.entityId, owner)
            // Editing refused values may fix them; nothing the user does here makes an unavailable row reachable.
            AttentionItem(operation.id, kind, item, canOpen = kind == AttentionKind.REFUSED && openable)
        }
        SyncStatus(waitingCount = waiting.size, attention = conflicted + stuck)
    }

    /** The item as Room has it now, and whether its usual screen can show it. */
    private suspend fun lookUp(type: SyncEntityType, id: UUID, owner: UUID): Pair<SyncItem, Boolean> = when (type) {
        SyncEntityType.PERSON -> people.findById(id, owner).let {
            SyncItem(SyncItemType.PERSON, id, it?.name) to (it != null && it.deletedAt == null)
        }
        SyncEntityType.OCCASION -> occasions.findById(id, owner).let {
            SyncItem(SyncItemType.OCCASION, id, it?.title, it?.type) to (it != null && it.deletedAt == null)
        }
        // Given ideas, archived or not, are not shown by the idea screens.
        SyncEntityType.GIFT_IDEA -> giftIdeas.findById(id, owner).let {
            SyncItem(SyncItemType.GIFT_IDEA, id, it?.name) to (giftIdeas.findActiveById(id, owner) != null)
        }
        SyncEntityType.GIFT -> gifts.findById(id, owner).let {
            SyncItem(SyncItemType.GIFT, id, it?.name) to (it != null && it.deletedAt == null)
        }
        SyncEntityType.WISHLIST -> wishlists.findById(id, owner).let {
            SyncItem(SyncItemType.WISHLIST, id, it?.name) to (it != null && it.deletedAt == null)
        }
        SyncEntityType.WISH -> wishes.findById(id, owner).let {
            SyncItem(SyncItemType.WISH, id, it?.name) to (it != null && it.deletedAt == null)
        }
    }

    private companion object {
        val SHOWN_TYPES = SyncEntityType.entries.toSet()
        val TABLES = arrayOf("sync_operation", "sync_conflict", "person", "occasion", "gift_idea", "gift", "wishlist", "wish")
    }
}
