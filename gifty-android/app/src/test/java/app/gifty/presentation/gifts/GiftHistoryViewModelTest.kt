package app.gifty.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import app.gifty.domain.model.GiftDirection
import app.gifty.testing.FakeGiftRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.giftDetails
import app.gifty.testing.giftEntry
import app.gifty.testing.person
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GiftHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftRepository()
    private val book = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Book", LocalDate.of(2026, 3, 12)), anna)
    private val headphones = giftEntry(giftDetails(GiftDirection.RECEIVED, anna, "Headphones", LocalDate.of(2025, 11, 4)), anna)

    @Test
    fun `all filter shows every gift, newest first`() = runTest {
        repository.givenGifts(headphones, book)
        val viewModel = viewModel()
        collectState(viewModel)

        assertEquals(GiftHistoryContent.Loaded(listOf(book, headphones)), viewModel.uiState.value.content)
    }

    @Test
    fun `given and received filters narrow the list immediately`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        collectState(viewModel)

        viewModel.onFilterChange(GiftFilter.GIVEN)
        assertEquals(GiftHistoryContent.Loaded(listOf(book)), viewModel.uiState.value.content)

        viewModel.onFilterChange(GiftFilter.RECEIVED)
        assertEquals(GiftHistoryContent.Loaded(listOf(headphones)), viewModel.uiState.value.content)
    }

    @Test
    fun `no gifts at all is distinguished from a filter without matches`() = runTest {
        val viewModel = viewModel()
        collectState(viewModel)
        assertEquals(GiftHistoryContent.NoGifts, viewModel.uiState.value.content)

        repository.givenGifts(book)
        viewModel.onFilterChange(GiftFilter.RECEIVED)
        assertEquals(GiftHistoryContent.NoMatches, viewModel.uiState.value.content)
    }

    @Test
    fun `the selected filter survives recreation`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).onFilterChange(GiftFilter.RECEIVED)

        assertEquals(GiftFilter.RECEIVED, viewModel(savedState).uiState.value.filter)
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = GiftHistoryViewModel(savedState, repository)

    private fun TestScope.collectState(viewModel: GiftHistoryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
