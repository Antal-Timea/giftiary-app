package app.gifty.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.dao.LegacyOwnershipDao
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LegacyOwnershipClaimTest {

    private val legacy = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val accountA = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val accountB = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val created = Instant.parse("2026-05-01T12:00:00Z")
    private val changed = Instant.parse("2026-05-02T12:00:00Z")

    private lateinit var database: GiftyDatabase
    private lateinit var claim: LegacyOwnershipClaim

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GiftyDatabase::class.java,
        ).build()
        claim = LegacyOwnershipClaim(database, database.legacyOwnershipDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a claim moves every legacy row and pending operation to the account and nothing else`() = runTest {
        val legacyData = seedDataset(legacy)
        val unclaimed = operation(owner = null, SyncEntityType.PERSON, legacyData.anna)
        val legacyOwned = operation(owner = legacy, SyncEntityType.GIFT, legacyData.gift)
        listOf(unclaimed, legacyOwned).forEach { database.syncOperationDao().insert(it) }
        val before = snapshot()

        val result = claim.claimLegacyData(legacy, accountA)

        assertEquals(
            LegacyClaimResult(
                peopleClaimed = 2,
                occasionsClaimed = 2,
                giftIdeasClaimed = 3,
                giftsClaimed = 1,
                wishlistsClaimed = 1,
                syncOperationsClaimed = 2,
            ),
            result,
        )
        // Only ownerUserId changes; every other column of every table, wishes included, is as before.
        assertEquals(before.withOwners(from = setOf(legacy, null), to = accountA), snapshot())
    }

    @Test
    fun `tombstones and archived ideas are claimed as they are`() = runTest {
        val legacyData = seedDataset(legacy)

        claim.claimLegacyData(legacy, accountA)

        val people = database.personDao()
        val ideas = database.giftIdeaDao()
        // Not visible to normal reads, so read the raw rows.
        val deletedPerson = row("person", legacyData.deletedPerson)
        val deletedOccasion = row("occasion", legacyData.deletedOccasion)
        val deletedIdea = row("gift_idea", legacyData.deletedIdea)
        val archivedIdea = ideas.findNotDeletedById(legacyData.archivedIdea, accountA)!!
        listOf(deletedPerson, deletedOccasion, deletedIdea).forEach { deleted ->
            assertEquals(accountA.toString(), deleted["ownerUserId"])
            assertEquals(changed.toEpochMilli(), deleted["deletedAt"])
        }
        assertEquals(changed, archivedIdea.archivedAt)
        assertEquals(null, people.findActiveById(legacyData.deletedPerson, accountA))
    }

    @Test
    fun `other accounts' rows and operations are left untouched`() = runTest {
        seedDataset(legacy)
        val accountAData = seedDataset(accountA)
        val accountBData = seedDataset(accountB)
        database.syncOperationDao().insert(operation(owner = accountA, SyncEntityType.PERSON, accountAData.anna))
        database.syncOperationDao().insert(operation(owner = accountB, SyncEntityType.PERSON, accountBData.anna))
        val before = snapshot()

        claim.claimLegacyData(legacy, accountA)

        val after = snapshot()
        assertEquals(before.ownedBy(accountB), after.ownedBy(accountB))
        // Account A gains the legacy rows; the rows it already had are unchanged.
        assertEquals(before.ownedBy(accountA), after.ownedBy(accountA).onlyRowsIn(before.ownedBy(accountA)))
    }

    @Test
    fun `claiming again changes nothing`() = runTest {
        seedDataset(legacy)
        database.syncOperationDao().insert(operation(owner = null, SyncEntityType.PERSON, UUID.randomUUID()))
        claim.claimLegacyData(legacy, accountA)
        val afterFirst = snapshot()

        val second = claim.claimLegacyData(legacy, accountA)

        assertEquals(LegacyClaimResult(0, 0, 0, 0, 0, 0), second)
        assertEquals(afterFirst, snapshot())
    }

    @Test
    fun `once claimed by one account, a second account gets nothing`() = runTest {
        seedDataset(legacy)
        database.syncOperationDao().insert(operation(owner = null, SyncEntityType.PERSON, UUID.randomUUID()))
        claim.claimLegacyData(legacy, accountA)
        val claimedByA = snapshot()

        val forB = claim.claimLegacyData(legacy, accountB)

        assertEquals(LegacyClaimResult(0, 0, 0, 0, 0, 0), forB)
        assertEquals(claimedByA, snapshot())
        assertEquals(
            emptyList<SyncOperationEntity>(),
            database.syncOperationDao().getPending(accountB, limit = 10),
        )
    }

    @Test
    fun `a failure anywhere in the claim rolls back every table, operations included`() = runTest {
        val legacyData = seedDataset(legacy)
        database.syncOperationDao().insert(operation(owner = null, SyncEntityType.PERSON, legacyData.anna))
        val before = snapshot()
        val real = database.legacyOwnershipDao()
        // Fails after the last statement has run, so every table was already rewritten.
        val failingAtTheEnd = object : LegacyOwnershipDao by real {
            override suspend fun claimSyncOperations(legacyOwnerId: UUID, accountOwnerId: UUID): Int {
                real.claimSyncOperations(legacyOwnerId, accountOwnerId)
                throw IllegalStateException("simulated failure")
            }
        }

        val failure = runCatching {
            LegacyOwnershipClaim(database, failingAtTheEnd).claimLegacyData(legacy, accountA)
        }

        assertEquals("simulated failure", failure.exceptionOrNull()?.message)
        assertEquals(before, snapshot())
    }

    @Test
    fun `migrated operations without an owner are all assigned by the first claim`() = runTest {
        val legacyData = seedDataset(legacy)
        val unclaimed = listOf(legacyData.anna, legacyData.gift).map { id ->
            operation(owner = null, SyncEntityType.PERSON, id).also { database.syncOperationDao().insert(it) }
        }

        claim.claimLegacyData(legacy, accountA)

        assertEquals(0, count("SELECT COUNT(*) FROM sync_operation WHERE ownerUserId IS NULL"))
        assertEquals(
            unclaimed.map { it.copy(ownerUserId = accountA) }.sortedBy { it.id },
            database.syncOperationDao().getPending(accountA, limit = 10).sortedBy { it.id },
        )
    }

    @Test
    fun `the legacy owner cannot claim its own data`() = runTest {
        val failure = runCatching { claim.claimLegacyData(legacy, legacy) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    // ---- data ----

    private class Dataset(
        val anna: UUID,
        val deletedPerson: UUID,
        val deletedOccasion: UUID,
        val archivedIdea: UUID,
        val deletedIdea: UUID,
        val gift: UUID,
    )

    /**
     * One owner's rows in every owned table: active, synced (version 3), soft-deleted and archived,
     * plus a wishlist with a wish, which has no owner of its own.
     */
    private suspend fun seedDataset(owner: UUID): Dataset {
        val anna = person(owner, deletedAt = null, remoteState = RemoteState.SYNCED, version = 3)
        val gone = person(owner, deletedAt = changed)
        database.personDao().insert(anna)
        database.personDao().insert(gone)
        val birthday = occasion(owner, anna.id, deletedAt = null)
        val deletedOccasion = occasion(owner, anna.id, deletedAt = changed)
        database.occasionDao().insert(birthday)
        database.occasionDao().insert(deletedOccasion)
        val idea = idea(owner, anna.id, birthday.id)
        val archived = idea(owner, anna.id, null).copy(archivedAt = changed, status = GiftIdeaStatus.PURCHASED)
        val deletedIdea = idea(owner, anna.id, null).copy(deletedAt = changed)
        listOf(idea, archived, deletedIdea).forEach { database.giftIdeaDao().insert(it) }
        val gift = gift(owner, anna.id, birthday.id, archived.id)
        database.giftDao().insert(gift)
        val wishlistId = UUID.randomUUID()
        execute(
            "INSERT INTO wishlist (id, ownerUserId, personId, name, version, remoteState, createdAt, updatedAt, " +
                "deletedAt) VALUES (?, ?, ?, 'Birthday list', 0, 'LOCAL_ONLY', ?, ?, NULL)",
            wishlistId.toString(), owner.toString(), anna.id.toString(),
            created.toEpochMilli(), created.toEpochMilli(),
        )
        execute(
            "INSERT INTO wish (id, wishlistId, name, note, estimatedPriceAmountMinor, estimatedPriceCurrencyCode, " +
                "productUrl, photoPath, version, remoteState, createdAt, updatedAt, deletedAt) VALUES " +
                "(?, ?, 'Scarf', NULL, 12000, 'HUF', NULL, NULL, 0, 'LOCAL_ONLY', ?, ?, NULL)",
            UUID.randomUUID().toString(), wishlistId.toString(), created.toEpochMilli(), created.toEpochMilli(),
        )
        return Dataset(anna.id, gone.id, deletedOccasion.id, archived.id, deletedIdea.id, gift.id)
    }

    private fun person(
        owner: UUID,
        deletedAt: Instant?,
        remoteState: RemoteState = RemoteState.LOCAL_ONLY,
        version: Long = 0,
    ) =
        PersonEntity(
            id = UUID.randomUUID(), ownerUserId = owner, name = "Anna", relationship = "Sister", photoPath = null,
            linkedUserId = null, version = version, remoteState = remoteState, createdAt = created,
            updatedAt = changed, deletedAt = deletedAt,
        )

    private fun occasion(owner: UUID, personId: UUID, deletedAt: Instant?) = OccasionEntity(
        id = UUID.randomUUID(), ownerUserId = owner, personId = personId, type = OccasionType.BIRTHDAY, title = null,
        day = 12, month = 3, year = null, repeatsYearly = true, version = 0, remoteState = RemoteState.LOCAL_ONLY,
        createdAt = created, updatedAt = changed, deletedAt = deletedAt,
    )

    private fun idea(owner: UUID, recipientId: UUID, occasionId: UUID?) = GiftIdeaEntity(
        id = UUID.randomUUID(), ownerUserId = owner, recipientPersonId = recipientId, occasionId = occasionId,
        name = "Scarf", status = GiftIdeaStatus.IDEA, estimatedPriceAmountMinor = 10000,
        estimatedPriceCurrencyCode = "HUF", actualPriceAmountMinor = null, actualPriceCurrencyCode = null,
        photoPath = null, productUrl = null, note = null, version = 0, remoteState = RemoteState.LOCAL_ONLY,
        createdAt = created, updatedAt = changed, archivedAt = null, deletedAt = null,
    )

    private fun gift(owner: UUID, recipientId: UUID, occasionId: UUID, sourceIdeaId: UUID) = GiftEntity(
        id = UUID.randomUUID(), ownerUserId = owner, direction = GiftDirection.GIVEN, giverPersonId = null,
        recipientPersonId = recipientId, occasionId = occasionId, sourceGiftIdeaId = sourceIdeaId, name = "Scarf",
        givenAt = LocalDate.parse("2026-03-12"), priceAmountMinor = 9900, priceCurrencyCode = "HUF",
        photoPath = null, productUrl = null, note = null, version = 0, remoteState = RemoteState.LOCAL_ONLY,
        createdAt = created, updatedAt = changed, deletedAt = null,
    )

    private fun operation(owner: UUID?, entityType: SyncEntityType, entityId: UUID) = SyncOperationEntity(
        id = UUID.randomUUID(), ownerUserId = owner, entityType = entityType, entityId = entityId,
        operation = SyncOperation.CREATE, mutationId = UUID.randomUUID(), inFlightMutationId = null, blockedReason = null,
        createdAt = created, attemptCount = 2, lastAttemptAt = changed,
        lastErrorCode = "timeout",
    )

    // ---- raw table snapshots ----

    private val tables = listOf("person", "occasion", "gift_idea", "gift", "wishlist", "wish", "sync_operation")

    /** Every row of every table as column values, so "unchanged" means every column, not selected fields. */
    private fun snapshot(): Map<String, List<Map<String, Any?>>> = tables.associateWith { table ->
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        (0 until cursor.columnCount).associate { i ->
                            cursor.getColumnName(i) to when (cursor.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> null
                                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                else -> cursor.getString(i)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun Map<String, List<Map<String, Any?>>>.withOwners(from: Set<UUID?>, to: UUID) =
        mapValues { (table, rows) ->
            rows.map { row ->
                val owner = row["ownerUserId"] as String?
                val eligible = "ownerUserId" in row &&
                    (owner?.let(UUID::fromString) in from && (owner != null || table == "sync_operation"))
                if (eligible) row + ("ownerUserId" to to.toString()) else row
            }
        }

    private fun Map<String, List<Map<String, Any?>>>.ownedBy(owner: UUID) =
        mapValues { (_, rows) -> rows.filter { it["ownerUserId"] == owner.toString() } }

    private fun Map<String, List<Map<String, Any?>>>.onlyRowsIn(other: Map<String, List<Map<String, Any?>>>) =
        mapValues { (table, rows) ->
            val ids = other.getValue(table).map { it["id"] }.toSet()
            rows.filter { it["id"] in ids }
        }

    private fun row(table: String, id: UUID): Map<String, Any?> =
        snapshot().getValue(table).single { it["id"] == id.toString() }

    private fun count(sql: String): Int =
        database.openHelper.readableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun execute(sql: String, vararg args: Any?) {
        database.openHelper.writableDatabase.execSQL(sql, args)
    }
}
