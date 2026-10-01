package app.gifty.data.local

import app.gifty.data.local.SyncOperationQueue.Companion.coalesce
import app.gifty.data.local.entity.SyncOperation.CREATE
import app.gifty.data.local.entity.SyncOperation.DELETE
import app.gifty.data.local.entity.SyncOperation.UPDATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncOperationCoalescingTest {

    @Test
    fun `without a pending operation the new operation is queued as is`() {
        assertEquals(CREATE, coalesce(pending = null, next = CREATE, existsRemotely = false))
        assertEquals(UPDATE, coalesce(pending = null, next = UPDATE, existsRemotely = true))
        assertEquals(DELETE, coalesce(pending = null, next = DELETE, existsRemotely = true))
    }

    @Test
    fun `update after create stays create`() {
        assertEquals(CREATE, coalesce(pending = CREATE, next = UPDATE, existsRemotely = false))
    }

    @Test
    fun `update after update stays update`() {
        assertEquals(UPDATE, coalesce(pending = UPDATE, next = UPDATE, existsRemotely = true))
    }

    @Test
    fun `delete after update becomes delete`() {
        assertEquals(DELETE, coalesce(pending = UPDATE, next = DELETE, existsRemotely = true))
    }

    @Test
    fun `delete after create of a local-only entity leaves nothing to sync`() {
        assertNull(coalesce(pending = CREATE, next = DELETE, existsRemotely = false))
    }

    @Test
    fun `delete after create of an entity the backend already has becomes delete`() {
        assertEquals(DELETE, coalesce(pending = CREATE, next = DELETE, existsRemotely = true))
    }

    @Test
    fun `impossible combinations are rejected`() {
        listOf(
            CREATE to CREATE,
            UPDATE to CREATE,
            DELETE to CREATE,
            DELETE to UPDATE,
            DELETE to DELETE,
        ).forEach { (pending, next) ->
            assertThrows("$next after $pending", IllegalStateException::class.java) {
                coalesce(pending = pending, next = next, existsRemotely = true)
            }
        }
    }
}
