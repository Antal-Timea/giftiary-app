package app.giftiary.presentation.giftideas

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.person
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

class GiftIdeaDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftIdeaRepository()
    private val scarf = ideaEntry(ideaDetails(anna), anna)

    @Test
    fun `status changes apply directly, forward and back`() = runTest {
        repository.givenIdeas(scarf)
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository)
        collectState(viewModel)

        viewModel.onStatusChange(GiftIdeaStatus.PURCHASED)
        assertEquals(GiftIdeaStatus.PURCHASED, loadedStatus(viewModel))

        viewModel.onStatusChange(GiftIdeaStatus.IDEA)
        assertEquals(GiftIdeaStatus.IDEA, loadedStatus(viewModel))
    }

    @Test
    fun `a failed status change is reported and the stored status stays`() = runTest {
        repository.givenIdeas(scarf)
        repository.writeFailure = IOException("disk full")
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository)
        collectState(viewModel)

        viewModel.onStatusChange(GiftIdeaStatus.PLANNED)

        assertTrue(viewModel.uiState.value.actions.writeFailed)
        assertFalse(viewModel.uiState.value.actions.isBusy)
        assertEquals(GiftIdeaStatus.IDEA, loadedStatus(viewModel))
    }

    @Test
    fun `deleting asks for confirmation and then reports the deletion`() = runTest {
        repository.givenIdeas(scarf)
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository)
        collectState(viewModel)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(listOf(scarf), repository.currentIdeas)

        viewModel.onDeleteConfirmed()
        assertEquals(GiftIdeaDetailContent.Deleted, viewModel.uiState.value.content)
        assertTrue(repository.currentIdeas.isEmpty())
    }

    @Test
    fun `an idea that is gone shows not found`() = runTest {
        val viewModel = GiftIdeaDetailViewModel(UUID.randomUUID(), repository)
        collectState(viewModel)

        assertEquals(GiftIdeaDetailContent.NotFound, viewModel.uiState.value.content)
    }

    private fun loadedStatus(viewModel: GiftIdeaDetailViewModel): GiftIdeaStatus =
        (viewModel.uiState.value.content as GiftIdeaDetailContent.Loaded).entry.idea.status

    private fun TestScope.collectState(viewModel: GiftIdeaDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
