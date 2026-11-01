package app.giftiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncConflictEntity
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.sync.BlockReason
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.AttentionItem
import app.giftiary.domain.model.AttentionKind
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.SyncItem
import app.giftiary.domain.model.SyncItemType
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.WishDetails
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.MutableClock
import app.giftiary.testing.blockCurrentMutation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The local sync status as built from real Room, queue and repositories. Blocks and conflicts are
 * written the way sync leaves them.
 */
@RunWith(RobolectricTestRunner::class)
class SyncStatusRepositoryImplTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-14T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(alice)
    private lateinit var database: GiftiaryDatabase
    private lateinit var repository: SyncStatusRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var wishlists: WishlistRepositoryImpl

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {})
        occasions = OccasionRepositoryImpl(database, database.occasionDao(), database.personDao(), queue, activeOwner, clock, SyncRequester {})
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner,
            clock, SyncRequester {},
        )
        gifts = GiftRepositoryImpl(database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {}, database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()))
        wishlists = WishlistRepositoryImpl(
            database, database.wishlistDao(), database.wishDao(), database.personDao(), queue, activeOwner, clock, SyncRequester {},
        )
        repository = SyncStatusRepositoryImpl(
            database, database.syncOperationDao(), database.syncConflictDao(), database.personDao(), database.occasionDao(),
            database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao(), activeOwner, clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing local waiting is an empty status, which claims nothing about the backend`() = runTest {
        assertEquals(SyncStatus(waitingCount = 0, attention = emptyList()), status())
    }

    @Test
    fun `changes still expected to sync are waiting, in flight and legacy conflicts included, and never also attention`() = runTest {
        later { people.addPerson("Anna", null) }
        val inFlight = later { people.addPerson("Béla", null) }.id
        pending(SyncEntityType.PERSON, inFlight).let { operations.markInFlight(it.id, it.mutationId) }
        // Blocked by an earlier version of the app: the next push settles it.
        val legacy = later { people.addPerson("Cecília", null) }.id
        block(SyncEntityType.PERSON, legacy, BlockReason.REMOTE_NEWER)
        val refused = later { people.addPerson("Dénes", null) }.id
        block(SyncEntityType.PERSON, refused, BlockReason.REJECTED)

        val status = status()

        assertEquals(3, status.waitingCount)
        assertEquals(listOf(refused), status.attention.map { it.item.id })
    }

    @Test
    fun `blocked changes are attention by kind, and only refused active items can be opened`() = runTest {
        val refused = later { people.addPerson("Anna", null) }.id
        block(SyncEntityType.PERSON, refused, BlockReason.REJECTED)
        val missing = later { people.addPerson("Béla", null) }.id
        block(SyncEntityType.PERSON, missing, BlockReason.REMOTE_MISSING)
        val taken = later { people.addPerson("Cecília", null) }.id
        block(SyncEntityType.PERSON, taken, BlockReason.REMOTE_ID_TAKEN)
        // A refused deletion: the person is gone from every screen, so there is nothing to open.
        val deleted = later { syncedPerson("Dénes") }
        later { people.deletePerson(deleted) }
        block(SyncEntityType.PERSON, deleted, BlockReason.REJECTED)

        val attention = status().attention

        assertEquals(
            listOf(
                Triple(refused, AttentionKind.REFUSED, true),
                Triple(missing, AttentionKind.UNAVAILABLE, false),
                Triple(taken, AttentionKind.UNAVAILABLE, false),
                Triple(deleted, AttentionKind.REFUSED, false),
            ),
            attention.map { Triple(it.item.id, it.kind, it.canOpen) },
        )
        assertTrue(attention.none { it.canDismiss })
        assertEquals(0, status().waitingCount)
    }

    @Test
    fun `unseen conflicts come first, newest first, and only a replaced active item can be opened`() = runTest {
        val anna = syncedPerson("Anna")
        val gift = gifts.addGift(giftFor(anna)).id
        operations.delete(pending(SyncEntityType.GIFT, gift))
        val older = conflict(SyncEntityType.PERSON, anna, baseVersion = 3)
        clock.advanceBy(Duration.ofMinutes(1))
        val duplicate = conflict(SyncEntityType.GIFT, gift, baseVersion = 0)
        clock.advanceBy(Duration.ofMinutes(1))
        val seen = conflict(SyncEntityType.PERSON, anna, baseVersion = 4)
        database.syncConflictDao().markSeen(seen, alice, clock.instant)
        val refused = later { people.addPerson("Béla", null) }.id
        block(SyncEntityType.PERSON, refused, BlockReason.REJECTED)

        val attention = status().attention

        assertEquals(listOf(duplicate, older, pending(SyncEntityType.PERSON, refused).id), attention.map { it.id })
        assertEquals(
            listOf(AttentionKind.DUPLICATE_GIFT_REMOVED, AttentionKind.REPLACED_BY_OTHER_DEVICE, AttentionKind.REFUSED),
            attention.map { it.kind },
        )
        assertEquals(listOf(false, true, true), attention.map { it.canOpen })
        assertEquals(listOf(true, true, false), attention.map { it.canDismiss })
    }

    @Test
    fun `items are named as Room has them, for every type, deleted ones included`() = runTest {
        val anna = syncedPerson("Anna")
        val birthday = occasions.addOccasion(anna, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true)).id
        val idea = ideas.addGiftIdea(ideaFor(anna)).id
        val gift = ideas.markAsGiven(idea, LocalDate.of(2026, 10, 11), null).id
        gifts.deleteGift(gift)
        listOf(
            SyncEntityType.PERSON to anna, SyncEntityType.OCCASION to birthday, SyncEntityType.GIFT_IDEA to idea,
            SyncEntityType.GIFT to gift,
        ).forEach { (type, id) ->
            operations.findForEntity(type, id)?.let { operations.delete(it) }
            conflict(type, id, baseVersion = 2)
            clock.advanceBy(Duration.ofSeconds(1))
        }

        val items = status().attention.map { it.item to it.canOpen }.reversed()

        assertEquals(
            listOf(
                SyncItem(SyncItemType.PERSON, anna, "Anna") to true,
                SyncItem(SyncItemType.OCCASION, birthday, null, OccasionType.BIRTHDAY) to true,
                // Given, so the idea screens no longer show it.
                SyncItem(SyncItemType.GIFT_IDEA, idea, "Scarf") to false,
                SyncItem(SyncItemType.GIFT, gift, "Scarf") to false,
            ),
            items,
        )
    }

    @Test
    fun `wishlist and wish changes are waiting, and refused or replaced ones open while their rows are active`() = runTest {
        val list = later { wishlists.addWishlist(null, "Birthday") }
        val lamp = later { wishlists.addWish(list.id, WishDetails("Lamp", null, null, null)) }
        assertEquals(2, status().waitingCount)

        block(SyncEntityType.WISHLIST, list.id, BlockReason.REJECTED)
        operations.delete(pending(SyncEntityType.WISH, lamp.id))
        conflict(SyncEntityType.WISH, lamp.id, baseVersion = 2)

        val status = status()
        assertEquals(0, status.waitingCount)
        assertEquals(
            listOf(
                Triple(SyncItem(SyncItemType.WISH, lamp.id, "Lamp"), AttentionKind.REPLACED_BY_OTHER_DEVICE, true),
                Triple(SyncItem(SyncItemType.WISHLIST, list.id, "Birthday"), AttentionKind.REFUSED, true),
            ),
            status.attention.map { Triple(it.item, it.kind, it.canOpen) },
        )
    }

    @Test
    fun `deleted wishlists and wishes stay named but cannot be opened`() = runTest {
        val list = later { wishlists.addWishlist(null, "Birthday") }
        val lamp = later { wishlists.addWish(list.id, WishDetails("Lamp", null, null, null)) }
        operations.findForEntity(SyncEntityType.WISHLIST, list.id)?.let { operations.delete(it) }
        operations.findForEntity(SyncEntityType.WISH, lamp.id)?.let { operations.delete(it) }
        conflict(SyncEntityType.WISHLIST, list.id, baseVersion = 2)
        conflict(SyncEntityType.WISH, lamp.id, baseVersion = 2)
        assertTrue(status().attention.all { it.canOpen })

        wishlists.deleteWishlist(list.id)

        val attention = status().attention
        assertEquals(setOf("Birthday", "Lamp"), attention.map { it.item.name }.toSet())
        assertTrue(attention.none { it.canOpen })
    }

    @Test
    fun `an unavailable wishlist is never openable, active or not`() = runTest {
        val list = later { wishlists.addWishlist(null, "Birthday") }
        block(SyncEntityType.WISHLIST, list.id, BlockReason.REMOTE_MISSING)

        val item = status().attention.single()

        assertEquals(AttentionKind.UNAVAILABLE, item.kind)
        assertFalse(item.canOpen)
    }

    @Test
    fun `another account's wishlist and wish are neither named nor openable`() = runTest {
        activeOwner.ownerId.value = bob
        val bobsList = wishlists.addWishlist(null, "Bob's list")
        val bobsWish = wishlists.addWish(bobsList.id, WishDetails("Bob's wish", null, null, null))
        activeOwner.ownerId.value = alice
        // Only reachable through corrupted data: Alice's conflict pointing at Bob's rows.
        conflict(SyncEntityType.WISHLIST, bobsList.id, baseVersion = 2)
        conflict(SyncEntityType.WISH, bobsWish.id, baseVersion = 2)

        val attention = status().attention

        assertEquals(listOf(null, null), attention.map { it.item.name })
        assertTrue(attention.none { it.canOpen })
    }

    @Test
    fun `another account's and ownerless pending work is neither shown nor counted`() = runTest {
        activeOwner.ownerId.value = bob
        val bobs = people.addPerson("Bob's friend", null).id
        block(SyncEntityType.PERSON, bobs, BlockReason.REJECTED)
        conflict(SyncEntityType.PERSON, bobs, baseVersion = 2, owner = bob)
        people.addPerson("Bob's other friend", null)
        activeOwner.ownerId.value = alice
        val anna = people.addPerson("Anna", null).id
        // A pre-v2 operation without an owner.
        operations.update(pending(SyncEntityType.PERSON, anna).copy(ownerUserId = null))

        assertEquals(SyncStatus(waitingCount = 0, attention = emptyList()), status())

        activeOwner.ownerId.value = null
        assertEquals(SyncStatus(waitingCount = 0, attention = emptyList()), repository.observeSyncStatus().first())
    }

    @Test
    fun `the status follows changes to the queue, the conflicts and the items themselves`() = runTest {
        val anna = syncedPerson("Anna")
        val emissions = Channel<SyncStatus>(Channel.UNLIMITED)
        val collector = launch { repository.observeSyncStatus().collect { emissions.send(it) } }
        assertEquals(SyncStatus(0, emptyList()), emissions.receive())

        people.updatePerson(anna, "Anna K.", null)
        assertEquals(SyncStatus(1, emptyList()), emissions.receive())

        block(SyncEntityType.PERSON, anna, BlockReason.REJECTED)
        assertEquals(listOf(Triple("Anna K.", AttentionKind.REFUSED, true)), emissions.receive().attention.map { it.summary() })

        // Renamed and deleted while still needing attention: the item is shown as it is now.
        database.personDao().run { update(findById(anna, alice)!!.copy(name = "Anna Kovács")) }
        assertEquals(listOf(Triple("Anna Kovács", AttentionKind.REFUSED, true)), emissions.receive().attention.map { it.summary() })
        database.personDao().softDelete(anna, alice, clock.instant)
        assertEquals(listOf(Triple("Anna Kovács", AttentionKind.REFUSED, false)), emissions.receive().attention.map { it.summary() })

        val conflict = conflict(SyncEntityType.PERSON, anna, baseVersion = 2)
        assertEquals(2, emissions.receive().attention.size)
        repository.dismissConflict(conflict)
        assertEquals(listOf(AttentionKind.REFUSED), emissions.receive().attention.map { it.kind })
        collector.cancel()
    }

    @Test
    fun `dismissing marks only the account's own conflict seen, once, and writes nothing else`() = runTest {
        val anna = syncedPerson("Anna")
        val mine = conflict(SyncEntityType.PERSON, anna, baseVersion = 2)
        val bobs = conflict(SyncEntityType.PERSON, anna, baseVersion = 3, owner = bob)
        val queue = operations.getAllForOwner(alice)
        val person = database.personDao().findById(anna, alice)
        // Whoever is active can only dismiss their own conflicts.
        activeOwner.ownerId.value = bob
        repository.dismissConflict(mine)
        assertEquals(listOf(mine), database.syncConflictDao().getUnseen(alice).map { it.id })
        activeOwner.ownerId.value = alice

        repository.dismissConflict(mine)
        clock.advanceBy(Duration.ofMinutes(1))
        repository.dismissConflict(mine)
        repository.dismissConflict(bobs)

        assertTrue(database.syncConflictDao().getUnseen(alice).isEmpty())
        assertEquals(listOf(bobs), database.syncConflictDao().getUnseen(bob).map { it.id })
        assertEquals("already seen", 0, database.syncConflictDao().markSeen(mine, alice, clock.instant))
        assertEquals(queue, operations.getAllForOwner(alice))
        assertEquals(person, database.personDao().findById(anna, alice))
        assertTrue(status().attention.isEmpty())
    }

    // ---- fixtures ----

    private suspend fun status(): SyncStatus = repository.observeSyncStatus().first()

    private fun AttentionItem.summary() = Triple(item.name, kind, canOpen)

    /** Runs [write] and moves the clock on, so queue order follows the order of the writes. */
    private suspend fun <T> later(write: suspend () -> T): T = write().also { clock.advanceBy(Duration.ofSeconds(1)) }

    private suspend fun pending(type: SyncEntityType, id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(type, id)) { "No pending $type operation" }

    private suspend fun block(type: SyncEntityType, id: UUID, reason: BlockReason) {
        val operation = pending(type, id)
        assertEquals(1, operations.blockCurrentMutation(operation.id, operation.mutationId, reason.name))
    }

    /** A person the backend already has, with nothing pending locally. */
    private suspend fun syncedPerson(name: String): UUID {
        val person = people.addPerson(name, null)
        database.personDao().run { update(findById(person.id, alice)!!.copy(version = 1, remoteState = RemoteState.SYNCED)) }
        operations.delete(pending(SyncEntityType.PERSON, person.id))
        return person.id
    }

    private suspend fun conflict(type: SyncEntityType, id: UUID, baseVersion: Long, owner: UUID = alice): UUID {
        val conflictId = UUID.randomUUID()
        database.syncConflictDao().insert(
            SyncConflictEntity(
                id = conflictId, ownerUserId = owner, entityType = type, entityId = id, mutationId = conflictId,
                baseVersion = baseVersion, remoteVersion = baseVersion + 1, localSnapshotJson = "{}", createdAt = clock.instant,
                seenAt = null,
            ),
        )
        return conflictId
    }

    private fun giftFor(personId: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = personId, occasionId = null,
        name = "Book", givenAt = LocalDate.of(2026, 10, 1), price = null, productUrl = null, note = null,
    )

    private fun ideaFor(personId: UUID) = GiftIdeaDetails(
        recipientPersonId = personId, occasionId = null, name = "Scarf", status = GiftIdeaStatus.IDEA,
        estimatedPrice = null, actualPrice = null, productUrl = null, note = null,
    )
}
