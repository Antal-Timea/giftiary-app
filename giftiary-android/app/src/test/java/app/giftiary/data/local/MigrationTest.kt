package app.giftiary.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.di.DatabaseModule
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/** Real migrations from the exported schema history, validated by Room against the next schema. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // androidx.sqlite (2.6.2) checks that the opened file matches the database name by splitting
    // paths on '/', which never matches the '\' paths Robolectric uses on Windows. Reporting the
    // full path as the name keeps that check exact on every OS; opening itself is unchanged.
    private val pathNamedHelpers = SupportSQLiteOpenHelper.Factory { configuration ->
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        object : SupportSQLiteOpenHelper by helper {
            override val databaseName: String? = configuration.name?.let { context.getDatabasePath(it).absolutePath }
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GiftiaryDatabase::class.java,
        emptyList(),
        pathNamedHelpers,
    )

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")

    // Mutation ids did not exist before v3; the migration gives each operation a new one, so the
    // values here only fill the field and are never compared.
    private val retried = SyncOperationEntity(
        id = UUID.fromString("11111111-1111-4111-8111-111111111111"),
        ownerUserId = null,
        entityType = SyncEntityType.PERSON,
        entityId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
        operation = SyncOperation.CREATE,
        mutationId = UUID(0, 0),
        inFlightMutationId = null,
        blockedReason = null,
        createdAt = Instant.parse("2026-05-01T12:00:00Z"),
        attemptCount = 2,
        lastAttemptAt = Instant.parse("2026-05-01T12:05:00Z"),
        lastErrorCode = "timeout",
    )
    private val fresh = retried.copy(
        id = UUID.fromString("22222222-2222-4222-8222-222222222222"),
        entityType = SyncEntityType.GIFT_IDEA,
        entityId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
        operation = SyncOperation.UPDATE,
        createdAt = Instant.parse("2026-05-02T08:00:00Z"),
        attemptCount = 0,
        lastAttemptAt = null,
        lastErrorCode = null,
    )

    @Test
    fun `v1 to v2 keeps every pending operation, without an owner`() = runTest {
        helper.createDatabase(DB_NAME, 1).use { db ->
            listOf(retried, fresh).forEach { op ->
                db.execSQL(
                    "INSERT INTO sync_operation (id, entityType, entityId, operation, createdAt, attemptCount, " +
                        "lastAttemptAt, lastErrorCode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        op.id.toString(), op.entityType.name, op.entityId.toString(), op.operation.name,
                        op.createdAt.toEpochMilli(), op.attemptCount, op.lastAttemptAt?.toEpochMilli(),
                        op.lastErrorCode,
                    ),
                )
            }
        }

        // Validates the migrated tables and indices against the exported v2 schema.
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(
                setOf("index_sync_operation_entityType_entityId", "index_sync_operation_ownerUserId_createdAt"),
                indices(db, "sync_operation"),
            )
        }

        withRoom { room ->
            val dao = room.syncOperationDao()
            listOf(retried, fresh).forEach { expected ->
                val actual = dao.findForEntity(expected.entityType, expected.entityId)!!
                assertEquals(expected.copy(mutationId = actual.mutationId), actual)
            }
            // Unowned operations are invisible to every owner.
            assertEquals(emptyList<SyncOperationEntity>(), dao.getPending(UUID.randomUUID(), limit = 10))
        }
    }

    @Test
    fun `v2 to v3 gives every pending operation its own new mutation id and keeps everything else`() = runTest {
        val operations = listOf(
            retried.copy(ownerUserId = alice),
            fresh.copy(ownerUserId = bob),
            // Still without an owner.
            retried.copy(
                id = UUID.fromString("33333333-3333-4333-8333-333333333333"),
                ownerUserId = null,
                entityType = SyncEntityType.OCCASION,
                entityId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                operation = SyncOperation.DELETE,
                createdAt = Instant.parse("2026-04-30T09:00:00Z"),
                attemptCount = 7,
                lastErrorCode = "http_503",
            ),
            fresh.copy(
                id = UUID.fromString("44444444-4444-4444-8444-444444444444"),
                ownerUserId = alice,
                entityType = SyncEntityType.GIFT,
                entityId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
                operation = SyncOperation.CREATE,
                createdAt = Instant.parse("2026-05-03T10:00:00Z"),
            ),
        )
        val (operationsBefore, peopleBefore) = helper.createDatabase(DB_NAME, 2).use { db ->
            operations.forEach { op ->
                db.execSQL(
                    "INSERT INTO sync_operation (id, ownerUserId, entityType, entityId, operation, createdAt, " +
                        "attemptCount, lastAttemptAt, lastErrorCode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        op.id.toString(), op.ownerUserId?.toString(), op.entityType.name, op.entityId.toString(),
                        op.operation.name, op.createdAt.toEpochMilli(), op.attemptCount,
                        op.lastAttemptAt?.toEpochMilli(), op.lastErrorCode,
                    ),
                )
            }
            db.execSQL(
                "INSERT INTO person (id, ownerUserId, name, relationship, photoPath, linkedUserId, version, " +
                    "remoteState, createdAt, updatedAt, deletedAt) VALUES (?, ?, 'Anna', NULL, NULL, NULL, 3, " +
                    "'SYNCED', 1746100800000, 1746104400000, NULL)",
                arrayOf<Any?>(retried.entityId.toString(), alice.toString()),
            )
            rows(db, "SELECT * FROM sync_operation ORDER BY id") to rows(db, "SELECT * FROM person ORDER BY id")
        }

        // Validates the migrated tables and indices against the exported v3 schema.
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { db ->
            val migrated = rows(db, "SELECT * FROM sync_operation ORDER BY id")
            assertEquals(operationsBefore, migrated.map { it - NEW_COLUMNS })

            val mutationIds = migrated.map { UUID.fromString(it.getValue("mutationId") as String) }
            mutationIds.forEach { assertEquals("random (version 4) UUID", 4, it.version()) }
            assertEquals(operations.size, mutationIds.toSet().size)
            migrated.forEach { row ->
                assertNull(row["inFlightMutationId"])
                assertNull(row["blockedReason"])
            }

            assertEquals(peopleBefore, rows(db, "SELECT * FROM person ORDER BY id"))
            assertEquals(
                setOf("index_sync_operation_entityType_entityId", "index_sync_operation_ownerUserId_createdAt"),
                indices(db, "sync_operation"),
            )
            assertEquals(setOf("index_sync_conflict_ownerUserId_seenAt"), indices(db, "sync_conflict"))
        }

        withRoom { room ->
            val dao = room.syncOperationDao()
            // Each owner still reads their own operations, oldest first; the unowned one by nobody.
            assertEquals(listOf(operations[0].id, operations[3].id), dao.getPending(alice, limit = 10).map { it.id })
            assertEquals(listOf(operations[1].id), dao.getPending(bob, limit = 10).map { it.id })
            operations.forEach { expected ->
                val actual = dao.findForEntity(expected.entityType, expected.entityId)!!
                assertEquals(expected.copy(mutationId = actual.mutationId), actual)
            }
        }
    }

    @Test
    fun `v2 to v3 with an empty queue`() = runTest {
        helper.createDatabase(DB_NAME, 2).close()

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { db ->
            assertTrue(rows(db, "SELECT * FROM sync_operation").isEmpty())
            assertTrue(rows(db, "SELECT * FROM sync_conflict").isEmpty())
        }
    }

    // ---- v3 to v4: immediate foreign keys ----

    @Test
    fun `v3 to v4 keeps every value of every row and every index, and no foreign key stays deferred`() = runTest {
        val before = helper.createDatabase(DB_NAME, 3).use { db ->
            insertFullGraph(db)
            ALL_TABLES.associateWith { rows(db, "SELECT * FROM `$it` ORDER BY id") }
        }

        // Room validates only the structure, which does not include deferral; the checks below do.
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
            ALL_TABLES.forEach { table ->
                assertEquals("rows of $table", before.getValue(table), rows(db, "SELECT * FROM `$table` ORDER BY id"))
            }
            assertEquals(EXPECTED_INDEXES, REBUILT_TABLES.associateWith { indexes(db, it) })
            assertEquals(EXPECTED_FOREIGN_KEYS, REBUILT_TABLES.flatMap { foreignKeys(db, it) }.toSet())
            assertNoDeferredForeignKeys(db)
        }
    }

    @Test
    fun `v3 data that already refers to a missing row fails the migration and stays v3`() = runTest {
        helper.createDatabase(DB_NAME, 3).use { db ->
            insert(db, "person", person(PERSON_A, alice))
            // Only possible with foreign keys off, as on this raw connection.
            insert(db, "occasion", occasion(OCCASION_A, alice, personId = UUID.randomUUID()))
        }

        val failure = runCatching { helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalStateException && "`occasion`" in failure.message!!)
        rawDatabase().use { db ->
            assertEquals(3, db.version)
            assertEquals(listOf(PERSON_A.toString()), column(db, "SELECT id FROM person"))
            assertEquals(listOf(OCCASION_A.toString()), column(db, "SELECT id FROM occasion"))
            assertTrue("still the v3 table", "DEFERRABLE INITIALLY DEFERRED" in tableSql(db, "occasion"))
            assertTrue("no rebuilt table left", column(db, "SELECT name FROM sqlite_master WHERE name LIKE '%\\_new' ESCAPE '\\'").isEmpty())
        }
    }

    @Test
    fun `the app's own database setup migrates v3, and the result rejects a missing reference at once`() = runTest {
        helper.createDatabase(GiftiaryDatabase.NAME, 3).use { db -> insert(db, "person", person(PERSON_A, alice)) }

        val room = DatabaseModule.provideDatabase(context)
        try {
            assertNoDeferredForeignKeys(room.openHelper.readableDatabase)
            ForeignKeySafety.assertMissingReferenceRollsBackCleanly(room, alice)
            assertNotNull("the migrated row is still there", room.personDao().findById(PERSON_A, alice))
        } finally {
            room.close()
        }
    }

    // ---- v4 to v5: a wish carries its wishlist's owner ----

    @Test
    fun `v4 to v5 makes wishlists and wishes same-owner and keeps every value of every row`() = runTest {
        val before = helper.createDatabase(DB_NAME, 4).use { db ->
            insertFullGraph(db)
            ALL_TABLES.associateWith { rows(db, "SELECT * FROM `$it` ORDER BY id") }
        }

        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5).use { db ->
            ALL_TABLES.forEach { table ->
                val expected = before.getValue(table).map { row ->
                    if (table == "wish") row + ("ownerUserId" to WISH_OWNERS.getValue(row["id"] as String)) else row
                }
                assertEquals("rows of $table", expected, rows(db, "SELECT * FROM `$table` ORDER BY id"))
            }
            assertEquals(
                mapOf(
                    "index_wish_ownerUserId" to (listOf("ownerUserId") to false),
                    "index_wish_wishlistId_ownerUserId" to (listOf("wishlistId", "ownerUserId") to false),
                ),
                indexes(db, "wish"),
            )
            assertEquals(
                mapOf(
                    "index_wishlist_ownerUserId" to (listOf("ownerUserId") to false),
                    "index_wishlist_personId_ownerUserId" to (listOf("personId", "ownerUserId") to false),
                    "index_wishlist_id_ownerUserId" to (listOf("id", "ownerUserId") to true),
                ),
                indexes(db, "wishlist"),
            )
            assertEquals(
                mapOf(
                    "index_person_ownerUserId" to (listOf("ownerUserId") to false),
                    "index_person_id_ownerUserId" to (listOf("id", "ownerUserId") to true),
                ),
                indexes(db, "person"),
            )
            assertEquals(
                setOf("wishlist.personId -> person.id CASCADE/NO ACTION", "wishlist.ownerUserId -> person.ownerUserId CASCADE/NO ACTION"),
                foreignKeys(db, "wishlist").toSet(),
            )
            assertEquals(
                setOf("wish.wishlistId -> wishlist.id CASCADE/NO ACTION", "wish.ownerUserId -> wishlist.ownerUserId CASCADE/NO ACTION"),
                foreignKeys(db, "wish").toSet(),
            )
            assertNoDeferredForeignKeys(db)
            assertEquals(0, db.query("PRAGMA foreign_key_check").use { it.count })

            db.execSQL("PRAGMA foreign_keys = ON")
            // Bob's wish on Alice's wishlist, and Bob's wishlist for Alice's person.
            val crossOwnerWish = runCatching {
                db.insert("wish", SQLiteDatabase.CONFLICT_ABORT, values(wish(UUID.randomUUID(), WISHLIST_A) + ("ownerUserId" to bob.toString())))
            }.exceptionOrNull()
            assertTrue("$crossOwnerWish", crossOwnerWish is SQLiteConstraintException)
            val crossOwnerWishlist = runCatching {
                db.insert("wishlist", SQLiteDatabase.CONFLICT_ABORT, values(wishlist(UUID.randomUUID(), bob, PERSON_A)))
            }.exceptionOrNull()
            assertTrue("$crossOwnerWishlist", crossOwnerWishlist is SQLiteConstraintException)
        }
    }

    @Test
    fun `a v4 wishlist of another owner's person fails the migration and stays v4`() = runTest {
        helper.createDatabase(DB_NAME, 4).use { db ->
            insert(db, "person", person(PERSON_A, alice))
            // v4's key is on the person's id alone, so this row is valid there.
            insert(db, "wishlist", wishlist(WISHLIST_A, bob, PERSON_A))
            insert(db, "wish", wish(WISH_A, WISHLIST_A))
        }

        val failure = runCatching { helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalStateException && "`wishlist`" in failure.message!!)
        rawDatabase().use { db ->
            assertEquals(4, db.version)
            assertEquals(listOf(bob.toString()), column(db, "SELECT ownerUserId FROM wishlist"))
            assertEquals(listOf(WISH_A.toString()), column(db, "SELECT id FROM wish"))
            assertTrue("still the v4 tables", "ownerUserId" !in tableSql(db, "wish") && "`ownerUserId`)" !in tableSql(db, "wishlist"))
            assertTrue("no new person key", column(db, "SELECT name FROM sqlite_master WHERE name = 'index_person_id_ownerUserId'").isEmpty())
            assertTrue("no rebuilt table left", column(db, "SELECT name FROM sqlite_master WHERE name LIKE '%\\_new' ESCAPE '\\'").isEmpty())
        }
    }

    @Test
    fun `a v4 wish without its wishlist fails the migration and stays v4`() = runTest {
        helper.createDatabase(DB_NAME, 4).use { db ->
            insert(db, "wishlist", wishlist(WISHLIST_A, alice, null))
            insert(db, "wish", wish(WISH_A, WISHLIST_A))
            // Only possible with foreign keys off, as on this raw connection.
            insert(db, "wish", wish(WISH_OWN, UUID.randomUUID()))
        }

        val failure = runCatching { helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5) }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalStateException && "1 wishes" in failure.message!!)
        rawDatabase().use { db ->
            assertEquals(4, db.version)
            assertEquals(listOf(WISH_A.toString(), WISH_OWN.toString()), column(db, "SELECT id FROM wish ORDER BY id"))
            assertTrue("still the v4 table", "ownerUserId" !in tableSql(db, "wish"))
            assertTrue("no rebuilt table left", column(db, "SELECT name FROM sqlite_master WHERE name LIKE '%\\_new' ESCAPE '\\'").isEmpty())
        }
    }

    private fun assertNoDeferredForeignKeys(db: SupportSQLiteDatabase) {
        REBUILT_TABLES.forEach { table ->
            val sql = db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
                it.moveToFirst()
                it.getString(0)
            }
            assertTrue("$table: $sql", "FOREIGN KEY" in sql && "DEFERRABLE" !in sql && "DEFERRED" !in sql)
        }
    }

    /** Two accounts, every table, and every kind of row and reference the app writes. */
    private fun insertFullGraph(db: SupportSQLiteDatabase) {
        insert(db, "person", person(PERSON_A, alice, version = 3, remoteState = "SYNCED"))
        insert(db, "person", person(PERSON_GONE, alice, version = 2, remoteState = "SYNCED", deletedAt = 1_700_000_100_000))
        insert(db, "person", person(PERSON_B, bob))
        insert(db, "occasion", occasion(OCCASION_A, alice, personId = PERSON_A, version = 1, remoteState = "SYNCED"))
        insert(db, "occasion", occasion(OCCASION_GONE, alice, personId = PERSON_GONE, deletedAt = 1_700_000_200_000))
        insert(db, "occasion", occasion(OCCASION_GENERAL, bob, personId = null, type = "CHRISTMAS"))
        insert(
            db, "gift_idea",
            idea(IDEA_GIVEN, alice, PERSON_A, OCCASION_A) + mapOf(
                "status" to "PURCHASED", "estimatedPriceAmountMinor" to 12990L, "estimatedPriceCurrencyCode" to "HUF",
                "actualPriceAmountMinor" to 11990L, "actualPriceCurrencyCode" to "HUF", "version" to 4L,
                "remoteState" to "SYNCED", "archivedAt" to 1_700_000_300_000L,
            ),
        )
        insert(db, "gift_idea", idea(IDEA_OF_GONE, alice, PERSON_GONE, null) + mapOf("note" to "Before"))
        insert(db, "gift_idea", idea(IDEA_B, bob, PERSON_B, OCCASION_GENERAL) + mapOf("productUrl" to "https://example.com"))
        insert(
            db, "gift",
            gift(GIFT_FROM_IDEA, alice, "GIVEN", giver = null, recipient = PERSON_A) + mapOf(
                "occasionId" to OCCASION_A.toString(), "sourceGiftIdeaId" to IDEA_GIVEN.toString(),
                "priceAmountMinor" to 11990L, "priceCurrencyCode" to "HUF", "version" to 1L, "remoteState" to "SYNCED",
            ),
        )
        insert(db, "gift", gift(GIFT_RECEIVED, bob, "RECEIVED", giver = PERSON_B, recipient = null) + mapOf("note" to "Thanks"))
        insert(
            db, "gift",
            gift(GIFT_GONE, alice, "GIVEN", giver = null, recipient = PERSON_GONE) + mapOf("deletedAt" to 1_700_000_400_000L),
        )
        insert(db, "wishlist", wishlist(WISHLIST_A, alice, PERSON_A))
        insert(db, "wishlist", wishlist(WISHLIST_OWN, bob, null))
        insert(db, "wish", wish(WISH_A, WISHLIST_A) + mapOf("estimatedPriceAmountMinor" to 5000L, "estimatedPriceCurrencyCode" to "EUR"))
        insert(db, "wish", wish(WISH_OWN, WISHLIST_OWN) + mapOf("deletedAt" to 1_700_000_500_000L))
        // Pending; blocked with an attempt; in flight with a newer change queued and its attempts; unowned.
        insert(db, "sync_operation", operation(1, bob, "PERSON", PERSON_B, "CREATE"))
        insert(
            db, "sync_operation",
            operation(2, alice, "GIFT_IDEA", IDEA_OF_GONE, "CREATE") + mapOf("blockedReason" to "REJECTED", "attemptCount" to 1L),
        )
        insert(
            db, "sync_operation",
            operation(3, alice, "PERSON", PERSON_A, "UPDATE") + mapOf(
                "inFlightMutationId" to "c0000000-0000-4000-8000-0000000000f1", "attemptCount" to 3L,
                "lastAttemptAt" to 1_700_000_600_000L, "lastErrorCode" to "NETWORK",
            ),
        )
        insert(db, "sync_operation", operation(4, null, "OCCASION", OCCASION_GENERAL, "CREATE"))
        insert(
            db, "sync_conflict",
            mapOf(
                "id" to "e0000000-0000-4000-8000-000000000001", "ownerUserId" to alice.toString(), "entityType" to "PERSON",
                "entityId" to PERSON_A.toString(), "mutationId" to "c0000000-0000-4000-8000-0000000000f0", "baseVersion" to 2L,
                "remoteVersion" to 3L, "localSnapshotJson" to "{}", "createdAt" to 1_700_000_700_000L, "seenAt" to null,
            ),
        )
    }

    private fun values(values: Map<String, Any?>) = ContentValues().apply {
        values.forEach { (column, value) -> if (value == null) putNull(column) else put(column, value.toString()) }
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, values: Map<String, Any?>) {
        val contentValues = ContentValues().apply {
            values.forEach { (column, value) ->
                when (value) {
                    null -> putNull(column)
                    is String -> put(column, value)
                    is Long -> put(column, value)
                    is Int -> put(column, value)
                    else -> error("Unsupported value for $column")
                }
            }
        }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, contentValues)
    }

    private fun person(id: UUID, owner: UUID, version: Long = 0, remoteState: String = "LOCAL_ONLY", deletedAt: Long? = null) =
        mapOf(
            "id" to id.toString(), "ownerUserId" to owner.toString(), "name" to "Person $id", "relationship" to null,
            "photoPath" to null, "linkedUserId" to null, "version" to version, "remoteState" to remoteState,
            "createdAt" to 1_700_000_000_000L, "updatedAt" to 1_700_000_000_001L, "deletedAt" to deletedAt,
        )

    private fun occasion(
        id: UUID,
        owner: UUID,
        personId: UUID?,
        type: String = "BIRTHDAY",
        version: Long = 0,
        remoteState: String = "LOCAL_ONLY",
        deletedAt: Long? = null,
    ) = mapOf(
        "id" to id.toString(), "ownerUserId" to owner.toString(), "personId" to personId?.toString(), "type" to type,
        "title" to null, "day" to 29, "month" to 2, "year" to null, "repeatsYearly" to 1, "version" to version,
        "remoteState" to remoteState, "createdAt" to 1_700_000_000_000L, "updatedAt" to 1_700_000_000_002L,
        "deletedAt" to deletedAt,
    )

    private fun idea(id: UUID, owner: UUID, recipient: UUID, occasionId: UUID?) = mapOf(
        "id" to id.toString(), "ownerUserId" to owner.toString(), "recipientPersonId" to recipient.toString(),
        "occasionId" to occasionId?.toString(), "name" to "Scarf", "status" to "IDEA", "estimatedPriceAmountMinor" to null,
        "estimatedPriceCurrencyCode" to null, "actualPriceAmountMinor" to null, "actualPriceCurrencyCode" to null,
        "photoPath" to null, "productUrl" to null, "note" to null, "version" to 0L, "remoteState" to "LOCAL_ONLY",
        "createdAt" to 1_700_000_000_000L, "updatedAt" to 1_700_000_000_003L, "archivedAt" to null, "deletedAt" to null,
    )

    private fun gift(id: UUID, owner: UUID, direction: String, giver: UUID?, recipient: UUID?) = mapOf(
        "id" to id.toString(), "ownerUserId" to owner.toString(), "direction" to direction, "giverPersonId" to giver?.toString(),
        "recipientPersonId" to recipient?.toString(), "occasionId" to null, "sourceGiftIdeaId" to null, "name" to "Book",
        "givenAt" to "2024-02-29", "priceAmountMinor" to null, "priceCurrencyCode" to null, "photoPath" to null,
        "productUrl" to null, "note" to null, "version" to 0L, "remoteState" to "LOCAL_ONLY",
        "createdAt" to 1_700_000_000_000L, "updatedAt" to 1_700_000_000_004L, "deletedAt" to null,
    )

    private fun wishlist(id: UUID, owner: UUID, personId: UUID?) = mapOf(
        "id" to id.toString(), "ownerUserId" to owner.toString(), "personId" to personId?.toString(), "name" to "Birthday",
        "version" to 0L, "remoteState" to "LOCAL_ONLY", "createdAt" to 1_700_000_000_000L,
        "updatedAt" to 1_700_000_000_005L, "deletedAt" to null,
    )

    private fun wish(id: UUID, wishlistId: UUID) = mapOf(
        "id" to id.toString(), "wishlistId" to wishlistId.toString(), "name" to "Lamp", "note" to null,
        "estimatedPriceAmountMinor" to null, "estimatedPriceCurrencyCode" to null, "productUrl" to null, "photoPath" to null,
        "version" to 0L, "remoteState" to "LOCAL_ONLY", "createdAt" to 1_700_000_000_000L,
        "updatedAt" to 1_700_000_000_006L, "deletedAt" to null,
    )

    private fun operation(n: Int, owner: UUID?, type: String, entityId: UUID, operation: String) = mapOf(
        "id" to "f000000$n-0000-4000-8000-000000000000", "ownerUserId" to owner?.toString(), "entityType" to type,
        "entityId" to entityId.toString(), "operation" to operation, "mutationId" to "c000000$n-0000-4000-8000-000000000000",
        "inFlightMutationId" to null, "blockedReason" to null, "createdAt" to 1_700_000_000_000L, "attemptCount" to 0L,
        "lastAttemptAt" to null, "lastErrorCode" to null,
    )

    /** Each index Room created on [table] (not the primary key's), with its columns in order and whether it is unique. */
    private fun indexes(db: SupportSQLiteDatabase, table: String): Map<String, Pair<List<String>, Boolean>> =
        db.query("SELECT name, `unique` FROM pragma_index_list('$table') WHERE origin = 'c'").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val columns = db.query("SELECT name FROM pragma_index_info('$name') ORDER BY seqno").use { info ->
                        buildList { while (info.moveToNext()) add(info.getString(0)) }
                    }
                    put(name, columns to (cursor.getInt(1) != 0))
                }
            }
        }

    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("SELECT `from`, `table`, `to`, on_update, on_delete FROM pragma_foreign_key_list('$table')").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val (from, parent, to) = Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                    add("$table.$from -> $parent.$to ${cursor.getString(3)}/${cursor.getString(4)}")
                }
            }
        }

    private fun rawDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY)

    private fun column(db: SQLiteDatabase, sql: String): List<String> = db.rawQuery(sql, null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun tableSql(db: SQLiteDatabase, table: String): String =
        db.rawQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
            it.moveToFirst()
            it.getString(0)
        }

    private suspend fun withRoom(block: suspend (GiftiaryDatabase) -> Unit) {
        val room = Room.databaseBuilder(context, GiftiaryDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            block(room)
        } finally {
            room.close()
        }
    }

    private fun indices(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("SELECT name FROM pragma_index_list('$table') WHERE origin = 'c'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    /** Every column of every row, so "unchanged" means every value, not selected fields. */
    private fun rows(db: SupportSQLiteDatabase, sql: String): List<Map<String, Any?>> =
        db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.columnNames.associateWith { cursor.value(cursor.getColumnIndexOrThrow(it)) })
                }
            }
        }

    private fun Cursor.value(index: Int): Any? = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        else -> getString(index)
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        val NEW_COLUMNS = setOf("mutationId", "inFlightMutationId", "blockedReason")

        val REBUILT_TABLES = listOf("occasion", "gift_idea", "gift", "wishlist", "wish")
        val ALL_TABLES = listOf("person") + REBUILT_TABLES + listOf("sync_operation", "sync_conflict")

        // v3's indexes, all kept by v4: one column each, none unique.
        val EXPECTED_INDEXES = mapOf(
            "occasion" to listOf("ownerUserId", "personId"),
            "gift_idea" to listOf("ownerUserId", "recipientPersonId", "occasionId"),
            "gift" to listOf("ownerUserId", "giverPersonId", "recipientPersonId", "occasionId", "sourceGiftIdeaId"),
            "wishlist" to listOf("ownerUserId", "personId"),
            "wish" to listOf("wishlistId"),
        ).mapValues { (table, columns) -> columns.associate { "index_${table}_$it" to (listOf(it) to false) } }

        val EXPECTED_FOREIGN_KEYS = setOf(
            "occasion.personId -> person.id NO ACTION/NO ACTION",
            "gift_idea.recipientPersonId -> person.id NO ACTION/NO ACTION",
            "gift_idea.occasionId -> occasion.id NO ACTION/NO ACTION",
            "gift.giverPersonId -> person.id NO ACTION/NO ACTION",
            "gift.recipientPersonId -> person.id NO ACTION/NO ACTION",
            "gift.occasionId -> occasion.id NO ACTION/NO ACTION",
            "gift.sourceGiftIdeaId -> gift_idea.id NO ACTION/NO ACTION",
            "wishlist.personId -> person.id NO ACTION/NO ACTION",
            "wish.wishlistId -> wishlist.id NO ACTION/NO ACTION",
        )

        val WISH_OWNERS = mapOf(
            "60000000-0000-4000-8000-000000000001" to "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "60000000-0000-4000-8000-000000000002" to "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        )

        val PERSON_A: UUID = UUID.fromString("a0000000-0000-4000-8000-000000000001")
        val PERSON_GONE: UUID = UUID.fromString("a0000000-0000-4000-8000-000000000002")
        val PERSON_B: UUID = UUID.fromString("a0000000-0000-4000-8000-000000000003")
        val OCCASION_A: UUID = UUID.fromString("b0000000-0000-4000-8000-000000000001")
        val OCCASION_GONE: UUID = UUID.fromString("b0000000-0000-4000-8000-000000000002")
        val OCCASION_GENERAL: UUID = UUID.fromString("b0000000-0000-4000-8000-000000000003")
        val IDEA_GIVEN: UUID = UUID.fromString("d0000000-0000-4000-8000-000000000001")
        val IDEA_OF_GONE: UUID = UUID.fromString("d0000000-0000-4000-8000-000000000002")
        val IDEA_B: UUID = UUID.fromString("d0000000-0000-4000-8000-000000000003")
        val GIFT_FROM_IDEA: UUID = UUID.fromString("90000000-0000-4000-8000-000000000001")
        val GIFT_RECEIVED: UUID = UUID.fromString("90000000-0000-4000-8000-000000000002")
        val GIFT_GONE: UUID = UUID.fromString("90000000-0000-4000-8000-000000000003")
        val WISHLIST_A: UUID = UUID.fromString("70000000-0000-4000-8000-000000000001")
        val WISHLIST_OWN: UUID = UUID.fromString("70000000-0000-4000-8000-000000000002")
        val WISH_A: UUID = UUID.fromString("60000000-0000-4000-8000-000000000001")
        val WISH_OWN: UUID = UUID.fromString("60000000-0000-4000-8000-000000000002")
    }
}
