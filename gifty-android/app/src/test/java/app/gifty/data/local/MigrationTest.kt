package app.gifty.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private val retried = SyncOperationEntity(
        id = UUID.fromString("11111111-1111-4111-8111-111111111111"),
        ownerUserId = null,
        entityType = SyncEntityType.PERSON,
        entityId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
        operation = SyncOperation.CREATE,
        createdAt = Instant.parse("2026-05-01T12:00:00Z"),
        attemptCount = 2,
        lastAttemptAt = Instant.parse("2026-05-01T12:05:00Z"),
        lastErrorCode = "timeout",
    )
    private val fresh = SyncOperationEntity(
        id = UUID.fromString("22222222-2222-4222-8222-222222222222"),
        ownerUserId = null,
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
            db.query("SELECT name FROM pragma_index_list('sync_operation')").use { cursor ->
                val indices = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue("index_sync_operation_entityType_entityId" in indices)
                assertTrue("index_sync_operation_ownerUserId_createdAt" in indices)
                assertTrue("index_sync_operation_createdAt" !in indices)
            }
        }

        val room = Room.databaseBuilder(context, GiftyDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val dao = room.syncOperationDao()
            assertEquals(retried, dao.findForEntity(retried.entityType, retried.entityId))
            assertEquals(fresh, dao.findForEntity(fresh.entityType, fresh.entityId))
            // Unclaimed operations are invisible to every owner.
            assertEquals(emptyList<SyncOperationEntity>(), dao.getPending(UUID.randomUUID(), limit = 10))
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
