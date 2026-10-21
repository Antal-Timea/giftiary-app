package app.giftiary.presentation.people

import androidx.lifecycle.SavedStateHandle
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.person
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePersonRepository()
    private val savedState = SavedStateHandle()

    @Test
    fun `people from the repository are shown and follow its changes`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)

        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        assertEquals(PeopleContent.Loaded(listOf(anna, bela)), viewModel.uiState.value.content)
    }

    @Test
    fun `no people is an empty loaded list`() = runTest {
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        assertEquals(PeopleContent.Loaded(emptyList()), viewModel.uiState.value.content)
    }

    @Test
    fun `failing to observe people shows a load error`() = runTest {
        repository.peopleFlowFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        assertEquals(PeopleContent.LoadFailed, viewModel.uiState.value.content)
    }

    @Test
    fun `deletion waits for confirmation`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        viewModel.onDeleteRequested(anna)
        assertEquals(anna, viewModel.uiState.value.personPendingDeletion)
        assertEquals(listOf(anna), repository.currentPeople)

        viewModel.onDeleteConfirmed()
        assertNull(viewModel.uiState.value.personPendingDeletion)
        assertEquals(PeopleContent.Loaded(emptyList()), viewModel.uiState.value.content)
    }

    @Test
    fun `dismissing the confirmation keeps the person`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        viewModel.onDeleteRequested(anna)
        viewModel.onDeleteDismissed()

        assertNull(viewModel.uiState.value.personPendingDeletion)
        assertEquals(listOf(anna), repository.currentPeople)
    }

    @Test
    fun `a failed deletion is reported once`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        repository.writeFailure = IOException("disk full")
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        viewModel.onDeleteRequested(anna)
        viewModel.onDeleteConfirmed()
        assertTrue(viewModel.uiState.value.deleteFailed)
        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)

        viewModel.onDeleteErrorShown()
        assertFalse(viewModel.uiState.value.deleteFailed)
    }

    @Test
    fun `a selected person can be cleared again`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna, person("Béla"))
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)
        assertNull(viewModel.uiState.value.selectedPersonId)

        viewModel.onPersonSelected(anna)
        assertEquals(anna.id, viewModel.uiState.value.selectedPersonId)

        viewModel.onSelectionCleared()
        assertNull(viewModel.uiState.value.selectedPersonId)
    }

    @Test
    fun `the selection is restored from saved state, as after process death`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        PeopleViewModel(repository, savedState).onPersonSelected(anna)

        val restored = PeopleViewModel(repository, savedState)
        assertNull("nothing selected before the people load", restored.uiState.value.selectedPersonId)
        collectState(restored)

        assertEquals(anna.id, restored.uiState.value.selectedPersonId)
    }

    @Test
    fun `a restored selection is not shown while the people cannot be loaded, and is kept for later`() = runTest {
        val annaId = person("Anna").id.toString()
        savedState[SELECTED_PERSON_KEY] = annaId
        repository.peopleFlowFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)

        assertEquals(PeopleContent.LoadFailed, viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.selectedPersonId)
        // Only a loaded list proves the person gone.
        assertEquals(annaId, savedState.get<String>(SELECTED_PERSON_KEY))
    }

    @Test
    fun `a selected person deleted elsewhere is no longer selected, and the saved id is dropped`() = runTest {
        val anna = person("Anna")
        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)
        viewModel.onPersonSelected(anna)

        repository.givenPeople(bela)

        assertNull(viewModel.uiState.value.selectedPersonId)
        assertNull(savedState.get<String>(SELECTED_PERSON_KEY))
    }

    @Test
    fun `deleting the selected person clears the selection, deleting another keeps it`() = runTest {
        val anna = person("Anna")
        val bela = person("Béla")
        val cili = person("Cili")
        repository.givenPeople(anna, bela, cili)
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)
        viewModel.onPersonSelected(anna)

        viewModel.onDeleteRequested(bela)
        viewModel.onDeleteConfirmed()
        assertEquals(anna.id, viewModel.uiState.value.selectedPersonId)

        viewModel.onDeleteRequested(anna)
        viewModel.onDeleteConfirmed()
        assertNull(viewModel.uiState.value.selectedPersonId)
        assertNull(savedState.get<String>(SELECTED_PERSON_KEY))
    }

    @Test
    fun `a failed deletion keeps the person selected`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        repository.writeFailure = IOException("disk full")
        val viewModel = PeopleViewModel(repository, savedState)
        collectState(viewModel)
        viewModel.onPersonSelected(anna)

        viewModel.onDeleteRequested(anna)
        viewModel.onDeleteConfirmed()

        assertEquals(anna.id, viewModel.uiState.value.selectedPersonId)
    }

    // uiState is shared WhileSubscribed, so a collector must be active for it to update.
    private fun TestScope.collectState(viewModel: PeopleViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private companion object {
        // The saved-state key is part of what a restored process reads back.
        const val SELECTED_PERSON_KEY = "selectedPersonId"
    }
}
