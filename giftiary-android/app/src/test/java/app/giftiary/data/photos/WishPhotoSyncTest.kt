package app.giftiary.data.photos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.SyncReconciler
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.data.remote.dto.RemoteWishFields
import app.giftiary.data.repository.WishlistRepositoryImpl
import app.giftiary.data.sync.FakeBackend
import app.giftiary.data.sync.FakeBackend.Behavior
import app.giftiary.data.sync.SyncDependencies
import app.giftiary.data.sync.SyncPusher
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.sync.SyncRunner
import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.WishDetails
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeGiftPhotoRemote
import app.giftiary.testing.MutableClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A wish's photo, which shares the gift and person photos' bucket, transfers and drain: what a
 * wish's writes queue, and a drain in the coordinator's order against [FakeGiftPhotoRemote] and
 * [FakeBackend].
 */
@RunWith(RobolectricTestRunner::class)
class WishPhotoSyncTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val account = RemoteAccount.capture(alice, token(alice))

    // Photo files carry real modification times, so this clock starts at real time.
    private val clock = MutableClock(Instant.now())
    private val files = GiftPhotoFiles(ApplicationProvider.getApplicationContext())
    private val remote = FakeGiftPhotoRemote()
    private lateinit var database: GiftiaryDatabase
    private lateinit var backend: FakeBackend
    private lateinit var wishlists: WishlistRepositoryImpl
    private lateinit var photos: GiftPhotoSync
    private lateinit var runner: SyncRunner
    private lateinit var listId: UUID

    private val transfers get() = database.photoTransferDao()
    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(alice)
        wishlists = WishlistRepositoryImpl(
            database, database.wishlistDao(), database.wishDao(), database.personDao(), queue, activeOwner, clock, SyncRequester {},
            transfers, files,
        )
        backend = FakeBackend(database)
        photos = GiftPhotoSync(transfers, database.giftDao(), database.giftIdeaDao(), database.personDao(), database.wishDao(), operations, files, remote, clock)
        runner = SyncRunner(
            operations,
            SyncDependencies(database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao()),
            SyncPusher(
                database, operations, database.personDao(), database.occasionDao(), database.giftIdeaDao(),
                database.giftDao(), database.wishlistDao(), database.wishDao(),
                SyncReconciler(
                    database, operations, database.personDao(), database.occasionDao(), database.giftIdeaDao(),
                    database.giftDao(), database.wishlistDao(), database.wishDao(), database.syncConflictDao(), clock,
                ),
                backend, backend, clock,
            ),
            activeOwner,
            photos,
        )
        listId = wishlists.addWishlist(null, "Birthday").id
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- writes ----

    @Test
    fun `a wish without a photo queues no Storage work and syncs as before`() = runTest {
        val wish = wishlists.addWish(listId, details("Lamp"))

        assertNull(wish.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        drain()
        assertNull(remotePhotoOf(wish.id))
        assertNotNull(backend.rows[wish.id])
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `a wish saved with a photo stores the row, its upload and its create together`() = runTest {
        val photo = newPhoto()

        val wish = wishlists.addWish(listId, details("Lamp", photo))

        assertEquals(photo, wish.photoPath)
        assertEquals(photo, wishlists.getWish(wish.id)!!.photoPath)
        val upload = transfers.find(photo, alice)!!
        assertEquals(PhotoTransferKind.UPLOAD, upload.kind)
        assertEquals(SyncEntityType.WISH, upload.entityType)
        assertEquals(wish.id, upload.entityId)
        assertEquals(SyncOperation.CREATE, operations.findForEntity(SyncEntityType.WISH, wish.id)!!.operation)
    }

    @Test
    fun `a photo of another account, or no longer on this device, writes no wish, transfer or operation`() = runTest {
        val bobs = newPhoto(owner = bob)
        val missing = files.newPath(alice)
        val before = operations.getAllForOwner(alice).map { it.entityId }.toSet()

        listOf(bobs, missing, "https://example.com/a.jpg").forEach { path ->
            val failure = runCatching { wishlists.addWish(listId, details("Lamp", path)) }.exceptionOrNull()
            assertTrue("$path: $failure", failure is GiftPhotoUnavailableException)
        }
        val lamp = wishlists.addWish(listId, details("Lamp"))
        val failure = runCatching { wishlists.updateWish(lamp.id, details("Lamp", bobs)) }.exceptionOrNull()

        assertTrue(failure is GiftPhotoUnavailableException)
        assertNull(wishlists.getWish(lamp.id)!!.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        assertEquals(before + lamp.id, operations.getAllForOwner(alice).map { it.entityId }.toSet())
    }

    @Test
    fun `a photo added to an existing wish later is uploaded before the wish names it`() = runTest {
        val wish = wishlists.addWish(listId, details("Lamp"))
        drain()
        val photo = newPhoto()

        wishlists.updateWish(wish.id, details("Lamp", photo))
        assertEquals(SyncOperation.UPDATE, operations.findForEntity(SyncEntityType.WISH, wish.id)!!.operation)
        drain()

        assertEquals(listOf("upload $photo"), remote.calls)
        assertEquals(photo, remotePhotoOf(wish.id))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- offline: upload before the row ----

    @Test
    fun `offline, the wish and its photo stay on this device until the upload succeeds`() = runTest {
        val photo = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", photo))
        remote.failure = RemoteFailure.NETWORK

        assertEquals(1, drain())
        assertEquals(1, drain())
        assertNull("the backend never names a missing object", backend.rows[wish.id])
        assertNotNull(operations.findForEntity(SyncEntityType.WISH, wish.id))
        assertTrue("the local photo stays usable", files.exists(photo))
        assertEquals(photo, wishlists.getWish(wish.id)!!.photoPath)

        remote.failure = null
        assertEquals(0, drain())
        assertEquals(listOf("upload $photo", "upload $photo", "upload $photo"), remote.calls)
        assertEquals(photo, remotePhotoOf(wish.id))
        assertTrue(operations.getAllForOwner(alice).isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `an upload retried after it was stored replaces the same object, never a second one`() = runTest {
        val photo = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", photo))
        // The object arrived, but the row push failed: the next drain does not upload again.
        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        drain()

        assertEquals(listOf("upload $photo"), remote.calls)
        assertEquals(setOf(photo), remote.objects.keys)
        assertEquals(photo, remotePhotoOf(wish.id))
    }

    // ---- replacement, removal and deletion ----

    @Test
    fun `a replaced photo's object is deleted only after the wish naming the new one is pushed`() = runTest {
        val old = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", old))
        drain()
        val new = newPhoto()

        wishlists.updateWish(wish.id, details("Lamp", new))
        assertFalse("the old file leaves this device at once", files.exists(old))
        assertEquals(PhotoTransferKind.DELETE, transfers.find(old, alice)!!.kind)
        assertEquals(PhotoTransferKind.UPLOAD, transfers.find(new, alice)!!.kind)

        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertEquals("the backend still names the old photo", old, remotePhotoOf(wish.id))
        assertTrue(remote.objects.containsKey(old))

        drain()
        assertEquals(new, remotePhotoOf(wish.id))
        assertEquals(setOf(new), remote.objects.keys)
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `replacing a photo that never left the device drops its upload`() = runTest {
        val first = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", first))
        val second = newPhoto()

        wishlists.updateWish(wish.id, details("Lamp", second))
        drain()

        assertEquals(listOf("upload $second"), remote.calls)
        assertFalse(files.exists(first))
        assertEquals(second, remotePhotoOf(wish.id))
    }

    @Test
    fun `a removed photo's object is deleted once the wish without it is pushed`() = runTest {
        val photo = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", photo))
        drain()

        wishlists.updateWish(wish.id, details("Lamp"))
        assertNull(wishlists.getWish(wish.id)!!.photoPath)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertTrue("the backend's wish still names it", remote.objects.containsKey(photo))

        drain()
        assertNull(remotePhotoOf(wish.id))
        assertTrue(remote.objects.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a wish deletes its photo, and its tombstone names none`() = runTest {
        val photo = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", photo))
        drain()

        wishlists.deleteWish(wish.id)
        assertNull(database.wishDao().findById(wish.id, alice)!!.photoPath)
        assertFalse(files.exists(photo))
        assertEquals(SyncEntityType.WISH, transfers.find(photo, alice)!!.entityType)
        drain()

        assertNull(remotePhotoOf(wish.id))
        assertNotNull((backend.rows.getValue(wish.id).fields as RemoteWishFields).deletedAt)
        assertTrue(remote.objects.isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a wish whose photo never left the device sends nothing to Storage`() = runTest {
        val photo = newPhoto()
        val wish = wishlists.addWish(listId, details("Lamp", photo))

        wishlists.deleteWish(wish.id)
        drain()

        assertTrue(remote.calls.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a wishlist deletes its wishes' photos with them`() = runTest {
        val lamp = newPhoto()
        val mug = newPhoto()
        val lampWish = wishlists.addWish(listId, details("Lamp", lamp))
        val mugWish = wishlists.addWish(listId, details("Mug", mug))
        wishlists.addWish(listId, details("Book"))
        drain()

        wishlists.deleteWishlist(listId)
        assertFalse(files.exists(lamp))
        assertFalse(files.exists(mug))
        drain()

        assertNull(remotePhotoOf(lampWish.id))
        assertNull(remotePhotoOf(mugWish.id))
        assertTrue(remote.objects.isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- local files ----

    @Test
    fun `local pruning keeps a wish's photo, attached or still to upload, and deletes the rest`() = runTest {
        val attached = newPhoto()
        wishlists.addWish(listId, details("Lamp", attached))
        drain()
        val pending = newPhoto()
        wishlists.addWish(listId, details("Mug", pending))
        remote.failure = RemoteFailure.NETWORK
        drain()
        val abandoned = newPhoto()

        clock.advanceBy(Duration.ofDays(3))
        photos.pruneLocal(alice)

        assertTrue("an uploaded wish photo is kept for offline use", files.exists(attached))
        assertTrue(files.exists(pending))
        assertNotNull(transfers.find(pending, alice))
        assertFalse(files.exists(abandoned))
    }

    @Test
    fun `a wish photo that another device synced down is kept once downloaded`() = runTest {
        // What a pull leaves: the row names an object this device has never had.
        val photo = files.newPath(alice)
        val wish = wishlists.addWish(listId, details("Lamp"))
        database.wishDao().update(database.wishDao().findById(wish.id, alice)!!.copy(photoPath = photo))
        files.write(photo, BYTES)

        clock.advanceBy(Duration.ofDays(3))
        photos.pruneLocal(alice)

        assertTrue(files.exists(photo))
    }

    /** One drain in the coordinator's order; returns the photo failures worth retrying. */
    private suspend fun drain(): Int {
        val uploads = photos.upload(account)
        runner.run(account)
        val deletes = photos.deleteUnused(account)
        photos.pruneLocal(alice)
        return uploads.transientFailures + deletes.transientFailures
    }

    private fun newPhoto(owner: UUID = alice): String = files.newPath(owner).also { files.write(it, BYTES) }

    private fun remotePhotoOf(wishId: UUID): String? = (backend.rows[wishId]?.fields as RemoteWishFields?)?.photoPath

    private fun details(name: String, photoPath: String? = null) =
        WishDetails(name, note = null, estimatedPrice = Money(12990, "HUF"), productUrl = null, photoPath = photoPath)

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
