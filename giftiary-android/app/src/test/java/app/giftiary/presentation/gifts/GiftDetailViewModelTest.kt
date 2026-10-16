package app.giftiary.presentation.gifts

import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
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

class GiftDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftRepository()
    private val book = giftEntry(giftDetails(person = anna), anna)

    @Test
    fun `shows the gift and follows edits`() = runTest {
        repository.people[anna.id] = anna
        repository.givenGifts(book)
        val viewModel = GiftDetailViewModel(book.gift.id, repository)
        collectState(viewModel)

        assertEquals(GiftDetailContent.Loaded(book), viewModel.uiState.value.content)

        repository.updateGift(book.gift.id, giftDetails(person = anna, name = "Signed book"))
        val content = viewModel.uiState.value.content as GiftDetailContent.Loaded
        assertEquals("Signed book", content.entry.gift.name)
    }

    @Test
    fun `a missing gift shows not found`() = runTest {
        val viewModel = GiftDetailViewModel(UUID.randomUUID(), repository)
        collectState(viewModel)

        assertEquals(GiftDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun `deleting asks for confirmation and then reports the deletion`() = runTest {
        repository.givenGifts(book)
        val viewModel = GiftDetailViewModel(book.gift.id, repository)
        collectState(viewModel)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(listOf(book), repository.currentGifts)

        viewModel.onDeleteConfirmed()
        assertEquals(GiftDetailContent.Deleted, viewModel.uiState.value.content)
        assertTrue(repository.currentGifts.isEmpty())
    }

    @Test
    fun `a failed deletion keeps the gift and reports the error`() = runTest {
        repository.givenGifts(book)
        repository.writeFailure = IOException("disk full")
        val viewModel = GiftDetailViewModel(book.gift.id, repository)
        collectState(viewModel)

        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertTrue(state.actions.deleteFailed)
        assertFalse(state.actions.isDeleting)
        assertEquals(GiftDetailContent.Loaded(book), state.content)
    }

    private fun TestScope.collectState(viewModel: GiftDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
