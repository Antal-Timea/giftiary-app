package app.gifty.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.local.entity.WishEntity
import app.gifty.data.local.entity.WishlistEntity
import app.gifty.data.sync.SyncRequester
import app.gifty.domain.model.Money
import app.gifty.domain.model.Wishlist
import app.gifty.domain.repository.NoActiveOwnerException
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.repository.WishDetails
import app.gifty.domain.repository.WishNotFoundException
import app.gifty.domain.repository.WishlistNotFoundException
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WishlistRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val otherOwnerId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(ownerUserId)
    private var syncRequests = 0
    private lateinit var database: GiftyDatabase
    private lateinit var repository: WishlistRepositoryImpl

    private val wishlistDao get() = database.wishlistDao()
    private val wishDao get() = database.wishDao()
    private val personDao get() = database.personDao()
    private val syncDao get() = database.syncOperationDao()

    private val anna = person(ownerUserId)
    private val lamp = WishDetails("Lamp", note = "Brass", estimatedPrice = Money(12990, "HUF"), productUrl = "https://example.com/lamp")

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        repository = WishlistRepositoryImpl(
            database = database,
            wishlistDao = database.wishlistDao(),
            wishDao = database.wishDao(),
            personDao = database.personDao(),
            syncQueue = SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner = activeOwner,
            clock = clock,
            syncRequester = SyncRequester { syncRequests++ },
        )
        personDao.insert(anna)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- wishlists ----

    @Test
    fun `adding a wishlist stores a local-only row and queues its creation`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")

        val entity = wishlistDao.findActiveById(wishlist.id, ownerUserId)!!
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertEquals(anna.id, entity.personId)
        assertEquals(listOf(wishlist), repository.observeWishlists().first())
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.WISHLIST, wishlist.id))
        assertEquals(1, syncRequests)
    }

    @Test
    fun `a wishlist does not need a person`() = runTest {
        val wishlist = repository.addWishlist(null, "Mine")

        assertNull(repository.getWishlist(wishlist.id)!!.personId)
    }

    @Test
    fun `a wishlist cannot be added for another owner's or a deleted person`() = runTest {
        val strangersPerson = person(otherOwnerId).also { personDao.insert(it) }
        val gone = person(ownerUserId).also { personDao.insert(it) }
        personDao.softDelete(gone.id, ownerUserId, clock.instant)

        listOf(strangersPerson.id, gone.id).forEach { personId ->
            val result = runCatching { repository.addWishlist(personId, "Birthday") }
            assertTrue("$personId", result.exceptionOrNull() is PersonNotFoundException)
        }
        assertEquals(0, count("wishlist"))
        assertEquals(0, count("sync_operation"))
    }

    @Test
    fun `renaming a synced wishlist keeps its sync metadata and queues an update`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        markSynced(SyncEntityType.WISHLIST, wishlist.id)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.renameWishlist(wishlist.id, "Name day")

        val entity = wishlistDao.findActiveById(wishlist.id, ownerUserId)!!
        assertEquals("Name day", entity.name)
        assertEquals(clock.instant, entity.updatedAt)
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(3L, entity.version)
        assertEquals(SyncOperation.UPDATE, pending(SyncEntityType.WISHLIST, wishlist.id))
    }

    @Test
    fun `renaming to the same name changes and queues nothing`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        markSynced(SyncEntityType.WISHLIST, wishlist.id)

        repository.renameWishlist(wishlist.id, "Birthday")

        assertNull(pending(SyncEntityType.WISHLIST, wishlist.id))
    }

    @Test
    fun `deleting a local-only wishlist keeps its create as a tombstone and forgets its local-only wishes`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)

        repository.deleteWishlist(wishlist.id)

        assertNull(repository.getWishlist(wishlist.id))
        assertNull(repository.getWish(wish.id))
        assertTrue(repository.observeWishlists().first().isEmpty())
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.WISHLIST, wishlist.id))
        assertNull(pending(SyncEntityType.WISH, wish.id))
        // Soft deletion only: both rows stay for sync.
        assertEquals(clock.instant, wishlistDao.findById(wishlist.id, ownerUserId)!!.deletedAt)
        assertEquals(clock.instant, wishDao.findById(wish.id, ownerUserId)!!.deletedAt)
    }

    @Test
    fun `deleting a synced wishlist queues its delete and one for each of its synced wishes`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val first = repository.addWish(wishlist.id, lamp)
        val second = repository.addWish(wishlist.id, lamp.copy(name = "Mug"))
        val alreadyGone = repository.addWish(wishlist.id, lamp.copy(name = "Scarf"))
        listOf(first.id, second.id, alreadyGone.id).forEach { markSynced(SyncEntityType.WISH, it) }
        markSynced(SyncEntityType.WISHLIST, wishlist.id)
        repository.deleteWish(alreadyGone.id)
        syncDao.delete(syncDao.findForEntity(SyncEntityType.WISH, alreadyGone.id)!!)

        repository.deleteWishlist(wishlist.id)

        assertEquals(SyncOperation.DELETE, pending(SyncEntityType.WISHLIST, wishlist.id))
        assertEquals(SyncOperation.DELETE, pending(SyncEntityType.WISH, first.id))
        assertEquals(SyncOperation.DELETE, pending(SyncEntityType.WISH, second.id))
        assertNull("an already deleted wish is not deleted again", pending(SyncEntityType.WISH, alreadyGone.id))
    }

    @Test
    fun `a failing queue write rolls back the whole wishlist deletion`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val first = repository.addWish(wishlist.id, lamp)
        val second = repository.addWish(wishlist.id, lamp.copy(name = "Mug"))
        // A pending DELETE for an active wish is inconsistent, so the queue refuses another DELETE.
        syncDao.delete(syncDao.findForEntity(SyncEntityType.WISH, second.id)!!)
        syncDao.insert(pendingDelete(SyncEntityType.WISH, second.id))

        val result = runCatching { repository.deleteWishlist(wishlist.id) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(wishlist, repository.getWishlist(wishlist.id))
        assertEquals(setOf(first, second), repository.observeWishes(wishlist.id).first().toSet())
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.WISHLIST, wishlist.id))
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.WISH, first.id))
        assertEquals(1 + 2, syncRequests)
    }

    // ---- wishes ----

    @Test
    fun `adding a wish stores a local-only row of the wishlist's owner and queues its creation`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")

        val wish = repository.addWish(wishlist.id, lamp)

        val entity = wishDao.findActiveById(wish.id, ownerUserId)!!
        assertEquals(ownerUserId, entity.ownerUserId)
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertEquals(Money(12990, "HUF"), repository.getWish(wish.id)!!.estimatedPrice)
        assertEquals(listOf(wish), repository.observeWishes(wishlist.id).first())
        assertEquals(SyncOperation.CREATE, pending(SyncEntityType.WISH, wish.id))
    }

    @Test
    fun `a wish cannot be added to a deleted, missing or another owner's wishlist`() = runTest {
        val gone = repository.addWishlist(anna.id, "Gone").also { repository.deleteWishlist(it.id) }
        val theirs = wishlist(otherOwnerId).also { wishlistDao.insert(it) }
        val operationsBefore = count("sync_operation")

        listOf(gone.id, theirs.id, UUID.randomUUID()).forEach { wishlistId ->
            val result = runCatching { repository.addWish(wishlistId, lamp) }
            assertTrue("$wishlistId", result.exceptionOrNull() is WishlistNotFoundException)
        }
        assertEquals(0, count("wish"))
        assertEquals(operationsBefore, count("sync_operation"))
    }

    @Test
    fun `updating a synced wish keeps its photo and sync metadata and queues an update`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)
        markSynced(SyncEntityType.WISH, wish.id)
        val stored = wishDao.findActiveById(wish.id, ownerUserId)!!
        wishDao.update(stored.copy(photoPath = "users/$ownerUserId/wishes/${wish.id}.jpg"))
        clock.advanceBy(Duration.ofMinutes(1))

        repository.updateWish(wish.id, lamp.copy(note = null, estimatedPrice = null))

        val entity = wishDao.findActiveById(wish.id, ownerUserId)!!
        assertNull(entity.note)
        assertNull(entity.estimatedPriceAmountMinor)
        assertNull(entity.estimatedPriceCurrencyCode)
        assertEquals("users/$ownerUserId/wishes/${wish.id}.jpg", entity.photoPath)
        assertEquals(clock.instant, entity.updatedAt)
        assertEquals(3L, entity.version)
        assertEquals(SyncOperation.UPDATE, pending(SyncEntityType.WISH, wish.id))
    }

    @Test
    fun `an unchanged wish is neither written nor queued`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)
        markSynced(SyncEntityType.WISH, wish.id)
        clock.advanceBy(Duration.ofMinutes(1))

        repository.updateWish(wish.id, lamp)

        assertEquals(wish.updatedAt, repository.getWish(wish.id)!!.updatedAt)
        assertNull(pending(SyncEntityType.WISH, wish.id))
    }

    @Test
    fun `a local-only wish deleted before it synced leaves nothing to sync`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)

        repository.deleteWish(wish.id)

        assertNull(repository.getWish(wish.id))
        assertNull(pending(SyncEntityType.WISH, wish.id))
        assertEquals(clock.instant, wishDao.findById(wish.id, ownerUserId)!!.deletedAt)
    }

    @Test
    fun `deleting a synced wish queues a delete`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)
        markSynced(SyncEntityType.WISH, wish.id)

        repository.deleteWish(wish.id)

        assertEquals(SyncOperation.DELETE, pending(SyncEntityType.WISH, wish.id))
    }

    // ---- accounts ----

    @Test
    fun `a single wishlist and wish are observed live, only the account's, and gone once deleted`() = runTest {
        val list = repository.addWishlist(null, "Birthday")
        val stored = repository.addWish(list.id, lamp)
        val theirList = wishlist(otherOwnerId).also { wishlistDao.insert(it) }
        val theirWish = wish(theirList).also { wishDao.insert(it) }

        assertEquals(list, repository.observeWishlist(list.id).first())
        assertEquals(stored, repository.observeWish(stored.id).first())
        assertNull(repository.observeWishlist(theirList.id).first())
        assertNull(repository.observeWish(theirWish.id).first())

        clock.advanceBy(Duration.ofMinutes(1))
        repository.renameWishlist(list.id, "Name day")
        repository.updateWish(stored.id, lamp.copy(name = "Brass lamp"))
        assertEquals("Name day", repository.observeWishlist(list.id).first()?.name)
        assertEquals("Brass lamp", repository.observeWish(stored.id).first()?.name)

        repository.deleteWishlist(list.id)
        assertNull(repository.observeWishlist(list.id).first())
        assertNull(repository.observeWish(stored.id).first())
    }

    @Test
    fun `wish counts include only the account's active wishes`() = runTest {
        val birthday = repository.addWishlist(null, "Birthday")
        val christmas = repository.addWishlist(anna.id, "Christmas")
        val empty = repository.addWishlist(null, "Empty")
        repository.addWish(birthday.id, lamp)
        repository.addWish(birthday.id, lamp.copy(name = "Mug"))
        val gone = repository.addWish(birthday.id, lamp.copy(name = "Socks"))
        repository.deleteWish(gone.id)
        repository.addWish(christmas.id, lamp)
        val theirList = wishlist(otherOwnerId).also { wishlistDao.insert(it) }
        wishDao.insert(wish(theirList))

        assertEquals(mapOf(birthday.id to 2, christmas.id to 1), repository.observeWishCounts().first())
        assertTrue(empty.id !in repository.observeWishCounts().first())

        repository.deleteWishlist(christmas.id)
        assertEquals(mapOf(birthday.id to 2), repository.observeWishCounts().first())
        activeOwner.ownerId.value = null
        assertEquals(emptyMap<UUID, Int>(), repository.observeWishCounts().first())
    }

    @Test
    fun `a running single-wishlist observation follows the account`() = runTest {
        val mine = repository.addWishlist(null, "Mine")
        val observed = MutableStateFlow<Wishlist?>(null)
        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeWishlist(mine.id).collect { observed.value = it; emissions++ }
        }
        awaitValue(observed) { it == mine }

        activeOwner.ownerId.value = otherOwnerId
        awaitValue(observed) { it == null }

        activeOwner.ownerId.value = ownerUserId
        awaitValue(observed) { it == mine }
        assertTrue(emissions >= 3)
    }

    @Test
    fun `another owner's wishlists and wishes are never returned, changed or deleted`() = runTest {
        val theirList = wishlist(otherOwnerId).also { wishlistDao.insert(it) }
        val theirWish = wish(theirList).also { wishDao.insert(it) }

        assertNull(repository.getWishlist(theirList.id))
        assertNull(repository.getWish(theirWish.id))
        assertTrue(repository.observeWishlists().first().isEmpty())
        assertTrue(repository.observeWishes(theirList.id).first().isEmpty())
        assertTrue(runCatching { repository.renameWishlist(theirList.id, "Mine") }.exceptionOrNull() is WishlistNotFoundException)
        assertTrue(runCatching { repository.updateWish(theirWish.id, lamp) }.exceptionOrNull() is WishNotFoundException)
        repository.deleteWish(theirWish.id)
        repository.deleteWishlist(theirList.id)

        assertEquals(theirList, wishlistDao.findActiveById(theirList.id, otherOwnerId))
        assertEquals(theirWish, wishDao.findActiveById(theirWish.id, otherOwnerId))
        assertEquals(0, count("sync_operation"))
    }

    @Test
    fun `a running wishlist list follows the account from one to none to the next`() = runTest {
        val mine = repository.addWishlist(null, "Mine")
        val theirs = wishlist(otherOwnerId).also { wishlistDao.insert(it) }
        val ids = MutableStateFlow<List<UUID>?>(null)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeWishlists().collect { lists -> ids.value = lists.map { it.id } }
        }
        awaitIds(ids, listOf(mine.id))

        activeOwner.ownerId.value = null
        awaitIds(ids, emptyList())

        activeOwner.ownerId.value = otherOwnerId
        awaitIds(ids, listOf(theirs.id))
    }

    @Test
    fun `without an owner nothing can be read or written`() = runTest {
        val wishlist = repository.addWishlist(anna.id, "Birthday")
        val wish = repository.addWish(wishlist.id, lamp)
        activeOwner.ownerId.value = null
        val operationsBefore = count("sync_operation")

        assertNull(repository.getWishlist(wishlist.id))
        assertNull(repository.getWish(wish.id))
        assertTrue(repository.observeWishlists().first().isEmpty())
        listOf(
            runCatching { repository.addWishlist(null, "Mine") },
            runCatching { repository.renameWishlist(wishlist.id, "Other") },
            runCatching { repository.deleteWishlist(wishlist.id) },
            runCatching { repository.addWish(wishlist.id, lamp) },
            runCatching { repository.updateWish(wish.id, lamp.copy(name = "Mug")) },
            runCatching { repository.deleteWish(wish.id) },
        ).forEach { assertTrue("${it.exceptionOrNull()}", it.exceptionOrNull() is NoActiveOwnerException) }
        assertEquals(1, count("wishlist"))
        assertEquals(1, count("wish"))
        assertEquals(operationsBefore, count("sync_operation"))
    }

    @Test
    fun `the database refuses a wish of another owner than its wishlist`() = runTest {
        val mine = wishlist(ownerUserId).also { wishlistDao.insert(it) }

        val result = runCatching { wishDao.insert(wish(mine).copy(ownerUserId = otherOwnerId)) }

        assertTrue("${result.exceptionOrNull()}", result.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(0, count("wish"))
    }

    @Test
    fun `the database refuses a wishlist for another owner's person, and accepts its own owner's or none`() = runTest {
        val strangersPerson = person(otherOwnerId).also { personDao.insert(it) }

        val crossOwner = runCatching { wishlistDao.insert(wishlist(ownerUserId).copy(personId = strangersPerson.id)) }
        val missing = runCatching { wishlistDao.insert(wishlist(ownerUserId).copy(personId = UUID.randomUUID())) }
        val annas = wishlist(ownerUserId).copy(personId = anna.id).also { wishlistDao.insert(it) }
        val personless = wishlist(ownerUserId).also { wishlistDao.insert(it) }

        assertTrue("${crossOwner.exceptionOrNull()}", crossOwner.exceptionOrNull() is SQLiteConstraintException)
        assertTrue("${missing.exceptionOrNull()}", missing.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(setOf(annas.id, personless.id), repository.observeWishlists().first().map { it.id }.toSet())
    }

    @Test
    fun `the database refuses moving a person-linked wishlist to another owner on its own`() = runTest {
        val annas = wishlist(ownerUserId).copy(personId = anna.id).also { wishlistDao.insert(it) }

        val moved = runCatching { wishlistDao.update(annas.copy(ownerUserId = otherOwnerId)) }

        assertTrue("${moved.exceptionOrNull()}", moved.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(annas, wishlistDao.findById(annas.id, ownerUserId))
    }

    @Test
    fun `wishlists are listed by name, ignoring case, and wishes in the order they were added`() = runTest {
        val zoe = repository.addWishlist(null, "zoe")
        val anyas = repository.addWishlist(anna.id, "Anya")
        val first = repository.addWish(anyas.id, lamp)
        clock.advanceBy(Duration.ofSeconds(1))
        val second = repository.addWish(anyas.id, lamp.copy(name = "A mug"))

        assertEquals(listOf(anyas, zoe), repository.observeWishlists().first())
        assertEquals(listOf(first, second), repository.observeWishes(anyas.id).first())
        assertTrue(repository.observeWishes(zoe.id).first().isEmpty())
    }

    private suspend fun <T> awaitValue(flow: MutableStateFlow<T>, condition: (T) -> Boolean) {
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { flow.first(condition) } }
    }

    // Room emits on its own executor, so the wait is in real time.
    private suspend fun awaitIds(ids: MutableStateFlow<List<UUID>?>, expected: List<UUID>) {
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { ids.first { it == expected } } }
    }

    private suspend fun pending(type: SyncEntityType, entityId: UUID): SyncOperation? =
        syncDao.findForEntity(type, entityId)
            ?.also { assertEquals("an operation has its entity's owner", ownerUserId, it.ownerUserId) }
            ?.operation

    private suspend fun markSynced(type: SyncEntityType, id: UUID) {
        when (type) {
            SyncEntityType.WISHLIST -> wishlistDao.findById(id, ownerUserId)!!
                .let { wishlistDao.update(it.copy(remoteState = RemoteState.SYNCED, version = 3)) }
            SyncEntityType.WISH -> wishDao.findById(id, ownerUserId)!!
                .let { wishDao.update(it.copy(remoteState = RemoteState.SYNCED, version = 3)) }
            else -> error("Not a wishlist type: $type")
        }
        syncDao.findForEntity(type, id)?.let { syncDao.delete(it) }
    }

    private fun pendingDelete(type: SyncEntityType, id: UUID) = SyncOperationEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        entityType = type,
        entityId = id,
        operation = SyncOperation.DELETE,
        mutationId = UUID.randomUUID(),
        inFlightMutationId = null,
        blockedReason = null,
        createdAt = clock.instant,
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    )

    private fun count(table: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM `$table`")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun person(ownerUserId: UUID) = PersonEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        name = "Someone",
        relationship = null,
        photoPath = null,
        linkedUserId = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = clock.instant,
        updatedAt = clock.instant,
        deletedAt = null,
    )

    private fun wishlist(ownerUserId: UUID) = WishlistEntity(
        id = UUID.randomUUID(),
        ownerUserId = ownerUserId,
        personId = null,
        name = "Theirs",
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = clock.instant,
        updatedAt = clock.instant,
        deletedAt = null,
    )

    private fun wish(wishlist: WishlistEntity) = WishEntity(
        id = UUID.randomUUID(),
        ownerUserId = wishlist.ownerUserId,
        wishlistId = wishlist.id,
        name = "Their lamp",
        note = null,
        estimatedPriceAmountMinor = null,
        estimatedPriceCurrencyCode = null,
        productUrl = null,
        photoPath = null,
        version = 0,
        remoteState = RemoteState.LOCAL_ONLY,
        createdAt = clock.instant,
        updatedAt = clock.instant,
        deletedAt = null,
    )
}
