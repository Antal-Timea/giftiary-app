package app.giftiary.presentation.giftideas

import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftIdeaAlreadyGivenException
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.MutableClock
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

class MarkGiftIdeaGivenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftIdeaRepository()
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val clock = MutableClock(now)

    private lateinit var defaultLocale: Locale

    @Before
    fun pinLocale() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(id: UUID) = MarkGiftIdeaGivenViewModel(id, repository, clock)

    @Test
    fun `the date defaults to today and the price to the actual one`() {
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(10000, "HUF"), actualPrice = Money(1250, "EUR")), anna)
        repository.givenIdeas(scarf)

        val state = viewModel(scarf.idea.id).uiState.value

        assertEquals(MarkGivenStatus.READY, state.status)
        assertEquals(now.atZone(ZoneId.systemDefault()).toLocalDate(), state.givenAt)
        assertEquals("12.50", state.price)
        assertEquals("EUR", state.currencyCode)
        assertEquals("Anna", state.recipientName)
    }

    @Test
    fun `without an actual price the estimate is suggested`() {
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(10000, "HUF")), anna)
        repository.givenIdeas(scarf)

        val state = viewModel(scarf.idea.id).uiState.value

        assertEquals("10000", state.price)
        assertEquals("HUF", state.currencyCode)
    }

    @Test
    fun `confirming records the chosen date and price and moves on to the gift`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(10000, "HUF")), anna)
        repository.givenIdeas(scarf)
        val viewModel = viewModel(scarf.idea.id)

        viewModel.onDateChange(LocalDate.of(2026, 3, 12))
        viewModel.onPriceChange("9900")
        viewModel.confirm()

        val state = viewModel.uiState.value
        assertEquals(MarkGivenStatus.DONE, state.status)
        assertNotNull(state.giftId)
        assertEquals(listOf(LocalDate.of(2026, 3, 12) to Money(9900, "HUF")), repository.conversions)
    }

    @Test
    fun `clearing the price records the gift without one`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(10000, "HUF")), anna)
        repository.givenIdeas(scarf)
        val viewModel = viewModel(scarf.idea.id)

        viewModel.onPriceChange("")
        viewModel.confirm()

        assertEquals(null, repository.conversions.single().second)
    }

    @Test
    fun `an invalid price is reported without converting`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        repository.givenIdeas(scarf)
        val viewModel = viewModel(scarf.idea.id)

        viewModel.onPriceChange("12,50")
        viewModel.confirm()

        assertEquals(PriceError.TOO_PRECISE, viewModel.uiState.value.priceError)
        assertTrue(repository.conversions.isEmpty())
    }

    @Test
    fun `a failed conversion keeps the input and reports the error`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        repository.givenIdeas(scarf)
        repository.writeFailure = IOException("disk full")
        val viewModel = viewModel(scarf.idea.id)

        viewModel.onDateChange(LocalDate.of(2026, 3, 12))
        viewModel.onPriceChange("9900")
        viewModel.confirm()

        val state = viewModel.uiState.value
        assertTrue(state.writeFailed)
        assertFalse(state.isBusy)
        assertEquals(MarkGivenStatus.READY, state.status)
        assertEquals(LocalDate.of(2026, 3, 12), state.givenAt)
        assertEquals("9900", state.price)
    }

    @Test
    fun `an idea given meanwhile ends the flow, with no gift to open, and confirming again asks nothing`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        repository.givenIdeas(scarf)
        val viewModel = viewModel(scarf.idea.id)
        repository.writeFailure = GiftIdeaAlreadyGivenException(scarf.idea.id)

        viewModel.confirm()

        val state = viewModel.uiState.value
        assertEquals(MarkGivenStatus.ALREADY_GIVEN, state.status)
        assertNull("no gift is guessed or opened", state.giftId)
        assertFalse(state.isBusy)
        assertFalse("not a retryable failure", state.writeFailed)

        repository.writeFailure = null
        viewModel.confirm()
        assertEquals(1, repository.markAsGivenCalls)
        assertEquals(MarkGivenStatus.ALREADY_GIVEN, viewModel.uiState.value.status)
        assertTrue(repository.conversions.isEmpty())
    }

    @Test
    fun `an idea that is no longer active cannot be given`() {
        assertEquals(MarkGivenStatus.NOT_AVAILABLE, viewModel(UUID.randomUUID()).uiState.value.status)
    }

    @Test
    fun `the sheet names the idea's occasion and keeps the estimate beside the price`() {
        val birthday = Occasion(UUID.randomUUID(), anna.id, OccasionType.BIRTHDAY, null, 12, 10, null, true, Instant.EPOCH, Instant.EPOCH)
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(10000, "HUF"), actualPrice = Money(9500, "HUF")), anna)
            .copy(occasion = birthday)
        repository.givenIdeas(scarf)

        val state = viewModel(scarf.idea.id).uiState.value

        assertEquals(birthday, state.occasion)
        assertEquals(Money(10000, "HUF"), state.estimate)
        assertEquals("the price still starts from what was paid", "9500", state.price)
    }

    @Test
    fun `confirming again, as a repeated tap would, converts the idea once`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        repository.givenIdeas(scarf)
        val viewModel = viewModel(scarf.idea.id)

        viewModel.confirm()
        viewModel.confirm()

        assertEquals(MarkGivenStatus.DONE, viewModel.uiState.value.status)
        assertEquals(1, repository.conversions.size)
    }
}
