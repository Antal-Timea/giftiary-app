package app.gifty.presentation.people

import app.gifty.domain.usecase.SavePersonUseCase
import app.gifty.testing.FakePersonRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.person
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class PersonEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePersonRepository()

    private fun viewModel(personId: UUID? = null) =
        PersonEditorViewModel(personId, repository, SavePersonUseCase(repository))

    @Test
    fun `editing starts with the stored details`() = runTest {
        val anna = person("Anna", relationship = "Sister")
        repository.givenPeople(anna)

        val state = viewModel(anna.id).uiState.value

        assertFalse(state.isNewPerson)
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals("Anna", state.name)
        assertEquals("Sister", state.relationship)
    }

    @Test
    fun `editing a person that no longer exists shows not found`() = runTest {
        assertEquals(EditorStatus.NOT_FOUND, viewModel(UUID.randomUUID()).uiState.value.status)
    }

    @Test
    fun `saving a blank name shows the validation message until the name changes`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("  ")
        viewModel.save()
        assertTrue(viewModel.uiState.value.showNameRequired)
        assertEquals(EditorStatus.EDITING, viewModel.uiState.value.status)

        viewModel.onNameChange("A")
        assertFalse(viewModel.uiState.value.showNameRequired)
    }

    @Test
    fun `a successful save closes the editor`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange(" Anna ")
        viewModel.onRelationshipChange("Sister")
        viewModel.save()

        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
        assertEquals("Anna", repository.currentPeople.single().name)
    }

    @Test
    fun `repeated save taps while saving store the person once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val viewModel = viewModel()

        viewModel.onNameChange("Anna")
        viewModel.save()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.save()
        gate.complete(Unit)

        assertEquals(1, repository.currentPeople.size)
        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
    }

    @Test
    fun `a failed save keeps the input and reports the error`() = runTest {
        repository.writeFailure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onNameChange("Anna")
        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals("Anna", state.name)
    }

    @Test
    fun `saving a person deleted in the meantime shows not found`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)
        repository.givenPeople()

        viewModel.onNameChange("Anna K.")
        viewModel.save()

        assertEquals(EditorStatus.NOT_FOUND, viewModel.uiState.value.status)
    }
}
