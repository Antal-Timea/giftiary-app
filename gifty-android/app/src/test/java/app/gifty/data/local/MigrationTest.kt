package app.gifty.data.local

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        GiftyDatabase::class.java,
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
            // Unclaimed operations are invisible to every owner.
            assertEquals(emptyList<SyncOperationEntity>(), dao.getPending(UUID.randomUUID(), limit = 10))
        }
    }

    @Test
    fun `v2 to v3 gives every pending operation its own new mutation id and keeps everything else`() = runTest {
        val operations = listOf(
            retried.copy(ownerUserId = alice),
            fresh.copy(ownerUserId = bob),
            // Still waiting for the legacy claim.
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
            // Each owner still reads their own operations, oldest first; the unclaimed one by nobody.
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

    private suspend fun withRoom(block: suspend (GiftyDatabase) -> Unit) {
        val room = Room.databaseBuilder(context, GiftyDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
