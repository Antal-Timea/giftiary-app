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
import app.giftiary.data.remote.dto.RemoteGiftIdeaFields
import app.giftiary.data.repository.GiftIdeaRepositoryImpl
import app.giftiary.data.repository.GiftRepositoryImpl
import app.giftiary.data.repository.PersonRepositoryImpl
import app.giftiary.data.sync.FakeBackend
import app.giftiary.data.sync.FakeBackend.Behavior
import app.giftiary.data.sync.SyncDependencies
import app.giftiary.data.sync.SyncPusher
import app.giftiary.data.sync.SyncRequester
import app.giftiary.data.sync.SyncRunner
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.PhotoEdit
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.GiftIdeaAlreadyGivenException
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeGiftPhotoRemote
import app.giftiary.testing.MutableClock
import kotlinx.coroutines.flow.first
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
 * A gift idea's photo, which shares the other photos' bucket, transfers and drain: what an idea's
 * writes queue, how marking it as given hands the photo to the gift, and a drain in the
 * coordinator's order against [FakeGiftPhotoRemote] and [FakeBackend].
 */
@RunWith(RobolectricTestRunner::class)
class GiftIdeaPhotoSyncTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val account = RemoteAccount.capture(alice, token(alice))
    private val givenAt = LocalDate.of(2026, 12, 24)

    // Photo files carry real modification times, so this clock starts at real time.
    private val clock = MutableClock(Instant.now())
    private val files = GiftPhotoFiles(ApplicationProvider.getApplicationContext())
    private val remote = FakeGiftPhotoRemote()
    private lateinit var database: GiftiaryDatabase
    private lateinit var backend: FakeBackend
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var photos: GiftPhotoSync
    private lateinit var runner: SyncRunner
    private lateinit var annaId: UUID

    private val transfers get() = database.photoTransferDao()
    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
        backend = FakeBackend(database)
        start()
        val people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), SyncOperationQueue(operations, clock), FakeActiveOwner(alice),
            clock, SyncRequester {}, transfers, files,
        )
        annaId = people.addPerson("Anna", null).id
    }

    /** Repositories and drain over [database] and [files]; again, as after the process was restarted. */
    private fun start() {
        val queue = SyncOperationQueue(operations, clock)
        val activeOwner = FakeActiveOwner(alice)
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner,
            clock, SyncRequester {}, transfers, files,
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, activeOwner, clock, SyncRequester {},
            transfers, files,
        )
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
    fun `an idea without a photo queues no Storage work and syncs as before`() = runTest {
        val idea = ideas.addGiftIdea(details("Scarf"))

        assertNull(idea.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        drain()
        assertNotNull(backend.rows[idea.id])
        assertNull(remotePhotoOf(idea.id))
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `an idea saved with a photo stores the row, its upload and its create together`() = runTest {
        val photo = newPhoto()

        val idea = ideas.addGiftIdea(details("Scarf", photo))

        assertEquals(photo, idea.photoPath)
        assertEquals(photo, ideas.getGiftIdea(idea.id)!!.idea.photoPath)
        val upload = transfers.find(photo, alice)!!
        assertEquals(PhotoTransferKind.UPLOAD, upload.kind)
        assertEquals(SyncEntityType.GIFT_IDEA, upload.entityType)
        assertEquals(idea.id, upload.entityId)
        assertEquals(SyncOperation.CREATE, operations.findForEntity(SyncEntityType.GIFT_IDEA, idea.id)!!.operation)
    }

    @Test
    fun `a photo of another account, or no longer on this device, writes no idea, transfer or operation`() = runTest {
        val bobs = newPhoto(owner = bob)
        val missing = files.newPath(alice)
        val before = operations.getAllForOwner(alice).map { it.entityId }.toSet()

        listOf(bobs, missing, "https://example.com/a.jpg").forEach { path ->
            val failure = runCatching { ideas.addGiftIdea(details("Scarf", path)) }.exceptionOrNull()
            assertTrue("$path: $failure", failure is GiftPhotoUnavailableException)
        }
        val scarf = ideas.addGiftIdea(details("Scarf"))
        val failure = runCatching { ideas.updateGiftIdea(scarf.id, details("Scarf", bobs)) }.exceptionOrNull()

        assertTrue(failure is GiftPhotoUnavailableException)
        assertNull(ideas.getGiftIdea(scarf.id)!!.idea.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        assertEquals(before + scarf.id, operations.getAllForOwner(alice).map { it.entityId }.toSet())
    }

    @Test
    fun `a photo added to an existing idea later is uploaded before the idea names it`() = runTest {
        val idea = ideas.addGiftIdea(details("Scarf"))
        drain()
        val photo = newPhoto()

        ideas.updateGiftIdea(idea.id, details("Scarf", photo))
        assertEquals(SyncOperation.UPDATE, operations.findForEntity(SyncEntityType.GIFT_IDEA, idea.id)!!.operation)
        drain()

        assertEquals(listOf("upload $photo"), remote.calls)
        assertEquals(photo, remotePhotoOf(idea.id))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `a save that leaves the photo unchanged keeps one another device changed meanwhile`() = runTest {
        val first = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", first))
        drain()
        // What a pull leaves after another device replaced the photo while the form was open here.
        val synced = newPhoto()
        database.giftIdeaDao().update(database.giftIdeaDao().findById(idea.id, alice)!!.copy(photoPath = synced))
        val transfersBefore = transfers.getPaths(alice)

        ideas.updateGiftIdea(idea.id, details("Green scarf"))

        assertEquals(synced, ideas.getGiftIdea(idea.id)!!.idea.photoPath)
        assertEquals("Green scarf", ideas.getGiftIdea(idea.id)!!.idea.name)
        assertEquals("no photo work is queued", transfersBefore, transfers.getPaths(alice))
        assertTrue(files.exists(synced))

        // And one removed elsewhere stays removed.
        database.giftIdeaDao().update(database.giftIdeaDao().findById(idea.id, alice)!!.copy(photoPath = null))
        ideas.updateGiftIdea(idea.id, details("Wool scarf"))
        assertNull(ideas.getGiftIdea(idea.id)!!.idea.photoPath)
        assertEquals(transfersBefore, transfers.getPaths(alice))
    }

    @Test
    fun `the photo stays through other edits and every status change`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        drain()

        ideas.updateGiftIdea(idea.id, details("Green scarf", photo, GiftIdeaStatus.PLANNED))
        GiftIdeaStatus.entries.forEach { status -> ideas.updateStatus(idea.id, status) }
        drain()

        assertEquals(photo, ideas.getGiftIdea(idea.id)!!.idea.photoPath)
        assertEquals(photo, remotePhotoOf(idea.id))
        assertEquals("uploaded once, never deleted", listOf("upload $photo"), remote.calls)
        assertTrue(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- offline: upload before the row ----

    @Test
    fun `offline, the idea and its photo survive a restart and sync once the upload succeeds`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        remote.failure = RemoteFailure.NETWORK

        assertEquals(1, drain())
        assertNull("the backend never names a missing object", backend.rows[idea.id])
        start()
        assertEquals(1, drain())
        assertNotNull(operations.findForEntity(SyncEntityType.GIFT_IDEA, idea.id))
        assertTrue("the local photo stays usable", files.exists(photo))
        assertEquals(photo, ideas.getGiftIdea(idea.id)!!.idea.photoPath)

        remote.failure = null
        assertEquals(0, drain())
        assertEquals(photo, remotePhotoOf(idea.id))
        assertEquals(setOf(photo), remote.objects.keys)
        assertTrue(operations.getAllForOwner(alice).isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- replacement, removal and deletion ----

    @Test
    fun `a replaced photo's object is deleted only after the idea naming the new one is pushed`() = runTest {
        val old = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", old))
        drain()
        val new = newPhoto()

        ideas.updateGiftIdea(idea.id, details("Scarf", new))
        assertFalse("the old file leaves this device at once", files.exists(old))
        assertEquals(PhotoTransferKind.DELETE, transfers.find(old, alice)!!.kind)

        backend.script += Behavior.Fail(RemoteFailure.SERVER)
        drain()
        assertEquals("the backend still names the old photo", old, remotePhotoOf(idea.id))
        assertTrue(remote.objects.containsKey(old))

        drain()
        assertEquals(new, remotePhotoOf(idea.id))
        assertEquals(setOf(new), remote.objects.keys)
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `a removed photo's object is deleted once the idea without it is pushed`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        drain()

        ideas.updateGiftIdea(idea.id, details("Scarf").copy(photo = PhotoEdit.Remove))
        assertNull(ideas.getGiftIdea(idea.id)!!.idea.photoPath)
        drain()

        assertNull(remotePhotoOf(idea.id))
        assertTrue(remote.objects.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `deleting an idea deletes its photo, and its tombstone names none`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        drain()

        ideas.deleteGiftIdea(idea.id)
        assertNull(database.giftIdeaDao().findById(idea.id, alice)!!.photoPath)
        assertFalse(files.exists(photo))
        drain()

        assertNull(remotePhotoOf(idea.id))
        assertNotNull((backend.rows.getValue(idea.id).fields as RemoteGiftIdeaFields).deletedAt)
        assertTrue(remote.objects.isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    // ---- marking as given ----

    @Test
    fun `a given idea's synced photo becomes the gift's, with nothing uploaded or deleted again`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        drain()

        val gift = ideas.markAsGiven(idea.id, givenAt, null)

        assertEquals(photo, gift.photoPath)
        assertEquals(photo, gifts.getGift(gift.id)!!.gift.photoPath)
        assertNull("only the gift names it now", database.giftIdeaDao().findById(idea.id, alice)!!.photoPath)
        assertTrue(transfers.getPaths(alice).isEmpty())
        drain()

        assertEquals(photo, remoteGiftPhotoOf(gift.id))
        assertNull(remotePhotoOf(idea.id))
        assertEquals(listOf("upload $photo"), remote.calls)
        assertEquals(setOf(photo), remote.objects.keys)
        assertTrue(files.exists(photo))
    }

    @Test
    fun `a given idea's photo not uploaded yet is uploaded as the gift's, and the gift waits for it`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))

        val gift = ideas.markAsGiven(idea.id, givenAt, null)
        val upload = transfers.find(photo, alice)!!
        assertEquals(SyncEntityType.GIFT, upload.entityType)
        assertEquals(gift.id, upload.entityId)

        remote.failure = RemoteFailure.NETWORK
        drain()
        assertNull("the backend never names a missing object", backend.rows[gift.id])
        start()
        remote.failure = null
        drain()

        assertEquals(photo, remoteGiftPhotoOf(gift.id))
        assertNull(remotePhotoOf(idea.id))
        assertEquals(setOf(photo), remote.objects.keys)
        assertTrue(operations.getAllForOwner(alice).isEmpty())
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `marking an idea as given again adds no second gift and leaves the photo alone`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        val gift = ideas.markAsGiven(idea.id, givenAt, null)

        val again = runCatching { ideas.markAsGiven(idea.id, givenAt, null) }.exceptionOrNull()
        drain()

        assertTrue(again is GiftIdeaAlreadyGivenException)
        assertEquals(listOf(gift.id), database.giftDao().observeActive(alice).first().map { it.gift.id })
        assertEquals(setOf(photo), remote.objects.keys)
        assertTrue(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `local pruning keeps an idea's photo and a given idea's photo now on its gift`() = runTest {
        val kept = newPhoto()
        ideas.addGiftIdea(details("Scarf", kept))
        val given = newPhoto()
        val idea = ideas.addGiftIdea(details("Mug", given))
        ideas.markAsGiven(idea.id, givenAt, null)
        drain()
        val abandoned = newPhoto()

        clock.advanceBy(Duration.ofDays(3))
        photos.pruneLocal(alice)

        assertTrue(files.exists(kept))
        assertTrue(files.exists(given))
        assertFalse(files.exists(abandoned))
    }

    @Test
    fun `deleting the gift deletes the photo it took over from its idea`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        val gift = ideas.markAsGiven(idea.id, givenAt, null)
        drain()

        gifts.deleteGift(gift.id)
        drain()

        assertTrue(remote.objects.isEmpty())
        assertFalse(files.exists(photo))
        assertTrue(transfers.getPaths(alice).isEmpty())
    }

    @Test
    fun `a photo an idea still names survives another row letting go of it`() = runTest {
        val photo = newPhoto()
        val idea = ideas.addGiftIdea(details("Scarf", photo))
        drain()
        // What a pull can leave: a gift naming the same object, written as the backend has it.
        val gift = gifts.addGift(giftDetails())
        database.giftDao().update(database.giftDao().findById(gift.id, alice)!!.copy(photoPath = photo))

        gifts.updateGift(gift.id, giftDetails())
        drain()

        assertTrue(files.exists(photo))
        assertEquals(setOf(photo), remote.objects.keys)
        assertEquals(photo, remotePhotoOf(idea.id))
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

    private fun remotePhotoOf(ideaId: UUID): String? = (backend.rows[ideaId]?.fields as RemoteGiftIdeaFields?)?.photoPath

    private fun remoteGiftPhotoOf(giftId: UUID): String? = (backend.rows[giftId]?.fields as RemoteGiftFields?)?.photoPath

    private fun details(name: String, photoPath: String? = null, status: GiftIdeaStatus = GiftIdeaStatus.IDEA) = GiftIdeaDetails(
        recipientPersonId = annaId,
        occasionId = null,
        name = name,
        status = status,
        estimatedPrice = null,
        actualPrice = null,
        productUrl = null,
        note = null,
        photo = photoPath?.let(PhotoEdit::Replace) ?: PhotoEdit.Unchanged,
    )

    private fun giftDetails() = GiftDetails(
        direction = GiftDirection.GIVEN,
        giverPersonId = null,
        recipientPersonId = annaId,
        occasionId = null,
        name = "Scarf",
        givenAt = givenAt,
        price = null,
        productUrl = null,
        note = null,
    )

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
