package app.giftiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.GiftEntity
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.sync.BlockReason
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftIdeaAlreadyGivenException
import app.giftiary.domain.repository.GiftIdeaDetails
import app.giftiary.domain.repository.GiftIdeaNotFoundException
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OccasionNotFoundException
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.MutableClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@RunWith(RobolectricTestRunner::class)
class GiftIdeaRepositoryImplTest {

    private val ownerUserId = UUID.randomUUID()
    private val otherOwnerId = UUID.randomUUID()
    private val clock = MutableClock(Instant.parse("2026-05-01T12:00:00.123Z"))
    private lateinit var database: GiftiaryDatabase
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl

    private val ideaDao get() = database.giftIdeaDao()
    private val syncDao get() = database.syncOperationDao()

    private lateinit var anna: Person
    private lateinit var bela: Person

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftiaryDatabase::class.java,
        ).build()
        val syncQueue = SyncOperationQueue(database.syncOperationDao(), clock)
        val activeOwner = FakeActiveOwner(ownerUserId)
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(),
            syncQueue, activeOwner, clock,
            SyncRequester {},
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), syncQueue, activeOwner, clock,
            SyncRequester {},
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), syncQueue, activeOwner, clock, SyncRequester {},
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        occasions = OccasionRepositoryImpl(
            database, database.occasionDao(), database.personDao(), syncQueue, activeOwner, clock, SyncRequester {},
        )
        anna = people.addPerson("Anna", "Sister")
        bela = people.addPerson("Béla", null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Writes and sync queue ---

    @Test
    fun `adding an idea stores a local-only row and queues its creation`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        val entity = ideaDao.findActiveById(idea.id, ownerUserId)!!
        assertEquals(RemoteState.LOCAL_ONLY, entity.remoteState)
        assertEquals(0L, entity.version)
        assertEquals(anna, ideas.getGiftIdea(idea.id)?.recipient)
        assertEquals(SyncOperation.CREATE, pendingIdeaOperation(idea.id))
    }

    @Test
    fun `editing a local-only idea keeps the pending create`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        clock.advanceBy(Duration.ofMinutes(1))

        ideas.updateGiftIdea(idea.id, ideaFor(anna.id).copy(name = "Wool scarf"))

        val updated = ideas.getGiftIdea(idea.id)!!.idea
        assertEquals("Wool scarf", updated.name)
        assertEquals(clock.instant, updated.updatedAt)
        assertEquals(SyncOperation.CREATE, pendingIdeaOperation(idea.id))
    }

    @Test
    fun `editing a synced idea keeps its sync metadata and queues an update`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)

        ideas.updateGiftIdea(idea.id, ideaFor(anna.id).copy(note = "Green"))

        val entity = ideaDao.findActiveById(idea.id, ownerUserId)!!
        assertEquals(RemoteState.SYNCED, entity.remoteState)
        assertEquals(2L, entity.version)
        assertEquals(SyncOperation.UPDATE, pendingIdeaOperation(idea.id))
    }

    @Test
    fun `status can move forward and back and queues an update`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)

        ideas.updateStatus(idea.id, GiftIdeaStatus.PURCHASED)
        assertEquals(GiftIdeaStatus.PURCHASED, ideas.getGiftIdea(idea.id)?.idea?.status)
        ideas.updateStatus(idea.id, GiftIdeaStatus.PLANNED)

        assertEquals(GiftIdeaStatus.PLANNED, ideas.getGiftIdea(idea.id)?.idea?.status)
        assertEquals(SyncOperation.UPDATE, pendingIdeaOperation(idea.id))
    }

    @Test
    fun `deleting a local-only idea keeps its create as a tombstone, a synced one queues a delete`() = runTest {
        val localOnly = ideas.addGiftIdea(ideaFor(anna.id))
        val synced = ideas.addGiftIdea(ideaFor(bela.id))
        markSynced(synced.id)

        ideas.deleteGiftIdea(localOnly.id)
        ideas.deleteGiftIdea(synced.id)

        assertTrue(ideas.observeGiftIdeas().first().isEmpty())
        assertEquals(SyncOperation.CREATE, pendingIdeaOperation(localOnly.id))
        assertEquals(SyncOperation.DELETE, pendingIdeaOperation(synced.id))
    }

    @Test
    fun `a failing queue write rolls back the idea change`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)
        insertInconsistentPendingDelete(idea.id)

        val result = runCatching { ideas.updateStatus(idea.id, GiftIdeaStatus.PLANNED) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(GiftIdeaStatus.IDEA, ideas.getGiftIdea(idea.id)?.idea?.status)
    }

    // --- References ---

    @Test
    fun `an unknown, foreign or deleted recipient is rejected`() = runTest {
        val stranger = insertStranger()
        people.deletePerson(bela.id)

        listOf(UUID.randomUUID(), stranger.id, bela.id).forEach { recipient ->
            val result = runCatching { ideas.addGiftIdea(ideaFor(recipient)) }
            assertTrue(result.exceptionOrNull() is PersonNotFoundException)
        }
        assertTrue(ideas.observeGiftIdeas().first().isEmpty())
    }

    @Test
    fun `a deleted person cannot be newly assigned`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        people.deletePerson(bela.id)

        val result = runCatching { ideas.updateGiftIdea(idea.id, ideaFor(bela.id)) }

        assertTrue(result.exceptionOrNull() is PersonNotFoundException)
        assertEquals(anna.id, ideas.getGiftIdea(idea.id)?.idea?.recipientPersonId)
    }

    @Test
    fun `occasions must be the recipient's own active ones`() = runTest {
        val annasBirthday = occasions.addOccasion(anna.id, birthday)
        val belasBirthday = occasions.addOccasion(bela.id, birthday)
        val deletedOccasion = occasions.addOccasion(anna.id, birthday.copy(type = OccasionType.NAME_DAY))
        occasions.deleteOccasion(deletedOccasion.id)

        val valid = ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = annasBirthday.id))
        listOf(UUID.randomUUID(), belasBirthday.id, deletedOccasion.id).forEach { occasionId ->
            val result = runCatching { ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = occasionId)) }
            assertTrue(result.exceptionOrNull() is OccasionNotFoundException)
        }
        assertEquals(annasBirthday, ideas.getGiftIdea(valid.id)?.occasion)
    }

    @Test
    fun `an idea keeps its deleted recipient and occasion when edited without changing them`() = runTest {
        val annasBirthday = occasions.addOccasion(anna.id, birthday)
        val idea = ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = annasBirthday.id))
        people.deletePerson(anna.id) // also deletes her birthday

        ideas.updateGiftIdea(idea.id, ideaFor(anna.id).copy(occasionId = annasBirthday.id, name = "Wool scarf"))

        val entry = ideas.getGiftIdea(idea.id)!!
        assertEquals("Wool scarf", entry.idea.name)
        assertEquals(anna, entry.recipient)
        assertEquals(annasBirthday.id, entry.occasion?.id)
    }

    @Test
    fun `deleting a person keeps their ideas, still showing who they were for`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        people.deletePerson(anna.id)

        assertEquals(anna, ideas.observeGiftIdeas().first().single { it.idea.id == idea.id }.recipient)
        assertEquals(1, ideas.observeGiftIdeasForPerson(anna.id).first().size)
        assertEquals(SyncOperation.CREATE, pendingIdeaOperation(idea.id))
    }

    @Test
    fun `another owner's idea is never returned, changed or deleted`() = runTest {
        val stranger = insertStranger()
        val theirs = ideas.addGiftIdea(ideaFor(anna.id)).let { mine ->
            val entity = ideaDao.findActiveById(mine.id, ownerUserId)!!
            ideaDao.softDelete(mine.id, ownerUserId, clock.instant)
            entity.copy(id = UUID.randomUUID(), ownerUserId = otherOwnerId, recipientPersonId = stranger.id)
        }
        ideaDao.insert(theirs)

        assertTrue(ideas.observeGiftIdeas().first().isEmpty())
        assertNull(ideas.getGiftIdea(theirs.id))
        assertTrue(runCatching { ideas.updateGiftIdea(theirs.id, ideaFor(anna.id)) }.exceptionOrNull() is GiftIdeaNotFoundException)
        assertTrue(runCatching { ideas.updateStatus(theirs.id, GiftIdeaStatus.PLANNED) }.exceptionOrNull() is GiftIdeaNotFoundException)
        assertTrue(runCatching { ideas.markAsGiven(theirs.id, today, null) }.exceptionOrNull() is GiftIdeaNotFoundException)
        ideas.deleteGiftIdea(theirs.id)

        assertEquals(theirs, ideaDao.findActiveById(theirs.id, otherOwnerId))
        assertTrue(gifts.observeGifts().first().isEmpty())
    }

    // --- Mark as given ---

    @Test
    fun `a general occasion fits an idea for anyone, also after the recipient changes, and carries over to the gift`() = runTest {
        val christmas = occasions.addOccasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))

        val idea = ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = christmas.id))
        assertEquals(christmas, ideas.getGiftIdea(idea.id)?.occasion)
        ideas.updateGiftIdea(idea.id, ideaFor(bela.id).copy(occasionId = christmas.id))
        assertEquals(christmas.id, ideas.getGiftIdea(idea.id)?.idea?.occasionId)

        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 12, 24), null)

        assertEquals(christmas.id, gifts.getGift(gift.id)?.gift?.occasionId)
        assertEquals(bela.id, gifts.getGift(gift.id)?.gift?.recipientPersonId)
    }

    @Test
    fun `marking as given creates exactly one given gift that copies the idea`() = runTest {
        val annasBirthday = occasions.addOccasion(anna.id, birthday)
        val idea = ideas.addGiftIdea(
            ideaFor(anna.id).copy(
                occasionId = annasBirthday.id,
                productUrl = "https://example.com/scarf",
                note = "Green",
            ),
        )

        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 3, 12), Money(9900, "HUF"))

        val history = gifts.observeGifts().first()
        assertEquals(listOf(gift.id), history.map { it.gift.id })
        val stored = history.single().gift
        assertEquals(GiftDirection.GIVEN, stored.direction)
        assertNull(stored.giverPersonId)
        assertEquals(anna.id, stored.recipientPersonId)
        assertEquals(annasBirthday.id, stored.occasionId)
        assertEquals(idea.id, stored.sourceGiftIdeaId)
        assertEquals("Scarf", stored.name)
        assertEquals("https://example.com/scarf", stored.productUrl)
        assertEquals("Green", stored.note)
        assertEquals(LocalDate.of(2026, 3, 12), stored.givenAt)
        assertEquals(Money(9900, "HUF"), stored.price)
    }

    @Test
    fun `the given gift's price is exactly the chosen one, including none`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id).copy(estimatedPrice = Money(10000, "HUF")))

        val gift = ideas.markAsGiven(idea.id, today, price = null)

        assertNull(gifts.getGift(gift.id)?.gift?.price)
    }

    @Test
    fun `marking as given archives the idea instead of deleting it and keeps its prices`() = runTest {
        val idea = ideas.addGiftIdea(
            ideaFor(anna.id).copy(estimatedPrice = Money(10000, "HUF"), actualPrice = Money(9900, "HUF")),
        )
        clock.advanceBy(Duration.ofMinutes(1))

        ideas.markAsGiven(idea.id, today, Money(9900, "HUF"))

        val archived = ideaDao.findNotDeletedById(idea.id, ownerUserId)!!
        assertEquals(clock.instant, archived.archivedAt)
        assertEquals(clock.instant, archived.updatedAt)
        assertNull(archived.deletedAt)
        assertEquals(10000L, archived.estimatedPriceAmountMinor)
        assertEquals(9900L, archived.actualPriceAmountMinor)
        assertNull(ideas.getGiftIdea(idea.id))
        assertTrue(ideas.observeGiftIdeas().first().isEmpty())
        assertTrue(ideas.observeGiftIdeasForPerson(anna.id).first().isEmpty())
    }

    @Test
    fun `a never-synced idea keeps its pending create so the backend learns about it`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))

        val gift = ideas.markAsGiven(idea.id, today, null)

        assertEquals(SyncOperation.CREATE, pendingIdeaOperation(idea.id))
        val giftOperation = syncDao.findForEntity(SyncEntityType.GIFT, gift.id)!!
        assertEquals(SyncOperation.CREATE, giftOperation.operation)
        val giftEntity = database.giftDao().findActiveById(gift.id, ownerUserId)!!
        assertEquals(RemoteState.LOCAL_ONLY, giftEntity.remoteState)
        assertEquals(0L, giftEntity.version)
        // The gift's operation belongs to the gift's owner, the same owner as the idea it came from.
        assertEquals(giftEntity.ownerUserId, giftOperation.ownerUserId)
        assertEquals(ownerUserId, giftOperation.ownerUserId)
    }

    @Test
    fun `marking as given is two mutations, each with its own new id`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)
        ideas.updateStatus(idea.id, GiftIdeaStatus.PURCHASED)
        val ideaUpdate = syncDao.findForEntity(SyncEntityType.GIFT_IDEA, idea.id)!!

        val gift = ideas.markAsGiven(idea.id, today, null)

        val archive = syncDao.findForEntity(SyncEntityType.GIFT_IDEA, idea.id)!!
        val giftCreate = syncDao.findForEntity(SyncEntityType.GIFT, gift.id)!!
        assertEquals(SyncOperation.UPDATE, archive.operation)
        assertEquals(SyncOperation.CREATE, giftCreate.operation)
        assertEquals(3, setOf(ideaUpdate.mutationId, archive.mutationId, giftCreate.mutationId).size)
        assertNull(archive.inFlightMutationId)
        assertNull(giftCreate.inFlightMutationId)
    }

    @Test
    fun `a synced idea gets an update when given`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)

        val gift = ideas.markAsGiven(idea.id, today, null)

        assertEquals(SyncOperation.UPDATE, pendingIdeaOperation(idea.id))
        assertEquals(SyncOperation.CREATE, syncDao.findForEntity(SyncEntityType.GIFT, gift.id)?.operation)
    }

    @Test
    fun `an idea cannot be given twice`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        ideas.markAsGiven(idea.id, today, null)

        val second = runCatching { ideas.markAsGiven(idea.id, today, null) }

        assertTrue(second.exceptionOrNull() is GiftIdeaAlreadyGivenException)
        assertEquals(1, gifts.observeGifts().first().size)
    }

    @Test
    fun `a queue failure rolls back both the gift and the archive`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        markSynced(idea.id)
        insertInconsistentPendingDelete(idea.id)

        val result = runCatching { ideas.markAsGiven(idea.id, today, null) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(gifts.observeGifts().first().isEmpty())
        assertNotNull(ideas.getGiftIdea(idea.id))
        assertNull(ideaDao.findNotDeletedById(idea.id, ownerUserId)?.archivedAt)
    }

    @Test
    fun `a deleted or unknown idea cannot be given`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        ideas.deleteGiftIdea(idea.id)

        listOf(idea.id, UUID.randomUUID()).forEach { id ->
            assertTrue(runCatching { ideas.markAsGiven(id, today, null) }.exceptionOrNull() is GiftIdeaNotFoundException)
        }
        assertTrue(gifts.observeGifts().first().isEmpty())
    }

    @Test
    fun `an idea for a person deleted since can still be given`() = runTest {
        val annasBirthday = occasions.addOccasion(anna.id, birthday)
        val idea = ideas.addGiftIdea(ideaFor(anna.id).copy(occasionId = annasBirthday.id))
        people.deletePerson(anna.id)

        val gift = ideas.markAsGiven(idea.id, today, null)

        val entry = gifts.getGift(gift.id)!!
        assertEquals(anna, entry.recipient)
        assertEquals(annasBirthday.id, entry.occasion?.id)
        assertTrue(people.observePeople().first().none { it.id == anna.id })
    }

    // --- Given through its gift ---

    @Test
    fun `an idea its gift was made from stays given when a sync reopens it`() = runTest {
        val (idea, _) = givenThenReopened()

        assertGiven(idea)
    }

    @Test
    fun `an idea whose gift was deleted stays given, even reopened`() = runTest {
        val (idea, gift) = givenThenReopened()
        gifts.deleteGift(gift)

        assertGiven(idea)
    }

    @Test
    fun `another owner's gift naming the idea does not make it given`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        val stranger = insertStranger()
        database.giftDao().insert(
            GiftEntity(
                id = UUID.randomUUID(), ownerUserId = otherOwnerId, direction = GiftDirection.GIVEN, giverPersonId = null,
                recipientPersonId = stranger.id, occasionId = null, sourceGiftIdeaId = idea.id, name = "Theirs",
                givenAt = today, priceAmountMinor = null, priceCurrencyCode = null, photoPath = null, productUrl = null,
                note = null, version = 0, remoteState = RemoteState.LOCAL_ONLY, createdAt = clock.instant,
                updatedAt = clock.instant, deletedAt = null,
            ),
        )

        assertEquals(listOf(idea.id), ideas.observeGiftIdeas().first().map { it.idea.id })
        assertEquals(listOf(idea.id), ideas.observeGiftIdeasForPerson(anna.id).first().map { it.idea.id })
        assertNotNull(ideas.getGiftIdea(idea.id))
        ideas.updateStatus(idea.id, GiftIdeaStatus.PLANNED)
        assertEquals(idea.id, ideas.markAsGiven(idea.id, today, null).sourceGiftIdeaId)
    }

    @Test
    fun `an observed idea leaves the active lists as soon as a gift made from it arrives`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        val sizes = Channel<Int>(Channel.UNLIMITED)
        val collector = launch { ideas.observeGiftIdeas().collect { sizes.send(it.size) } }
        assertEquals(1, sizes.receive())

        // As a pull would: the gift arrives on its own, the idea row is not touched.
        database.giftDao().insert(giftFrom(idea.id))

        assertEquals(0, sizes.receive())
        collector.cancel()
        assertNull(ideaDao.findNotDeletedById(idea.id, ownerUserId)?.archivedAt)
    }

    @Test
    fun `an idea given through its gift cannot be edited, restatused or deleted`() = runTest {
        val (idea, _) = givenThenReopened()
        val entity = ideaDao.findNotDeletedById(idea, ownerUserId)
        val queue = syncDao.getAllForOwner(ownerUserId)

        assertTrue(runCatching { ideas.updateGiftIdea(idea, ideaFor(anna.id).copy(name = "Changed")) }.exceptionOrNull() is GiftIdeaNotFoundException)
        assertTrue(runCatching { ideas.updateStatus(idea, GiftIdeaStatus.PURCHASED) }.exceptionOrNull() is GiftIdeaNotFoundException)
        ideas.deleteGiftIdea(idea)

        assertEquals(entity, ideaDao.findNotDeletedById(idea, ownerUserId))
        assertEquals(queue, syncDao.getAllForOwner(ownerUserId))
    }

    private val today = LocalDate.of(2026, 5, 1)
    private val birthday = OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true)

    private fun ideaFor(personId: UUID) = GiftIdeaDetails(
        recipientPersonId = personId,
        occasionId = null,
        name = "Scarf",
        status = GiftIdeaStatus.IDEA,
        estimatedPrice = null,
        actualPrice = null,
        productUrl = null,
        note = null,
    )

    /** Hidden, not convertible, and nothing written when trying. */
    private suspend fun assertGiven(idea: UUID) {
        val gifts = database.giftDao().observeActive(ownerUserId).first()
        val queue = syncDao.getAllForOwner(ownerUserId)

        assertTrue(ideas.observeGiftIdeas().first().none { it.idea.id == idea })
        assertTrue(ideas.observeGiftIdeasForPerson(anna.id).first().none { it.idea.id == idea })
        assertNull(ideas.getGiftIdea(idea))
        assertTrue(runCatching { ideas.markAsGiven(idea, today, null) }.exceptionOrNull() is GiftIdeaAlreadyGivenException)

        assertEquals(gifts, database.giftDao().observeActive(ownerUserId).first())
        assertEquals(queue, syncDao.getAllForOwner(ownerUserId))
        assertFalse("not repaired", ideaDao.findNotDeletedById(idea, ownerUserId)?.archivedAt != null)
    }

    /**
     * An idea given here, then reopened the way a pull does after a conflict: the idea row is
     * open again while its gift stays. Returns the idea and the gift.
     */
    private suspend fun givenThenReopened(): Pair<UUID, UUID> {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        val gift = ideas.markAsGiven(idea.id, today, null)
        ideaDao.update(ideaDao.findNotDeletedById(idea.id, ownerUserId)!!.copy(archivedAt = null))
        return idea.id to gift.id
    }

    private fun giftFrom(ideaId: UUID) = GiftEntity(
        id = UUID.randomUUID(), ownerUserId = ownerUserId, direction = GiftDirection.GIVEN, giverPersonId = null,
        recipientPersonId = anna.id, occasionId = null, sourceGiftIdeaId = ideaId, name = "Scarf", givenAt = today,
        priceAmountMinor = null, priceCurrencyCode = null, photoPath = null, productUrl = null, note = null, version = 1,
        remoteState = RemoteState.SYNCED, createdAt = clock.instant, updatedAt = clock.instant, deletedAt = null,
    )

    private suspend fun pendingIdeaOperation(ideaId: UUID): SyncOperation? =
        syncDao.findForEntity(SyncEntityType.GIFT_IDEA, ideaId)
            ?.also { assertEquals("an operation has its entity's owner", ownerUserId, it.ownerUserId) }
            ?.operation

    private suspend fun markSynced(ideaId: UUID) {
        val entity = ideaDao.findActiveById(ideaId, ownerUserId)!!
        ideaDao.update(entity.copy(remoteState = RemoteState.SYNCED, version = 2))
        syncDao.findForEntity(SyncEntityType.GIFT_IDEA, ideaId)?.let { syncDao.delete(it) }
    }

    // A pending DELETE for an active idea is inconsistent, so the queue refuses any further operation.
    private suspend fun insertInconsistentPendingDelete(ideaId: UUID) {
        syncDao.insert(
            SyncOperationEntity(
                id = UUID.randomUUID(),
                ownerUserId = ownerUserId,                entityType = SyncEntityType.GIFT_IDEA,
                entityId = ideaId,
                operation = SyncOperation.DELETE,
                mutationId = UUID.randomUUID(),
                inFlightMutationId = null,
                blockedReason = null,
                createdAt = clock.instant,
                attemptCount = 0,
                lastAttemptAt = null,
                lastErrorCode = null,
            ),
        )
    }

    private suspend fun insertStranger(): PersonEntity {
        val stranger = PersonEntity(
            id = UUID.randomUUID(),
            ownerUserId = otherOwnerId,
            name = "Stranger",
            relationship = null,
            photoPath = null,
            linkedUserId = null,
            version = 0,
            remoteState = RemoteState.LOCAL_ONLY,
            createdAt = clock.instant,
            updatedAt = clock.instant,
            deletedAt = null,
        )
        database.personDao().insert(stranger)
        return stranger
    }

    @Test
    fun `an idea waits to sync while its own change is queued, not while it is blocked`() = runTest {
        val idea = ideas.addGiftIdea(ideaFor(anna.id))
        val other = ideas.addGiftIdea(ideaFor(anna.id))
        assertTrue(ideas.observeWaitingToSync(idea.id).first())

        val operation = syncDao.findForEntity(SyncEntityType.GIFT_IDEA, idea.id)!!
        syncDao.update(operation.copy(blockedReason = BlockReason.REJECTED.name))
        assertFalse("a blocked change needs attention instead", ideas.observeWaitingToSync(idea.id).first())

        syncDao.delete(operation)
        assertFalse("another idea's change is not this one's", ideas.observeWaitingToSync(idea.id).first())
        assertTrue(ideas.observeWaitingToSync(other.id).first())
    }
}
