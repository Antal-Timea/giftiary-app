package app.gifty.presentation.people

import app.gifty.testing.FakePersonRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.person
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

    @Test
    fun `people from the repository are shown and follow its changes`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository)
        collectState(viewModel)

        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)

        val bela = person("Béla")
        repository.givenPeople(anna, bela)
        assertEquals(PeopleContent.Loaded(listOf(anna, bela)), viewModel.uiState.value.content)
    }

    @Test
    fun `no people is an empty loaded list`() = runTest {
        val viewModel = PeopleViewModel(repository)
        collectState(viewModel)

        assertEquals(PeopleContent.Loaded(emptyList()), viewModel.uiState.value.content)
    }

    @Test
    fun `failing to observe people shows a load error`() = runTest {
        repository.peopleFlowFailure = IOException("disk")
        val viewModel = PeopleViewModel(repository)
        collectState(viewModel)

        assertEquals(PeopleContent.LoadFailed, viewModel.uiState.value.content)
    }

    @Test
    fun `deletion waits for confirmation`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = PeopleViewModel(repository)
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
        val viewModel = PeopleViewModel(repository)
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
        val viewModel = PeopleViewModel(repository)
        collectState(viewModel)

        viewModel.onDeleteRequested(anna)
        viewModel.onDeleteConfirmed()
        assertTrue(viewModel.uiState.value.deleteFailed)
        assertEquals(PeopleContent.Loaded(listOf(anna)), viewModel.uiState.value.content)

        viewModel.onDeleteErrorShown()
        assertFalse(viewModel.uiState.value.deleteFailed)
    }

    // uiState is shared WhileSubscribed, so a collector must be active for it to update.
    private fun TestScope.collectState(viewModel: PeopleViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
