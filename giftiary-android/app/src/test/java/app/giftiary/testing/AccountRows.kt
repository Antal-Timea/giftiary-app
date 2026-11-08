package app.giftiary.testing

import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.GiftEntity
import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.OccasionEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.PhotoTransferEntity
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncConflictEntity
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.local.entity.WishEntity
import app.giftiary.data.local.entity.WishlistEntity
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-10-25T12:00:00Z")

/** Every table the app keeps, so a test can see that none is left out. */
val ACCOUNT_TABLES = listOf("person", "occasion", "gift_idea", "gift", "wishlist", "wish", "sync_operation", "sync_conflict", "photo_transfer")

/**
 * One row of [owner] in every table, linked the way the app links them (an idea for a person's
 * birthday, the gift it became, a list for the person with a wish), each with a pending create,
 * one conflict and one pending photo upload.
 */
suspend fun GiftiaryDatabase.seedAccount(owner: UUID) {
    val person = PersonEntity(UUID.randomUUID(), owner, "Anna", null, null, null, 1, RemoteState.SYNCED, NOW, NOW, null)
    val occasion = OccasionEntity(
        UUID.randomUUID(), owner, person.id, OccasionType.BIRTHDAY, null, 12, 5, null, true, 1, RemoteState.SYNCED, NOW, NOW, null,
    )
    val idea = GiftIdeaEntity(
        UUID.randomUUID(), owner, person.id, occasion.id, "Scarf", GiftIdeaStatus.IDEA, null, null, null, null, null, null, null,
        1, RemoteState.SYNCED, NOW, NOW, null, null,
    )
    val gift = GiftEntity(
        UUID.randomUUID(), owner, GiftDirection.GIVEN, null, person.id, occasion.id, idea.id, "Book", LocalDate.of(2026, 5, 12),
        null, null, null, null, null, 1, RemoteState.SYNCED, NOW, NOW, null,
    )
    val wishlist = WishlistEntity(UUID.randomUUID(), owner, person.id, "For Anna", 1, RemoteState.SYNCED, NOW, NOW, null)
    val wish = WishEntity(UUID.randomUUID(), owner, wishlist.id, "Lamp", null, null, null, null, null, 0, RemoteState.LOCAL_ONLY, NOW, NOW, null)
    personDao().insert(person)
    occasionDao().insert(occasion)
    giftIdeaDao().insert(idea)
    giftDao().insert(gift)
    wishlistDao().insert(wishlist)
    wishDao().insert(wish)
    syncOperationDao().insert(pendingCreate(owner, SyncEntityType.WISH, wish.id))
    syncConflictDao().insert(
        SyncConflictEntity(UUID.randomUUID(), owner, SyncEntityType.PERSON, person.id, UUID.randomUUID(), 1, 2, "{}", NOW, null),
    )
    photoTransferDao().put(PhotoTransferEntity("$owner/${UUID.randomUUID()}.jpg", owner, SyncEntityType.GIFT, gift.id, PhotoTransferKind.UPLOAD, NOW, 0, null))
}

fun pendingCreate(owner: UUID?, type: SyncEntityType, entityId: UUID) = SyncOperationEntity(
    id = UUID.randomUUID(), ownerUserId = owner, entityType = type, entityId = entityId, operation = SyncOperation.CREATE,
    mutationId = UUID.randomUUID(), inFlightMutationId = null, blockedReason = null, createdAt = NOW, attemptCount = 0,
    lastAttemptAt = null, lastErrorCode = null,
)

/** How many rows [owner] has in each table; null counts the legacy queue rows without an owner. */
fun GiftiaryDatabase.rowsOf(owner: UUID?): Map<String, Int> = ACCOUNT_TABLES.associateWith { table ->
    val (where, args) = if (owner == null) "ownerUserId IS NULL" to emptyArray<Any>() else "ownerUserId = ?" to arrayOf<Any>(owner.toString())
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE $where", args).use { it.moveToFirst(); it.getInt(0) }
}
