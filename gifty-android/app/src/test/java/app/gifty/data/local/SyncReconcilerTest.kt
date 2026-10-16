package app.gifty.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncConflictEntity
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.repository.GiftIdeaRepositoryImpl
import app.gifty.data.repository.GiftRepositoryImpl
import app.gifty.data.repository.OccasionRepositoryImpl
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.sync.BlockReason
import app.gifty.data.sync.SyncRequester
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftDetails
import app.gifty.domain.repository.GiftIdeaDetails
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
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
 * Applying a committed in-flight mutation, or a proven conflict, to Room. States are built through the real repositories
 * and queue; the test only plays sync's part: marking the current mutation in flight, and
 * marking rows as known to the backend.
 */
@RunWith(RobolectricTestRunner::class)
class SyncReconcilerTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val clock = MutableClock(Instant.parse("2026-10-10T12:00:00.123Z"))
    private lateinit var database: GiftyDatabase
    private lateinit var people: PersonRepositoryImpl
    private lateinit var occasions: OccasionRepositoryImpl
    private lateinit var ideas: GiftIdeaRepositoryImpl
    private lateinit var gifts: GiftRepositoryImpl
    private lateinit var reconciler: SyncReconciler
    private lateinit var anna: Person

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        val queue = SyncOperationQueue(database.syncOperationDao(), clock)
        val owner = FakeActiveOwner(alice)
        people = PersonRepositoryImpl(database, database.personDao(), database.occasionDao(), queue, owner, clock, SyncRequester {})
        occasions = OccasionRepositoryImpl(database, database.occasionDao(), database.personDao(), queue, owner, clock, SyncRequester {})
        ideas = GiftIdeaRepositoryImpl(
            database, database.giftIdeaDao(), database.giftDao(), database.personDao(), database.occasionDao(), queue, owner, clock,
            SyncRequester {},
        )
        gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), queue, owner, clock, SyncRequester {},
        )
        reconciler = SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(), database.wishlistDao(), database.wishDao(), database.syncConflictDao(), clock,
        )
        anna = people.addPerson("Anna", "Sister")
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 1)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- the committed mutation was the latest change ----

    @Test
    fun `a committed create that is still the latest change completes, for every entity`() = runTest {
        val created = createOneOfEach()

        created.forEach { (type, id) ->
            val before = entity(type, id)
            val sent = send(type, id)

            assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, type, id, sent, resultingVersion = 1))

            assertNull("$type operation", operations.findForEntity(type, id))
            val after = entity(type, id)
            assertEquals(1L, after.version)
            assertEquals(RemoteState.SYNCED, after.remoteState)
            assertEquals("$type content untouched", before.content, after.content)
        }
    }

    @Test
    fun `a committed update that is still the latest change completes at the next version`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4))

        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertEquals(4L, entity(SyncEntityType.PERSON, anna.id).version)
        assertEquals("Anna Kovács", people.getPerson(anna.id)?.name)
    }

    @Test
    fun `a committed tombstone completes and the row stays, deleted`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 2)
        people.deletePerson(anna.id)
        val deletedAt = entity(SyncEntityType.PERSON, anna.id).deletedAt
        val sent = send(SyncEntityType.PERSON, anna.id)
        assertEquals(SyncOperation.DELETE, operations.findForEntity(SyncEntityType.PERSON, anna.id)?.operation)

        assertEquals(CommitOutcome.COMPLETED, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 3))

        val after = entity(SyncEntityType.PERSON, anna.id)
        assertNotNull(deletedAt)
        assertEquals(deletedAt, after.deletedAt)
        assertEquals(3L, after.version)
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
    }

    // ---- a newer change is pending ----

    @Test
    fun `a committed update with a newer edit pending keeps that edit queued against the new version`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        operations.update(pending(SyncEntityType.PERSON, anna.id).copy(attemptCount = 2, lastErrorCode = "timeout"))
        clock.advanceBy(Duration.ofMinutes(1))
        people.updatePerson(anna.id, "Anna K.", "Big sister")
        val edit = pending(SyncEntityType.PERSON, anna.id)

        assertEquals(
            CommitOutcome.NEWER_MUTATION_PENDING,
            reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4),
        )

        assertEquals(edit.copy(inFlightMutationId = null), pending(SyncEntityType.PERSON, anna.id))
        val after = entity(SyncEntityType.PERSON, anna.id)
        assertEquals(4L, after.version)
        assertEquals(RemoteState.SYNCED, after.remoteState)
        // The local content is the newer edit, not what the committed mutation carried.
        assertEquals("Anna K.", people.getPerson(anna.id)?.name)
    }

    @Test
    fun `failed attempts of the committed mutation do not carry over to the newer one`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        people.updatePerson(anna.id, "Anna K.", "Sister")
        // Resends of the in-flight mutation after the edit, while its outcome was unknown.
        val edit = pending(SyncEntityType.PERSON, anna.id)
        operations.update(edit.copy(attemptCount = 3, lastAttemptAt = clock.instant, lastErrorCode = "timeout"))

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4)

        assertEquals(edit.copy(inFlightMutationId = null), pending(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a committed create with a newer edit pending continues as an update, for every entity`() = runTest {
        val created = createOneOfEach()
        val sent = created.associate { (type, id) -> (type to id) to send(type, id) }
        // The user edits each entity while its create is still unresolved.
        people.updatePerson(created[0].second, "Béla B.", null)
        occasions.updateOccasion(created[1].second, birthday.copy(day = 13))
        ideas.updateStatus(created[2].second, GiftIdeaStatus.PURCHASED)
        gifts.updateGift(created[3].second, giftDetails.copy(name = "Blue scarf"))

        created.forEach { (type, id) ->
            val edit = pending(type, id)
            val before = entity(type, id)

            assertEquals(
                CommitOutcome.NEWER_MUTATION_PENDING,
                reconciler.applyCommitted(alice, type, id, sent.getValue(type to id), resultingVersion = 1),
            )

            val next = pending(type, id)
            assertEquals("$type", edit.copy(operation = SyncOperation.UPDATE, inFlightMutationId = null), next)
            assertEquals("$type keeps its newer mutation", edit.mutationId, next.mutationId)
            val after = entity(type, id)
            assertEquals(1L, after.version)
            assertEquals(RemoteState.SYNCED, after.remoteState)
            assertEquals("$type content untouched", before.content, after.content)
        }
    }

    @Test
    fun `a committed create of a person deleted meanwhile continues as the deletion`() = runTest {
        val bela = people.addPerson("Béla", null)
        val sent = send(SyncEntityType.PERSON, bela.id)
        people.deletePerson(bela.id)
        val tombstone = pending(SyncEntityType.PERSON, bela.id)
        assertEquals(SyncOperation.CREATE, tombstone.operation)

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, bela.id, sent, 1)

        assertEquals(
            tombstone.copy(operation = SyncOperation.DELETE, inFlightMutationId = null),
            pending(SyncEntityType.PERSON, bela.id),
        )
        assertNotNull(entity(SyncEntityType.PERSON, bela.id).deletedAt)
    }

    @Test
    fun `a gift deleted while its create was in flight keeps its deletion queued once the create is confirmed`() = runTest {
        val gift = gifts.addGift(giftDetails)
        val sent = send(SyncEntityType.GIFT, gift.id)
        gifts.deleteGift(gift.id)
        // Room v3 keeps the create, because the create in flight may have committed.
        val tombstone = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(SyncOperation.CREATE, tombstone.operation)
        val deletedAt = entity(SyncEntityType.GIFT, gift.id).deletedAt

        assertEquals(
            CommitOutcome.NEWER_MUTATION_PENDING,
            reconciler.applyCommitted(alice, SyncEntityType.GIFT, gift.id, sent, 1),
        )

        val next = pending(SyncEntityType.GIFT, gift.id)
        assertEquals(tombstone.copy(operation = SyncOperation.DELETE, inFlightMutationId = null), next)
        val after = entity(SyncEntityType.GIFT, gift.id)
        assertEquals(deletedAt, after.deletedAt)
        assertEquals(1L, after.version)
        assertEquals(RemoteState.SYNCED, after.remoteState)
    }

    // ---- results that do not belong to the in-flight slot ----

    @Test
    fun `a result for another mutation, a repeated result or the current mutation changes nothing`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        people.updatePerson(anna.id, "Anna K.", "Sister")
        val edit = pending(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, UUID.randomUUID(), 4))
            // The newer mutation is not in flight, so it cannot have committed.
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, edit.mutationId, 4))
        }

        reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4)
        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 4))
        }
    }

    @Test
    fun `another account's result changes nothing`() = runTest {
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            assertEquals(CommitOutcome.STALE, reconciler.applyCommitted(bob, SyncEntityType.PERSON, anna.id, sent, 2))
        }
    }

    @Test
    fun `an impossible version is refused and nothing is applied`() = runTest {
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            listOf(0L, -1L).forEach { version ->
                val impossible = runCatching { reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, version) }
                assertTrue("$version", impossible.exceptionOrNull() is IllegalArgumentException)
            }
            // Anna is at version 1, so her in-flight update can only have committed as version 2.
            val skipped = runCatching { reconciler.applyCommitted(alice, SyncEntityType.PERSON, anna.id, sent, 5) }
            assertTrue(skipped.exceptionOrNull() is IllegalStateException)
        }
    }

    // ---- a conflict ----

    @Test
    fun `a conflict keeps the entity's current state, exactly, and removes the operation in one step`() = runTest {
        val rows = syncedOneOfEach()

        rows.forEach { (type, id, json) ->
            val before = entity(type, id)
            val sent = send(type, id)

            assertTrue("$type", reconciler.settleConflict(alice, type, id, sent, wasBlocked = false, baseVersion = 3, remoteVersion = 5))

            assertNull("$type operation", operations.findForEntity(type, id))
            assertEquals("$type untouched", before, entity(type, id))
            assertEquals(
                SyncConflictEntity(
                    id = sent, ownerUserId = alice, entityType = type, entityId = id, mutationId = sent, baseVersion = 3,
                    remoteVersion = 5, localSnapshotJson = json, createdAt = clock.instant, seenAt = null,
                ),
                conflicts().single { it.entityId == id },
            )
        }
    }

    @Test
    fun `a conflict keeps the latest local state, including an edit made after the proving mutation was sent`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)
        people.deletePerson(anna.id)
        val deletedAt = entity(SyncEntityType.PERSON, anna.id).deletedAt

        assertTrue(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, sent, wasBlocked = false, baseVersion = 3, remoteVersion = 4))

        val conflict = conflicts().single()
        assertEquals(sent, conflict.mutationId)
        assertEquals(
            """{"name":"Anna Kovács","relationship":"Sister","photo_path":null,"linked_user_id":null,"deleted_at":"$deletedAt"}""",
            conflict.localSnapshotJson,
        )
        assertNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
    }

    @Test
    fun `a blocked conflict is settled only while its operation is still blocked for it, with the same mutation`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val blocked = pending(SyncEntityType.PERSON, anna.id)
        operations.blockCurrentMutation(blocked.id, blocked.mutationId, BlockReason.REMOTE_NEWER.name)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            // Taken as an in-flight result, or blocked for another reason, it is not this conflict.
            assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, blocked.mutationId, wasBlocked = false, 3, 4))
            operations.update(pending(SyncEntityType.PERSON, anna.id).copy(blockedReason = BlockReason.REJECTED.name))
            assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, blocked.mutationId, wasBlocked = true, 3, 4))
            // An operation in flight is never in the state an earlier version left a blocked conflict in.
            operations.update(pending(SyncEntityType.PERSON, anna.id).copy(inFlightMutationId = blocked.mutationId, blockedReason = BlockReason.REMOTE_NEWER.name))
            assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, blocked.mutationId, wasBlocked = true, 3, 4))
            operations.update(pending(SyncEntityType.PERSON, anna.id).copy(inFlightMutationId = null))
        }
        // A new edit unblocks the operation under a new mutation: the old evidence is about nothing current.
        people.updatePerson(anna.id, "Anna K.", "Sister")
        assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, blocked.mutationId, wasBlocked = true, 3, 4))
        assertNotNull(operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertTrue(conflicts().isEmpty())

        val current = pending(SyncEntityType.PERSON, anna.id)
        operations.blockCurrentMutation(current.id, current.mutationId, BlockReason.REMOTE_NEWER.name)
        // Blocked again for the newer mutation: the older one's evidence still settles nothing.
        assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, blocked.mutationId, wasBlocked = true, 3, 4))
        assertTrue(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, current.mutationId, wasBlocked = true, 3, 4))
        assertEquals(current.mutationId, conflicts().single().id)
    }

    @Test
    fun `a conflict for another mutation, a repeated one or another account's changes nothing`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val current = pending(SyncEntityType.PERSON, anna.id).mutationId

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            // Not in flight yet.
            assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, current, wasBlocked = false, 3, 4))
            val sent = send(SyncEntityType.PERSON, anna.id)
            assertFalse(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, UUID.randomUUID(), wasBlocked = false, 3, 4))
            assertFalse(reconciler.settleConflict(bob, SyncEntityType.PERSON, anna.id, sent, wasBlocked = false, 3, 4))
            operations.update(pending(SyncEntityType.PERSON, anna.id).copy(inFlightMutationId = null))
        }
        val sent = send(SyncEntityType.PERSON, anna.id)
        assertTrue(reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, sent, wasBlocked = false, 3, 4))
        assertFalse("repeated", reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, sent, wasBlocked = false, 3, 4))
        assertEquals(1, conflicts().size)
    }

    @Test
    fun `an impossible conflict is refused and nothing is applied`() = runTest {
        markKnownToBackend(SyncEntityType.PERSON, anna.id, version = 3)
        people.updatePerson(anna.id, "Anna Kovács", "Sister")
        val sent = send(SyncEntityType.PERSON, anna.id)

        assertUnchanged(SyncEntityType.PERSON, anna.id) {
            listOf(
                2L to 4L, // based on a version the entity is not at
                3L to 3L, // the backend not ahead
                3L to 2L,
            ).forEach { (base, remote) ->
                val refused = runCatching { reconciler.settleConflict(alice, SyncEntityType.PERSON, anna.id, sent, false, base, remote) }
                assertTrue("$base/$remote", refused.exceptionOrNull() is IllegalStateException)
            }
        }
        assertTrue(conflicts().isEmpty())
    }

    // ---- a gift refused because its idea already has a gift ----

    @Test
    fun `a duplicate gift is kept as a conflict, deleted locally and its operation removed, in one step`() = runTest {
        val (idea, gift) = givenGift()
        val sent = send(SyncEntityType.GIFT, gift)
        val before = database.giftDao().findById(gift, alice)!!

        assertTrue(reconciler.settleDuplicateGift(alice, gift, sent, idea, winnerVersion = 3))

        assertNull(operations.findForEntity(SyncEntityType.GIFT, gift))
        assertEquals(before.copy(deletedAt = clock.instant, updatedAt = clock.instant), database.giftDao().findById(gift, alice))
        val conflict = conflicts().single()
        assertEquals(sent to sent, conflict.id to conflict.mutationId)
        assertEquals(0L to 3L, conflict.baseVersion to conflict.remoteVersion)
        assertTrue(conflict.localSnapshotJson, conflict.localSnapshotJson.endsWith("\"deleted_at\":null}"))
    }

    @Test
    fun `a duplicate gift the user deleted meanwhile keeps its own deletion`() = runTest {
        val (idea, gift) = givenGift()
        val sent = send(SyncEntityType.GIFT, gift)
        clock.advanceBy(Duration.ofMinutes(1))
        gifts.deleteGift(gift)
        val deleted = database.giftDao().findById(gift, alice)!!
        clock.advanceBy(Duration.ofMinutes(1))

        assertTrue(reconciler.settleDuplicateGift(alice, gift, sent, idea, winnerVersion = 1))

        assertEquals(deleted, database.giftDao().findById(gift, alice))
        assertTrue(conflicts().single().localSnapshotJson.contains("\"deleted_at\":\"${deleted.deletedAt}\""))
    }

    @Test
    fun `a duplicate for another mutation, a repeated one or another account's changes nothing`() = runTest {
        val (idea, gift) = givenGift()

        assertUnchanged(SyncEntityType.GIFT, gift) {
            val current = pending(SyncEntityType.GIFT, gift).mutationId
            assertFalse("not in flight", reconciler.settleDuplicateGift(alice, gift, current, idea, 1))
            val sent = send(SyncEntityType.GIFT, gift)
            assertFalse(reconciler.settleDuplicateGift(alice, gift, UUID.randomUUID(), idea, 1))
            assertFalse(reconciler.settleDuplicateGift(bob, gift, sent, idea, 1))
            operations.update(pending(SyncEntityType.GIFT, gift).copy(inFlightMutationId = null))
        }
        val sent = send(SyncEntityType.GIFT, gift)
        assertTrue(reconciler.settleDuplicateGift(alice, gift, sent, idea, 1))
        assertFalse("repeated", reconciler.settleDuplicateGift(alice, gift, sent, idea, 1))
        assertEquals(1, conflicts().size)
    }

    @Test
    fun `an impossible duplicate is refused and nothing is applied`() = runTest {
        val (idea, gift) = givenGift()
        val sent = send(SyncEntityType.GIFT, gift)

        assertUnchanged(SyncEntityType.GIFT, gift) {
            // Another idea, a backend version no row has, a gift the backend already knows.
            assertTrue(runCatching { reconciler.settleDuplicateGift(alice, gift, sent, UUID.randomUUID(), 1) }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { reconciler.settleDuplicateGift(alice, gift, sent, idea, 0) }.exceptionOrNull() is IllegalArgumentException)
            listOf(1L to RemoteState.LOCAL_ONLY, 0L to RemoteState.SYNCED, 1L to RemoteState.SYNCED).forEach { (version, state) ->
                database.giftDao().run { update(findById(gift, alice)!!.copy(version = version, remoteState = state)) }
                val refused = runCatching { reconciler.settleDuplicateGift(alice, gift, sent, idea, 1) }
                assertTrue("$version/$state", refused.exceptionOrNull() is IllegalStateException)
            }
            database.giftDao().run { update(findById(gift, alice)!!.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY)) }
        }
        assertTrue(conflicts().isEmpty())
    }

    // ---- fixtures ----

    /** An idea for Anna given here: returns the idea and its gift, whose create is pending. */
    private suspend fun givenGift(): Pair<UUID, UUID> {
        val idea = ideas.addGiftIdea(
            GiftIdeaDetails(anna.id, occasionId = null, name = "Book", status = GiftIdeaStatus.IDEA, estimatedPrice = null,
                actualPrice = null, productUrl = null, note = null),
        )
        val gift = ideas.markAsGiven(idea.id, LocalDate.of(2026, 10, 11), null)
        return idea.id to gift.id
    }

    private suspend fun conflicts(): List<SyncConflictEntity> = database.syncConflictDao().observeUnseen(alice).first()

    /**
     * One synced row of each type at version 3, with an update pending, and the snapshot each must
     * produce: every client-decided column, nulls and markers included.
     */
    private suspend fun syncedOneOfEach(): List<Triple<SyncEntityType, UUID, String>> {
        val created = Instant.parse("2026-01-02T03:04:05.006Z")
        val deleted = Instant.parse("2026-02-03T04:05:06.789Z")
        val archived = Instant.parse("2026-03-04T05:06:07Z")
        val person = PersonEntity(
            id = UUID.fromString("a0000000-0000-4000-8000-00000000000a"), ownerUserId = alice, name = "Anna \"Panni\"",
            relationship = null, photoPath = "users/a/people/p/avatar.jpg",
            linkedUserId = UUID.fromString("c0000000-0000-4000-8000-00000000000c"), version = 3,
            remoteState = RemoteState.SYNCED, createdAt = created, updatedAt = created, deletedAt = deleted,
        )
        val occasion = OccasionEntity(
            id = UUID.fromString("e0000000-0000-4000-8000-00000000000e"), ownerUserId = alice, personId = anna.id,
            type = OccasionType.BIRTHDAY, title = null, day = 29, month = 2, year = null, repeatsYearly = true, version = 3,
            remoteState = RemoteState.SYNCED, createdAt = created, updatedAt = created, deletedAt = null,
        )
        val idea = GiftIdeaEntity(
            id = UUID.fromString("d0000000-0000-4000-8000-00000000000d"), ownerUserId = alice, recipientPersonId = anna.id,
            occasionId = occasion.id, name = "Scarf", status = GiftIdeaStatus.PURCHASED, estimatedPriceAmountMinor = 1299000,
            estimatedPriceCurrencyCode = "HUF", actualPriceAmountMinor = null, actualPriceCurrencyCode = null, photoPath = null,
            productUrl = "https://example.com/scarf", note = "Green", version = 3, remoteState = RemoteState.SYNCED,
            createdAt = created, updatedAt = created, archivedAt = archived, deletedAt = null,
        )
        val gift = GiftEntity(
            id = UUID.fromString("f0000000-0000-4000-8000-00000000000f"), ownerUserId = alice, direction = GiftDirection.RECEIVED,
            giverPersonId = anna.id, recipientPersonId = null, occasionId = null, sourceGiftIdeaId = idea.id, name = "Book",
            givenAt = LocalDate.of(2024, 2, 29), priceAmountMinor = 1999, priceCurrencyCode = "EUR", photoPath = null,
            productUrl = null, note = null, version = 3, remoteState = RemoteState.SYNCED, createdAt = created,
            updatedAt = created, deletedAt = null,
        )
        database.personDao().insert(person)
        database.occasionDao().insert(occasion)
        database.giftIdeaDao().insert(idea)
        database.giftDao().insert(gift)
        val queue = SyncOperationQueue(operations, clock)
        listOf(
            SyncEntityType.PERSON to person.id, SyncEntityType.OCCASION to occasion.id,
            SyncEntityType.GIFT_IDEA to idea.id, SyncEntityType.GIFT to gift.id,
        ).forEach { (type, id) -> queue.enqueue(alice, type, id, SyncOperation.UPDATE, RemoteState.SYNCED) }
        return listOf(
            Triple(
                SyncEntityType.PERSON, person.id,
                """{"name":"Anna \"Panni\"","relationship":null,"photo_path":"users/a/people/p/avatar.jpg",""" +
                    """"linked_user_id":"c0000000-0000-4000-8000-00000000000c","deleted_at":"2026-02-03T04:05:06.789Z"}""",
            ),
            Triple(
                SyncEntityType.OCCASION, occasion.id,
                """{"person_id":"${anna.id}","type":"BIRTHDAY","title":null,"day":29,"month":2,"year":null,""" +
                    """"repeats_yearly":true,"deleted_at":null}""",
            ),
            Triple(
                SyncEntityType.GIFT_IDEA, idea.id,
                """{"recipient_person_id":"${anna.id}","occasion_id":"${occasion.id}","name":"Scarf","status":"PURCHASED",""" +
                    """"estimated_price_amount_minor":1299000,"estimated_price_currency_code":"HUF",""" +
                    """"actual_price_amount_minor":null,"actual_price_currency_code":null,"photo_path":null,""" +
                    """"product_url":"https://example.com/scarf","note":"Green","archived_at":"2026-03-04T05:06:07Z",""" +
                    """"deleted_at":null}""",
            ),
            Triple(
                SyncEntityType.GIFT, gift.id,
                """{"direction":"RECEIVED","giver_person_id":"${anna.id}","recipient_person_id":null,"occasion_id":null,""" +
                    """"source_gift_idea_id":"${idea.id}","name":"Book","given_at":"2024-02-29","price_amount_minor":1999,""" +
                    """"price_currency_code":"EUR","photo_path":null,"product_url":null,"note":null,"deleted_at":null}""",
            ),
        )
    }

    private val birthday = OccasionDetails(OccasionType.BIRTHDAY, title = null, day = 12, month = 3, year = null, repeatsYearly = true)

    private val giftDetails get() = GiftDetails(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = anna.id, occasionId = null,
        name = "Scarf", givenAt = LocalDate.of(2026, 3, 12), price = Money(9900, "HUF"), productUrl = null, note = null,
    )

    /** A person, an occasion, an idea and a gift, each created locally with its create pending. */
    private suspend fun createOneOfEach(): List<Pair<SyncEntityType, UUID>> {
        val bela = people.addPerson("Béla", null)
        val occasion = occasions.addOccasion(anna.id, birthday)
        val idea = ideas.addGiftIdea(
            GiftIdeaDetails(anna.id, occasionId = null, name = "Book", status = GiftIdeaStatus.IDEA, estimatedPrice = null,
                actualPrice = null, productUrl = null, note = null),
        )
        val gift = gifts.addGift(giftDetails)
        return listOf(
            SyncEntityType.PERSON to bela.id,
            SyncEntityType.OCCASION to occasion.id,
            SyncEntityType.GIFT_IDEA to idea.id,
            SyncEntityType.GIFT to gift.id,
        )
    }

    /** What sync does before sending: the current payload becomes the mutation in flight. */
    private suspend fun send(type: SyncEntityType, id: UUID): UUID {
        val operation = pending(type, id)
        assertEquals(1, operations.markInFlight(operation.id, operation.mutationId))
        return operation.mutationId
    }

    private suspend fun pending(type: SyncEntityType, id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(type, id)) { "No pending $type operation" }

    /** As if a push had completed earlier: the backend has the row at [version], nothing is pending. */
    private suspend fun markKnownToBackend(type: SyncEntityType, id: UUID, version: Long) {
        check(type == SyncEntityType.PERSON)
        database.personDao().run { update(findById(id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)) }
        operations.findForEntity(type, id)?.let { operations.delete(it) }
    }

    private suspend fun assertUnchanged(type: SyncEntityType, id: UUID, block: suspend () -> Unit) {
        val entityBefore = entity(type, id)
        val operationBefore = operations.findForEntity(type, id)
        block()
        assertEquals(entityBefore, entity(type, id))
        assertEquals(operationBefore, operations.findForEntity(type, id))
    }

    /** The sync metadata of a row, and everything else about it as [content]. */
    private data class Snapshot(val version: Long, val remoteState: RemoteState, val deletedAt: Instant?, val content: Any)

    private suspend fun entity(type: SyncEntityType, id: UUID): Snapshot = when (type) {
        SyncEntityType.PERSON -> database.personDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.OCCASION -> database.occasionDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.GIFT_IDEA -> database.giftIdeaDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        SyncEntityType.GIFT -> database.giftDao().findById(id, alice)!!.let {
            Snapshot(it.version, it.remoteState, it.deletedAt, it.copy(version = 0, remoteState = RemoteState.LOCAL_ONLY))
        }
        else -> error("Not synced: $type")
    }
}
