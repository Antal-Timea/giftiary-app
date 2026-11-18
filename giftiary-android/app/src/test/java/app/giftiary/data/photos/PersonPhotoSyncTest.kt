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
import app.giftiary.data.remote.dto.RemoteGiftFields
import app.giftiary.data.remote.dto.RemotePersonFields
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
 * A person's photo, which shares the gift photos' bucket, transfers and drain: the transfers a
 * person's writes queue, and a drain in the coordinator's order against [FakeGiftPhotoRemote] and
 * [FakeBackend].
 */
@RunWith(RobolectricTestRunner::class)
class PersonPhotoSyncTest {

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

    private val transfers get() = database.photoTransferDao()
    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() {
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
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- writes ----

    @Test
    fun `a person saved with a photo stores the row, its upload and its create together`() = runTest {
        val photo = newPhoto()

        val anna = people.addPerson("Anna", null, photoPath = photo)

        assertEquals(photo, database.personDao().findActiveById(anna.id, alice)!!.photoPath)
        val upload = transfers.find(photo, alice)!!
        assertEquals(PhotoTransferKind.UPLOAD, upload.kind)
        assertEquals(SyncEntityType.PERSON, upload.entityType)
        assertEquals(anna.id, upload.entityId)
        assertEquals(SyncOperation.CREATE, operations.findForEntity(SyncEntityType.PERSON, anna.id)!!.operation)
    }

    @Test
    fun `a photo of another account, or no longer on this device, writes no person, transfer or operation`() = runTest {
        val bobs = newPhoto(owner = bob)
        val missing = files.newPath(alice)

        listOf(bobs, missing, "https://example.com/a.jpg").forEach { path ->
            val failure = runCatching { people.addPerson("Anna", null, photoPath = path) }.exceptionOrNull()
            assertTrue("$path: $failure", failure is GiftPhotoUnavailableException)
        }
        val anna = people.addPerson("Anna", null)
        val failure = runCatching { people.setPersonPhoto(anna.id, bobs) }.exceptionOrNull()

        assertTrue(failure is GiftPhotoUnavailableException)
        assertNull(database.personDao().findActiveById(anna.id, alice)!!.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        assertEquals(listOf(anna.id), operations.getAllForOwner(alice).map { it.entityId })
    }

    // ---- upload before the row ----

    @Test
    fun `a person is not pushed while its photo upload is pending, and nor are its children`() = runTest {
        val photo = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = photo)
        val gift = gifts.addGift(giftFor(anna.id))
        remote.failure = RemoteFailure.NETWORK

        assertEquals(1, drain())
        assertNull("the person row never names a missing object", backend.rows[anna.id])
        assertNull("a child waits for its parent as always", backend.rows[gift.id])
        assertNotNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertTrue(files.exists(photo))

        remote.failure = null
        assertEquals(0, drain())
        assertEquals(listOf("upload $photo", "upload $photo"), remote.calls)
        assertEquals(photo, remotePhotoOf(anna.id))
        assertNotNull(backend.rows[gift.id])
        assertTrue(operations.getAllForOwner(alice).isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- replacement, removal and deletion ----

    @Test
    fun `a replaced photo's object is deleted only after the person naming the new one is pushed`() = runTest {
        val old = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = old)
        drain()
        val new = newPhoto()

        people.setPersonPhoto(anna.id, new)
        assertFalse("the old file leaves this device at once", files.exists(old))
        assertEquals(SyncOperation.UPDATE, operations.findForEntity(SyncEntityType.PERSON, anna.id)!!.operation)
        assertEquals(PhotoTransferKind.DELETE, transfers.find(old, alice)!!.kind)
        assertEquals(PhotoTransferKind.UPLOAD, transfers.find(new, alice)!!.kind)

        // The new photo goes up, but the person row does not: the backend still names the old one.
        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertEquals(old, remotePhotoOf(anna.id))
        assertTrue(remote.objects.containsKey(old))
        assertTrue(remote.objects.containsKey(new))

        drain()
        assertEquals(new, remotePhotoOf(anna.id))
        assertFalse(remote.objects.containsKey(old))
        assertEquals("delete $old", remote.calls.last())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `replacing a person's photo that never left the device drops its upload`() = runTest {
        val first = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = first)
        val second = newPhoto()

        people.setPersonPhoto(anna.id, second)
        drain()

        assertEquals(listOf("upload $second"), remote.calls)
        assertFalse(files.exists(first))
        assertEquals(second, remotePhotoOf(anna.id))
    }

    @Test
    fun `a removed photo's object is deleted once the person without it is pushed`() = runTest {
        val photo = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = photo)
        drain()

        people.setPersonPhoto(anna.id, null)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertTrue("the backend's person still names it", remote.objects.containsKey(photo))

        drain()
        assertNull(remotePhotoOf(anna.id))
        assertTrue(remote.objects.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a person deletes its photo, and its tombstone names none`() = runTest {
        val photo = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = photo)
        drain()

        people.deletePerson(anna.id)
        assertNull(database.personDao().findById(anna.id, alice)!!.photoPath)
        assertFalse(files.exists(photo))
        assertEquals(SyncEntityType.PERSON, transfers.find(photo, alice)!!.entityType)
        drain()

        assertNull(remotePhotoOf(anna.id))
        assertNotNull((backend.rows.getValue(anna.id).fields as RemotePersonFields).deletedAt)
        assertTrue(remote.objects.isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting a person whose photo never left the device sends nothing to Storage`() = runTest {
        val photo = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = photo)

        people.deletePerson(anna.id)
        drain()

        assertTrue(remote.calls.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- references ----

    @Test
    fun `a photo still named by a gift keeps its file, upload and object when a person lets go of it`() = runTest {
        val photo = newPhoto()
        val anna = people.addPerson("Anna", null, photoPath = photo)
        val gift = gifts.addGift(giftFor(anna.id).copy(photoPath = photo))

        people.setPersonPhoto(anna.id, null)
        assertTrue(files.exists(photo))
        assertEquals(PhotoTransferKind.UPLOAD, transfers.find(photo, alice)!!.kind)

        drain()
        assertEquals(photo, (backend.rows.getValue(gift.id).fields as RemoteGiftFields).photoPath)
        assertTrue(remote.objects.containsKey(photo))

        gifts.updateGift(gift.id, giftFor(anna.id))
        drain()
        assertFalse("the last reference went", remote.objects.containsKey(photo))
        assertFalse(files.exists(photo))
    }

    @Test
    fun `local pruning keeps a person's photo, attached or still to upload, and deletes the rest`() = runTest {
        val attached = newPhoto()
        people.addPerson("Anna", null, photoPath = attached)
        drain()
        val pending = newPhoto()
        people.addPerson("Béla", null, photoPath = pending)
        remote.failure = RemoteFailure.NETWORK
        drain()
        val abandoned = newPhoto()

        clock.advanceBy(Duration.ofDays(3))
        photos.pruneLocal(alice)

        assertTrue(files.exists(attached))
        assertTrue(files.exists(pending))
        assertNotNull(transfers.find(pending, alice))
        assertFalse(files.exists(abandoned))
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

    private fun remotePhotoOf(personId: UUID): String? = (backend.rows[personId]?.fields as RemotePersonFields?)?.photoPath

    private fun giftFor(personId: UUID) = GiftDetails(
        direction = GiftDirection.GIVEN,
        giverPersonId = null,
        recipientPersonId = personId,
        occasionId = null,
        name = "Book",
        givenAt = LocalDate.of(2026, 5, 12),
        price = null,
        productUrl = null,
        note = null,
        photoPath = null,
    )

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
