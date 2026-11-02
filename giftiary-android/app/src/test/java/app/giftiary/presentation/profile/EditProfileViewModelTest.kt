package app.giftiary.presentation.profile

import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.testing.FakeProfileRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The name others see, moved here from Settings unchanged. */
class EditProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()

    private fun viewModel() = EditProfileViewModel(profiles)

    @Test
    fun `the saved name is shown for editing`() = runTest {
        profiles.name = "Anna"

        val viewModel = viewModel()

        assertEquals(ProfileContent.Loaded(saved = "Anna"), viewModel.state.value.profile)
        assertEquals("Anna", viewModel.state.value.nameInput)
    }

    @Test
    fun `without a name the field is empty, never the email`() = runTest {
        val viewModel = viewModel()

        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
        assertEquals("", viewModel.state.value.nameInput)
    }

    @Test
    fun `a name is saved trimmed, and an emptied one as no name`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("  Anna  ")
        viewModel.onSave()
        assertEquals(listOf<String?>("Anna"), profiles.saved)
        assertEquals("Anna", viewModel.state.value.nameInput)
        assertTrue(viewModel.state.value.saved)

        viewModel.onMessageShown()
        viewModel.onNameChange("   ")
        viewModel.onSave()
        assertEquals(listOf("Anna", null), profiles.saved)
        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
    }

    @Test
    fun `a name with a line break is refused locally`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("Anna\nKovács")
        viewModel.onSave()

        assertTrue(viewModel.state.value.inputInvalid)
        assertTrue(profiles.saved.isEmpty())
        viewModel.onNameChange("Anna")
        assertFalse(viewModel.state.value.inputInvalid)
    }

    @Test
    fun `an unchanged name is not sent again`() = runTest {
        profiles.name = "Anna"
        val viewModel = viewModel()

        viewModel.onNameChange(" Anna ")
        viewModel.onSave()

        assertTrue(profiles.saved.isEmpty())
    }

    @Test
    fun `a failed save keeps what was typed and can be tried again`() = runTest {
        val viewModel = viewModel()
        profiles.failNext += ProfileFailure.NETWORK

        viewModel.onNameChange("Anna")
        viewModel.onSave()
        assertEquals(ProfileFailure.NETWORK, viewModel.state.value.saveFailure)
        assertEquals("Anna", viewModel.state.value.nameInput)
        assertFalse(viewModel.state.value.isSaving)

        viewModel.onMessageShown()
        viewModel.onSave()
        assertEquals(listOf<String?>("Anna"), profiles.saved)
    }

    @Test
    fun `a profile that cannot be read can be read again`() = runTest {
        profiles.failNext += ProfileFailure.UNAVAILABLE
        val viewModel = viewModel()
        assertEquals(ProfileContent.Failed(ProfileFailure.UNAVAILABLE), viewModel.state.value.profile)

        viewModel.onSave()
        assertTrue("nothing to save before it is loaded", profiles.saved.isEmpty())

        viewModel.load()
        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
        assertNull(viewModel.state.value.saveFailure)
    }
}
