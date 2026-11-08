package app.giftiary.data.photos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.SyncReconciler
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.data.remote.dto.RemoteGiftFields
import app.giftiary.data.repository.GiftRepositoryImpl
import app.giftiary.data.repository.PersonRepositoryImpl
import app.giftiary.data.sync.FakeBackend
import app.giftiary.data.sync.FakeBackend.Behavior
import app.giftiary.data.sync.SyncDependencies
import app.giftiary.data.sync.SyncPusher
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.sync.SyncRunner
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftPhotoUnavailableException
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
import java.time.LocalDate
import java.util.UUID

/**
 * A gift's photo from the editor to Storage and back out: the transfers its writes queue, and a
 * drain in the coordinator's order (uploads, push, unused deletes, local pruning) against
 * [FakeGiftPhotoRemote] and [FakeBackend].
 */
@RunWith(RobolectricTestRunner::class)
class GiftPhotoSyncTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val account = RemoteAccount.capture(alice, token(alice))
    // Photo files carry real modification times, so this clock starts at real time.
    private val clock = MutableClock(Instant.now())
    private val files = GiftPhotoFiles(ApplicationProvider.getApplicationContext())
    private val remote = FakeGiftPhotoRemote()
    private lateinit var database: GiftiaryDatabase
    private lateinit var backend: FakeBackend
    private lateinit var people: PersonRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var photos: GiftPhotoSync
    private lateinit var runner: SyncRunner
    private lateinit var anna: UUID

    private val transfers get() = database.photoTransferDao()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(alice)
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {},
            transfers, files,
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {},
            transfers, files,
        )
        backend = FakeBackend(database)
        restart()
        anna = people.addPerson("Anna", null).id
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- writes ----

    @Test
    fun `a gift without a photo queues no transfer and syncs without Storage`() = runTest {
        val gift = gifts.addGift(details())

        drain()

        assertTrue(transfers.getPaths(alice).isEmpty())
        assertTrue(remote.calls.isEmpty())
        assertNull(remotePhotoOf(gift.id))
        assertNotNull(backend.rows[gift.id])
    }

    @Test
    fun `a photo of another account, or no longer on this device, cannot be attached and nothing is written`() = runTest {
        val bobs = newPhoto(owner = bob)
        val missing = files.newPath(alice)

        listOf(bobs, missing, "https://example.com/a.jpg").forEach { path ->
            val failure = runCatching { gifts.addGift(details(path)) }.exceptionOrNull()
            assertTrue("$path: $failure", failure is GiftPhotoUnavailableException)
        }

        assertTrue(database.giftDao().getPhotoPaths(alice).isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
        assertEquals(listOf(SyncEntityType.PERSON), database.syncOperationDao().getAllForOwner(alice).map { it.entityType })
    }

    // ---- offline, upload and retry ----

    @Test
    fun `a photo saved offline keeps its file and upload, and holds its gift back`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))
        remote.failure = RemoteFailure.NETWORK

        val transient = drain()

        assertEquals(1, transient)
        assertTrue(files.exists(photo))
        val upload = transfers.find(photo, alice)!!
        assertEquals(PhotoTransferKind.UPLOAD, upload.kind)
        assertEquals(1, upload.attemptCount)
        assertEquals("NETWORK", upload.lastErrorCode)
        assertNull("the gift row never names a missing object", backend.rows[gift.id])
        assertNotNull(backend.rows[anna])
        assertNotNull(database.syncOperationDao().findForEntity(SyncEntityType.GIFT, gift.id))
    }

    @Test
    fun `after a restart the pending upload goes first and then its gift is pushed`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))
        remote.failure = RemoteFailure.NETWORK
        drain()

        restart()
        remote.failure = null
        val transient = drain()

        assertEquals(0, transient)
        assertEquals(listOf("upload $photo", "upload $photo"), remote.calls)
        assertTrue(remote.objects.getValue(photo).contentEquals(BYTES))
        assertEquals(photo, remotePhotoOf(gift.id))
        assertNull(transfers.find(photo, alice))
        assertNull(database.syncOperationDao().findForEntity(SyncEntityType.GIFT, gift.id))
        assertTrue("the uploaded photo stays on this device", files.exists(photo))
    }

    // ---- replacement and removal ----

    @Test
    fun `a replaced photo's object is deleted only after the gift row naming the new one is pushed`() = runTest {
        val old = newPhoto()
        val gift = gifts.addGift(details(old))
        drain()
        val new = newPhoto()

        gifts.updateGift(gift.id, details(new))
        assertFalse("the old file leaves this device at once", files.exists(old))
        assertEquals(PhotoTransferKind.DELETE, transfers.find(old, alice)!!.kind)
        assertEquals(PhotoTransferKind.UPLOAD, transfers.find(new, alice)!!.kind)

        // The new photo goes up, but the gift row does not: the backend still names the old one.
        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertEquals(old, remotePhotoOf(gift.id))
        assertTrue(remote.objects.containsKey(old))
        assertTrue(remote.objects.containsKey(new))

        drain()
        assertEquals(new, remotePhotoOf(gift.id))
        assertFalse(remote.objects.containsKey(old))
        assertEquals("delete $old", remote.calls.last())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `replacing a photo that never left the device drops its upload`() = runTest {
        val first = newPhoto()
        val gift = gifts.addGift(details(first))
        val second = newPhoto()

        gifts.updateGift(gift.id, details(second))
        drain()

        assertEquals(listOf("upload $second"), remote.calls)
        assertFalse(files.exists(first))
        assertEquals(second, remotePhotoOf(gift.id))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `a removed photo's object is deleted once the gift without it is pushed`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))
        drain()

        gifts.updateGift(gift.id, details(photoPath = null))
        drain()

        assertNull(remotePhotoOf(gift.id))
        assertTrue(remote.objects.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `a failed delete is kept and tried again`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))
        drain()
        gifts.updateGift(gift.id, details(photoPath = null))

        remote.failure = RemoteFailure.SERVER
        val transient = drain()
        assertEquals(1, transient)
        assertEquals("SERVER", transfers.find(photo, alice)!!.lastErrorCode)

        remote.failure = null
        drain()
        assertTrue(remote.objects.isEmpty())
        assertNull(transfers.find(photo, alice))
    }

    // ---- gift deletion ----

    @Test
    fun `deleting a gift deletes its photo, and its tombstone names none`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))
        drain()

        gifts.deleteGift(gift.id)
        assertNull(database.giftDao().findById(gift.id, alice)!!.photoPath)
        assertFalse(files.exists(photo))
        drain()

        assertNull(remotePhotoOf(gift.id))
        assertNotNull((backend.rows.getValue(gift.id).fields as RemoteGiftFields).deletedAt)
        assertTrue(remote.objects.isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a gift whose photo never left the device sends nothing to Storage`() = runTest {
        val photo = newPhoto()
        val gift = gifts.addGift(details(photo))

        gifts.deleteGift(gift.id)
        drain()

        assertTrue(remote.calls.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- local files ----

    @Test
    fun `local pruning keeps what a gift or transfer needs and recent picks, and deletes the rest`() = runTest {
        val attached = newPhoto()
        gifts.addGift(details(attached))
        val pending = newPhoto()
        gifts.addGift(details(pending))
        val abandoned = newPhoto()

        clock.advanceBy(Duration.ofHours(2))
        photos.pruneLocal(alice)
        assertTrue("an editor may still hold a recent pick", files.exists(abandoned))

        clock.advanceBy(Duration.ofDays(2))
        photos.pruneLocal(alice)
        assertFalse(files.exists(abandoned))
        assertTrue(files.exists(attached))
        assertTrue(files.exists(pending))
        assertNotNull(transfers.find(pending, alice))
    }

    /** One drain in the coordinator's order; returns the photo failures worth retrying. */
    private suspend fun drain(): Int {
        val uploads = photos.upload(account)
        runner.run(account)
        val deletes = photos.deleteUnused(account)
        photos.pruneLocal(alice)
        return uploads.transientFailures + deletes.transientFailures
    }

    /** New instances over the same database and files, as after the process was restarted. */
    private fun restart() {
        photos = GiftPhotoSync(transfers, database.giftDao(), database.personDao(), database.syncOperationDao(), files, remote, clock)
        runner = SyncRunner(
            database.syncOperationDao(),
            SyncDependencies(database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao()),
            SyncPusher(
                database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
                database.giftDao(), database.wishlistDao(), database.wishDao(),
                SyncReconciler(
                    database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
                    database.giftDao(), database.wishlistDao(), database.wishDao(), database.syncConflictDao(), clock,
                ),
                backend, backend, clock,
            ),
            FakeActiveOwner(alice),
            photos,
        )
    }

    private fun newPhoto(owner: UUID = alice): String = files.newPath(owner).also { files.write(it, BYTES) }

    private fun remotePhotoOf(giftId: UUID): String? = (backend.rows[giftId]?.fields as RemoteGiftFields?)?.photoPath

    private fun details(photoPath: String? = null) = GiftDetails(
        direction = GiftDirection.GIVEN,
        giverPersonId = null,
        recipientPersonId = anna,
        occasionId = null,
        name = "Book",
        givenAt = LocalDate.of(2026, 5, 12),
        price = null,
        productUrl = null,
        note = null,
        photoPath = photoPath,
    )

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
