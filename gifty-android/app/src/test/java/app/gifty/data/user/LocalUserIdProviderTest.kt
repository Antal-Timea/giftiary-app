package app.gifty.data.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalUserIdProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the local user id survives a new provider instance, as after an app restart`() = runTest {
        val first = LocalUserIdProvider(context).currentUserId()

        val afterRestart = LocalUserIdProvider(context).currentUserId()

        assertEquals(first, afterRestart)
    }
}
