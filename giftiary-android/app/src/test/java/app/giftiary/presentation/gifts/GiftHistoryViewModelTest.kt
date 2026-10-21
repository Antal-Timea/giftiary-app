package app.giftiary.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import app.giftiary.domain.model.GiftDirection
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.person
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GiftHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftRepository()
    private val ideas = FakeGiftIdeaRepository()
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
    fun `the ideas filter shows active ideas while all still means actual gifts only`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        repository.givenGifts(book)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel()
        collectState(viewModel)

        assertEquals(GiftHistoryContent.Loaded(listOf(book)), viewModel.uiState.value.content)

        viewModel.onFilterChange(GiftFilter.IDEAS)
        assertEquals(GiftHistoryContent.IdeasLoaded(listOf(scarf)), viewModel.uiState.value.content)
    }

    @Test
    fun `no ideas is its own state, even when there are gifts`() = runTest {
        repository.givenGifts(book)
        val viewModel = viewModel()
        collectState(viewModel)

        viewModel.onFilterChange(GiftFilter.IDEAS)

        assertEquals(GiftHistoryContent.NoIdeas, viewModel.uiState.value.content)
    }

    @Test
    fun `the selected filter survives recreation`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).onFilterChange(GiftFilter.RECEIVED)

        assertEquals(GiftFilter.RECEIVED, viewModel(savedState).uiState.value.filter)
    }

    private val scarf = ideaEntry(ideaDetails(anna), anna)

    @Test
    fun `a gift or an idea can be selected, and cleared again`() = runTest {
        repository.givenGifts(book, headphones)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel()
        collectState(viewModel)
        assertNull(viewModel.uiState.value.selection)

        viewModel.onGiftSelected(book)
        assertEquals(GiftHistorySelection.Gift(book.gift.id), viewModel.uiState.value.selection)

        viewModel.onFilterChange(GiftFilter.IDEAS)
        viewModel.onIdeaSelected(scarf)
        assertEquals(GiftHistorySelection.Idea(scarf.idea.id), viewModel.uiState.value.selection)

        viewModel.onSelectionCleared()
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `the selection is restored from saved state once the history loads`() = runTest {
        val savedState = SavedStateHandle()
        repository.givenGifts(book)
        viewModel(savedState).onGiftSelected(book)

        val restored = viewModel(savedState)
        assertNull("nothing selected before the history loads", restored.uiState.value.selection)
        collectState(restored)

        assertEquals(GiftHistorySelection.Gift(book.gift.id), restored.uiState.value.selection)
    }

    @Test
    fun `a deleted gift and an archived idea are no longer selected, and the saved value is dropped`() = runTest {
        val savedState = SavedStateHandle()
        repository.givenGifts(book, headphones)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(savedState)
        collectState(viewModel)

        viewModel.onGiftSelected(book)
        repository.givenGifts(headphones)
        assertNull(viewModel.uiState.value.selection)
        assertNull(savedState.get<String>(SELECTION_KEY))

        viewModel.onFilterChange(GiftFilter.IDEAS)
        viewModel.onIdeaSelected(scarf)
        ideas.givenIdeas()
        assertNull(viewModel.uiState.value.selection)
        assertNull(savedState.get<String>(SELECTION_KEY))
    }

    @Test
    fun `a filter that hides the selected gift clears it for good`() = runTest {
        repository.givenGifts(book, headphones)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onGiftSelected(headphones)

        viewModel.onFilterChange(GiftFilter.GIVEN)
        assertNull(viewModel.uiState.value.selection)

        viewModel.onFilterChange(GiftFilter.ALL)
        assertNull("not restored by switching back", viewModel.uiState.value.selection)

        viewModel.onGiftSelected(book)
        viewModel.onFilterChange(GiftFilter.IDEAS)
        assertNull("a gift is never in the ideas list", viewModel.uiState.value.selection)
    }

    @Test
    fun `a filter that still shows the selected gift keeps it`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        collectState(viewModel)
        viewModel.onGiftSelected(book)

        viewModel.onFilterChange(GiftFilter.GIVEN)

        assertEquals(GiftHistorySelection.Gift(book.gift.id), viewModel.uiState.value.selection)
    }

    @Test
    fun `a selection is kept while the history cannot be loaded`() = runTest {
        val savedState = SavedStateHandle(mapOf(KEY_FILTER to GiftFilter.IDEAS, SELECTION_KEY to "idea:${scarf.idea.id}"))
        ideas.readFailure = IOException("disk")
        val viewModel = viewModel(savedState)
        collectState(viewModel)

        assertEquals(GiftHistoryContent.LoadFailed, viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.selection)
        assertEquals("only loaded content proves it gone", "idea:${scarf.idea.id}", savedState.get<String>(SELECTION_KEY))
    }

    @Test
    fun `unreadable or mismatched saved selections are never shown and are dropped once loaded`() = runTest {
        repository.givenGifts(book)
        val unreadable = listOf(
            "gift:not-a-uuid",
            "book:${book.gift.id}",
            book.gift.id.toString(),
            "",
            // The right id under the wrong kind: an idea with a gift's id is not that gift.
            "idea:${book.gift.id}",
        )
        for (saved in unreadable) {
            val savedState = SavedStateHandle(mapOf(SELECTION_KEY to saved))
            val viewModel = viewModel(savedState)
            assertNull(saved, viewModel.uiState.value.selection)
            collectState(viewModel)

            assertEquals(GiftHistoryContent.Loaded(listOf(book)), viewModel.uiState.value.content)
            assertNull(saved, viewModel.uiState.value.selection)
            assertNull(saved, savedState.get<String>(SELECTION_KEY))
        }
    }

    @Test
    fun `a selection survives its own saved form`() {
        val id = book.gift.id
        listOf(GiftHistorySelection.Gift(id), GiftHistorySelection.Idea(id)).forEach {
            assertEquals(it, GiftHistorySelection.fromSaved(it.toSaved()))
        }
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = GiftHistoryViewModel(savedState, repository, ideas)

    private fun TestScope.collectState(viewModel: GiftHistoryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private companion object {
        // The saved-state keys are part of what a restored process reads back.
        const val KEY_FILTER = "filter"
        const val SELECTION_KEY = "selection"
    }
}
