package app.gifty.testing

import app.gifty.data.user.ActiveOwner
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** An active owner a test sets directly, e.g. to switch accounts or close all data. */
class FakeActiveOwner(owner: UUID?) : ActiveOwner {
    override val ownerId = MutableStateFlow(owner)
}
