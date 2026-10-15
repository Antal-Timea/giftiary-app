package app.gifty.testing

import app.gifty.data.reminders.ReminderScheduler
import java.util.Collections

/** Records what was asked of the reminder heartbeat, in order. */
class FakeReminderScheduler : ReminderScheduler {

    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun ensureScheduled() {
        calls += ENSURE
    }

    override fun cancel() {
        calls += CANCEL
    }

    companion object {
        const val ENSURE = "ensure"
        const val CANCEL = "cancel"
    }
}
