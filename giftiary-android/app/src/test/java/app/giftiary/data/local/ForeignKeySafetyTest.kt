package app.giftiary.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** A database Room creates at the current version has only immediate foreign keys. */
@RunWith(RobolectricTestRunner::class)
class ForeignKeySafetyTest {

    private lateinit var database: GiftiaryDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `every foreign key is declared, and none is deferred`() {
        val db = database.openHelper.readableDatabase
        val tables = listOf("occasion", "gift_idea", "gift", "wishlist", "wish")

        // Room's schema validation does not see deferral, so the stored definitions are checked.
        tables.forEach { table ->
            val sql = db.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use {
                it.moveToFirst()
                it.getString(0)
            }
            assertTrue("$table: $sql", "DEFERRABLE" !in sql && "DEFERRED" !in sql)
        }
        val foreignKeys = tables.sumOf { table ->
            // A key over two columns, like the wish's same-owner key, is listed once per column.
            db.query("SELECT COUNT(DISTINCT id) FROM pragma_foreign_key_list('$table')").use { it.moveToFirst(); it.getInt(0) }
        }
        assertEquals(9, foreignKeys)
    }

    @Test
    fun `a write referring to a missing row fails at once, rolls back and leaves the database usable`() = runTest {
        ForeignKeySafety.assertMissingReferenceRollsBackCleanly(database, UUID.randomUUID())
    }
}
