package app.gifty.presentation.home

import app.gifty.domain.model.NotificationAccess
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.FakeReminderSettingsRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.occasion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class ReminderCardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val occasions = FakeOccasionRepository()
    private val settings = FakeReminderSettingsRepository()

    @Test
    fun `nothing is offered before there is an occasion to be reminded of`() = runTest {
        val viewModel = collected()
        assertEquals(ReminderCardState.Hidden, viewModel.state.value)

        occasions.givenOccasions(occasion(null))

        assertEquals(ReminderCardState.Off, viewModel.state.value)
    }

    @Test
    fun `turned on where Android asks first, the system is asked once, from that tap`() = runTest {
        occasions.givenOccasions(occasion(null))
        settings.access = NotificationAccess.PERMISSION_MISSING
        val viewModel = collected()

        assertTrue("ask now", viewModel.onTurnOn())
        assertEquals(ReminderCardState.NeedsPermission, viewModel.state.value)

        settings.access = NotificationAccess.GRANTED
        viewModel.onPermissionResult()
        assertEquals(ReminderCardState.On, viewModel.state.value)
    }

    @Test
    fun `a refusal leaves the choice on and the way to allow it open, however often`() = runTest {
        occasions.givenOccasions(occasion(null))
        settings.access = NotificationAccess.PERMISSION_MISSING
        val viewModel = collected()
        viewModel.onTurnOn()

        repeat(3) { viewModel.onPermissionResult() }

        assertEquals(ReminderCardState.NeedsPermission, viewModel.state.value)
        assertEquals(true, settings.isEnabled())
    }

    @Test
    fun `where nothing has to be asked, turning on is enough`() = runTest {
        occasions.givenOccasions(occasion(null))
        val viewModel = collected()

        assertFalse(viewModel.onTurnOn())
        assertEquals(ReminderCardState.On, viewModel.state.value)

        viewModel.onTurnOff()
        assertEquals(ReminderCardState.Off, viewModel.state.value)
    }

    @Test
    fun `coming back from the system settings shows what was changed there`() = runTest {
        occasions.givenOccasions(occasion(null))
        settings.setEnabled(true)
        settings.access = NotificationAccess.BLOCKED
        val viewModel = collected()
        assertEquals(ReminderCardState.Blocked, viewModel.state.value)

        settings.access = NotificationAccess.GRANTED
        viewModel.onResume()
        assertEquals(ReminderCardState.On, viewModel.state.value)

        // The reminder channel switched off there.
        settings.access = NotificationAccess.BLOCKED
        viewModel.onResume()
        assertEquals(ReminderCardState.Blocked, viewModel.state.value)
    }

    @Test
    fun `reminders already on stay visible, to be turned off, even without occasions or when they cannot be read`() = runTest {
        settings.setEnabled(true)
        occasions.readFailure = IOException("disk")

        assertEquals(ReminderCardState.On, collected().state.value)
    }

    @Test
    fun `each account sees its own choice`() = runTest {
        occasions.givenOccasions(occasion(null))
        val alice = settings.owner!!
        settings.setEnabled(true)
        assertEquals(ReminderCardState.On, collected().state.value)

        // The Home of another account is a new screen with a new view model.
        settings.owner = UUID.randomUUID()

        assertEquals(ReminderCardState.Off, collected().state.value)
        assertEquals(true, settings.enabled[alice])
    }

    private fun TestScope.collected(): ReminderCardViewModel {
        val viewModel = ReminderCardViewModel(occasions, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }
}
