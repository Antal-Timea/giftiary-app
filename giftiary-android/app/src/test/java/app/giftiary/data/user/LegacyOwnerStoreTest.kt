package app.giftiary.data.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** The legacy id decides which data a claim may take over, so it must only ever be read. */
@RunWith(RobolectricTestRunner::class)
class LegacyOwnerStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("local_user", Context.MODE_PRIVATE)
    private val store = LegacyOwnerStore(context)

    @Test
    fun `an id written before accounts existed is returned unchanged`() = runTest {
        val existing = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
        preferences.edit().putString("user_id", existing.toString()).commit()

        assertEquals(existing, store.existingLegacyOwnerIdOrNull())
        assertEquals(existing.toString(), preferences.getString("user_id", null))
    }

    @Test
    fun `without a stored id there is none, and none is created`() = runTest {
        assertNull(store.existingLegacyOwnerIdOrNull())

        assertFalse(preferences.contains("user_id"))
        assertEquals(emptyMap<String, Any?>(), preferences.all)
    }

    @Test
    fun `a malformed stored id is ignored and left as it is`() = runTest {
        preferences.edit().putString("user_id", "not-a-uuid").commit()

        assertNull(store.existingLegacyOwnerIdOrNull())
        assertEquals("not-a-uuid", preferences.getString("user_id", null))
    }

    @Test
    fun `an id in a shortened form the app never wrote is not trusted`() = runTest {
        // UUID.fromString would read this as 00000001-0002-0003-0004-000000000005.
        preferences.edit().putString("user_id", "1-2-3-4-5").commit()

        assertNull(store.existingLegacyOwnerIdOrNull())
        assertEquals("1-2-3-4-5", preferences.getString("user_id", null))
    }
}
