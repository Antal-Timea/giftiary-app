package app.giftiary.presentation.gifts

import androidx.lifecycle.SavedStateHandle
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class GiftHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val peter = person("Peter")
    private val repository = FakeGiftRepository()
    private val photos = FakeGiftPhotoRepository()
    private val book = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Book", LocalDate.of(2026, 3, 12)), anna)
    private val headphones = giftEntry(giftDetails(GiftDirection.RECEIVED, anna, "Headphones", LocalDate.of(2025, 11, 4)), anna)
    private val watch = giftEntry(giftDetails(GiftDirection.RECEIVED, peter, "Watch", LocalDate.of(2026, 9, 1)), peter)

    private fun loaded(vararg gifts: GiftEntry) = GiftHistoryContent.Loaded(groupByYearAndMonth(gifts.toList()))

    @Test
    fun `every gift is grouped by year, then month, newest first`() = runTest {
        val sameDayEarlier = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Card", LocalDate.of(2026, 9, 1)), anna)
            .let { it.copy(gift = it.gift.copy(createdAt = Instant.EPOCH)) }
        val sameDayLater = watch.copy(gift = watch.gift.copy(createdAt = Instant.EPOCH.plusSeconds(60)))
        repository.givenGifts(headphones, sameDayEarlier, book, sameDayLater)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)

        val years = (viewModel.uiState.value.content as GiftHistoryContent.Loaded).years
        assertEquals(listOf(2026, 2025), years.map { it.year })
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 3)), years[0].months.map { it.month })
        assertEquals("latest added first on a shared day", listOf(sameDayLater, sameDayEarlier), years[0].months[0].gifts)
        assertEquals(listOf(book), years[0].months[1].gifts)
        assertEquals(listOf(YearMonth.of(2025, 11)), years[1].months.map { it.month })
    }

    @Test
    fun `there are only the three direction filters, never one for ideas`() {
        assertEquals(listOf(GiftFilter.ALL, GiftFilter.GIVEN, GiftFilter.RECEIVED), GiftFilter.entries)
    }

    @Test
    fun `given and received filters narrow the list immediately`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)

        viewModel.onFilterChange(GiftFilter.GIVEN)
        assertEquals(loaded(book), viewModel.uiState.value.content)

        viewModel.onFilterChange(GiftFilter.RECEIVED)
        assertEquals(loaded(headphones), viewModel.uiState.value.content)
    }

    @Test
    fun `the search finds a gift by its name, its people or its occasion's own title, with the filter`() = runTest {
        val party = book.copy(
            gift = book.gift.copy(name = "Lamp"),
            occasion = Occasion(
                id = UUID.randomUUID(),
                personId = null,
                type = OccasionType.CUSTOM,
                title = "Housewarming",
                day = 3,
                month = 5,
                year = null,
                repeatsYearly = false,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        repository.givenGifts(party, headphones, watch)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)

        viewModel.onQueryChange("  head ")
        assertEquals(loaded(headphones), viewModel.uiState.value.content)
        viewModel.onQueryChange("PETER")
        assertEquals(loaded(watch), viewModel.uiState.value.content)
        viewModel.onQueryChange("housewarm")
        assertEquals(loaded(party), viewModel.uiState.value.content)
        viewModel.onQueryChange("anna")
        assertEquals(loaded(party, headphones), viewModel.uiState.value.content)
        viewModel.onFilterChange(GiftFilter.GIVEN)
        assertEquals(loaded(party), viewModel.uiState.value.content)
    }

    @Test
    fun `no gifts at all is distinguished from a filter or search without matches`() = runTest {
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)
        assertEquals(GiftHistoryContent.NoGifts, viewModel.uiState.value.content)

        viewModel.onQueryChange("book")
        assertEquals("a search on an empty history is still empty", GiftHistoryContent.NoGifts, viewModel.uiState.value.content)

        repository.givenGifts(book, headphones)
        viewModel.onQueryChange("socks")
        assertEquals(GiftHistoryContent.NoMatches(total = 2), viewModel.uiState.value.content)

        viewModel.onQueryChange("")
        viewModel.onFilterChange(GiftFilter.GIVEN)
        repository.givenGifts(headphones)
        assertEquals(GiftHistoryContent.NoMatches(total = 1), viewModel.uiState.value.content)
    }

    @Test
    fun `an unreadable history is a load failure, never an empty one`() = runTest {
        repository.readFailure = IOException("disk")
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftHistoryContent.LoadFailed, viewModel.uiState.value.content)
    }

    @Test
    fun `the filter and the search survive recreation`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState).apply {
            onFilterChange(GiftFilter.RECEIVED)
            onQueryChange("peter")
        }

        val restored = viewModel(savedState).uiState.value
        assertEquals(GiftFilter.RECEIVED, restored.filter)
        assertEquals("peter", restored.query)
    }

    @Test
    fun `loading, filtering and searching the history read no photo, and a shown row reads its own, once`() = runTest {
        val withPhoto = book.copy(gift = book.gift.copy(photoPath = "book.jpg"))
        val otherPhoto = headphones.copy(gift = headphones.gift.copy(photoPath = "headphones.jpg"))
        photos.stored["book.jpg"] = byteArrayOf(1)
        photos.stored["headphones.jpg"] = byteArrayOf(2)
        repository.givenGifts(withPhoto, otherPhoto)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)
        viewModel.onFilterChange(GiftFilter.GIVEN)
        viewModel.onQueryChange("b")
        assertEquals(emptyList<String>(), photos.loads)

        viewModel.photos.show("book.jpg")
        viewModel.photos.show("book.jpg")

        assertEquals(listOf("book.jpg"), photos.loads)
        assertTrue(viewModel.photos["book.jpg"]!!.bytes.contentEquals(byteArrayOf(1)))
        assertNull(viewModel.photos["headphones.jpg"])
    }

    @Test
    fun `a gift can be selected, and cleared again`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)
        assertNull(viewModel.uiState.value.selectedGiftId)

        viewModel.onGiftSelected(book)
        assertEquals(book.gift.id, viewModel.uiState.value.selectedGiftId)

        viewModel.onSelectionCleared()
        assertNull(viewModel.uiState.value.selectedGiftId)
    }

    @Test
    fun `the selection is restored from saved state once the history loads`() = runTest {
        val savedState = SavedStateHandle()
        repository.givenGifts(book)
        viewModel(savedState).onGiftSelected(book)

        val restored = viewModel(savedState)
        assertNull("nothing selected before the history loads", restored.uiState.value.selectedGiftId)
        keepSubscribed(restored.uiState)

        assertEquals(book.gift.id, restored.uiState.value.selectedGiftId)
    }

    @Test
    fun `a deleted gift is no longer selected, and the saved value is dropped`() = runTest {
        val savedState = SavedStateHandle()
        repository.givenGifts(book, headphones)
        val viewModel = viewModel(savedState)
        keepSubscribed(viewModel.uiState)

        viewModel.onGiftSelected(book)
        repository.givenGifts(headphones)

        assertNull(viewModel.uiState.value.selectedGiftId)
        assertNull(savedState.get<String>(SELECTION_KEY))
    }

    @Test
    fun `a filter or search that hides the selected gift clears it for good`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)
        viewModel.onGiftSelected(headphones)

        viewModel.onFilterChange(GiftFilter.GIVEN)
        assertNull(viewModel.uiState.value.selectedGiftId)
        viewModel.onFilterChange(GiftFilter.ALL)
        assertNull("not restored by switching back", viewModel.uiState.value.selectedGiftId)

        viewModel.onGiftSelected(book)
        viewModel.onQueryChange("headphones")
        assertNull(viewModel.uiState.value.selectedGiftId)
    }

    @Test
    fun `a filter that still shows the selected gift keeps it`() = runTest {
        repository.givenGifts(book, headphones)
        val viewModel = viewModel()
        keepSubscribed(viewModel.uiState)
        viewModel.onGiftSelected(book)

        viewModel.onFilterChange(GiftFilter.GIVEN)

        assertEquals(book.gift.id, viewModel.uiState.value.selectedGiftId)
    }

    @Test
    fun `a selection is kept while the history cannot be loaded`() = runTest {
        val savedState = SavedStateHandle(mapOf(SELECTION_KEY to book.gift.id.toString()))
        repository.readFailure = IOException("disk")
        val viewModel = viewModel(savedState)
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftHistoryContent.LoadFailed, viewModel.uiState.value.content)
        assertNull(viewModel.uiState.value.selectedGiftId)
        assertEquals("only loaded content proves it gone", book.gift.id.toString(), savedState.get<String>(SELECTION_KEY))
    }

    @Test
    fun `unreadable saved selections are never shown and are dropped once loaded`() = runTest {
        repository.givenGifts(book)
        // The earlier "gift:<id>" and "idea:<id>" forms are unreadable too: nothing is restored from them.
        for (saved in listOf("not-a-uuid", "", "gift:${book.gift.id}", "idea:${book.gift.id}")) {
            val savedState = SavedStateHandle(mapOf(SELECTION_KEY to saved))
            val viewModel = viewModel(savedState)
            assertNull(saved, viewModel.uiState.value.selectedGiftId)
            keepSubscribed(viewModel.uiState)

            assertEquals(loaded(book), viewModel.uiState.value.content)
            assertNull(saved, viewModel.uiState.value.selectedGiftId)
            assertNull(saved, savedState.get<String>(SELECTION_KEY))
        }
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = GiftHistoryViewModel(savedState, repository, photos)

    private companion object {
        // The saved-state key is part of what a restored process reads back.
        const val SELECTION_KEY = "selection"
    }
}
