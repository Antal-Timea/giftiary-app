package app.giftiary.testing

import app.giftiary.data.sync.SyncScheduler
import java.util.Collections

/** Records what was asked of the scheduler, in order. */
class FakeSyncScheduler : SyncScheduler {

    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Runs at each catch-up request, e.g. to see what had happened by then. */
    var onCatchUp: () -> Unit = {}

    override fun requestCatchUp() {
        calls += CATCH_UP
        onCatchUp()
    }

    override fun schedulePeriodic() {
        calls += PERIODIC
    }

    override fun cancelAll() {
        calls += CANCEL
    }

    companion object {
        const val CATCH_UP = "catch-up"
        const val PERIODIC = "periodic"
        const val CANCEL = "cancel"
    }
}
