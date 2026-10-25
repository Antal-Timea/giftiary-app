package app.giftiary.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.testing.ACCOUNT_TABLES
import app.giftiary.testing.pendingCreate
import app.giftiary.testing.rowsOf
import app.giftiary.testing.seedAccount
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AccountDataDaoTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val legacy = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private lateinit var database: GiftiaryDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), GiftiaryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the purge removes every row of the account and nothing of anyone else`() = runTest {
        database.seedAccount(alice)
        database.seedAccount(bob)
        // Data from before accounts: rows of the old device id, and a queue row without an owner.
        database.seedAccount(legacy)
        database.syncOperationDao().insert(pendingCreate(null, SyncEntityType.PERSON, UUID.randomUUID()))
        val everyTable = ACCOUNT_TABLES.associateWith { 1 }
        assertEquals(everyTable, database.rowsOf(alice))

        database.accountDataDao().purge(alice)

        assertEquals(ACCOUNT_TABLES.associateWith { 0 }, database.rowsOf(alice))
        assertEquals(everyTable, database.rowsOf(bob))
        assertEquals(everyTable, database.rowsOf(legacy))
        assertEquals(1, database.rowsOf(null).getValue("sync_operation"))
    }

    @Test
    fun `purging an account that has nothing here changes nothing`() = runTest {
        database.seedAccount(bob)

        database.accountDataDao().purge(alice)
        database.accountDataDao().purge(alice)

        assertEquals(ACCOUNT_TABLES.associateWith { 1 }, database.rowsOf(bob))
    }

    @Test
    fun `the purge covers every table of the database`() {
        // A new table must be added to the purge, and here, or a deleted account leaves rows behind.
        val tables = database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('room_master_table', 'android_metadata', 'sqlite_sequence')")
            .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        assertEquals(tables, ACCOUNT_TABLES.toSet())
    }
}
