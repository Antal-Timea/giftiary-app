package app.giftiary

import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.data.sync.ForegroundSyncCoordinator
import app.giftiary.data.sync.FullSyncRequester
import app.giftiary.data.sync.SyncPuller
import app.giftiary.data.sync.SyncRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun `every start of the activity asks for a full sync once`() {
        var requests = 0
        // Never created: Hilt injects on create, and the only dependency onStart uses is set here.
        val activity = Robolectric.buildActivity(MainActivity::class.java)
        activity.get().fullSyncRequester = FullSyncRequester { requests++ }

        activity.start()
        assertEquals(1, requests)

        activity.stop().start()
        assertEquals(2, requests)
    }

    @Test
    fun `the activity can only ask for sync, never sync itself`() {
        val syncTypes = setOf(ForegroundSyncCoordinator::class.java, SyncRunner::class.java, SyncPuller::class.java, RemoteAccounts::class.java)

        assertTrue(MainActivity::class.java.declaredFields.none { it.type in syncTypes })
    }
}
