package app.giftiary.testing

import app.giftiary.data.auth.RecoveryGate
import kotlinx.coroutines.flow.MutableStateFlow

/** A recovery gate a test opens and closes directly. */
class FakeRecoveryGate(active: Boolean = false) : RecoveryGate {
    override val isActive = MutableStateFlow(active)
}
