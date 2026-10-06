package app.gifty.data.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LocalUserIdProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the local user id survives a new provider instance, as after an app restart`() = runTest {
        val first = LocalUserIdProvider(context).currentUserId()

        val afterRestart = LocalUserIdProvider(context).currentUserId()

        assertEquals(first, afterRestart)
    }

    // Signing in must not change who owns the rows already on the device.
    @Test
    fun `an id stored by an earlier version of the app is kept as it is`() = runTest {
        val existing = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
        context.getSharedPreferences("local_user", Context.MODE_PRIVATE)
            .edit().putString("user_id", existing.toString()).commit()

        assertEquals(existing, LocalUserIdProvider(context).currentUserId())
    }
}
