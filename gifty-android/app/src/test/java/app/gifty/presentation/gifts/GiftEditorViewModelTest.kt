package app.gifty.presentation.gifts

import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.Money
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.PersonNotFoundException
import app.gifty.domain.usecase.GiftInputError
import app.gifty.domain.usecase.SaveGiftUseCase
import app.gifty.testing.FakeGiftRepository
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.FakePersonRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.MutableClock
import app.gifty.testing.giftDetails
import app.gifty.testing.giftEntry
import app.gifty.testing.occasion
import app.gifty.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class GiftEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val bela = person("Béla")
    private val people = FakePersonRepository().apply { givenPeople(anna, bela) }
    private val occasions = FakeOccasionRepository()
    private val gifts = FakeGiftRepository().apply {
        people[anna.id] = anna
        people[bela.id] = bela
    }
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val clock = MutableClock(now)

    private val annasBirthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
    private val belasNameDay = occasion(bela.id, OccasionDetails(OccasionType.NAME_DAY, null, 4, 11, null, true))

    private lateinit var defaultLocale: Locale

    @Before
    fun pinLocale() {
        // The editor parses prices with the default locale; keep it fixed for these tests.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        occasions.givenOccasions(annasBirthday, belasNameDay)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(target: GiftEditorTarget = GiftEditorTarget.New()) =
        GiftEditorViewModel(target, people, occasions, gifts, SaveGiftUseCase(gifts), clock)

    @Test
    fun `a new gift starts as given today in the default currency`() {
        val state = viewModel().uiState.value

        assertTrue(state.isNew)
        assertEquals(GiftDirection.GIVEN, state.direction)
        assertEquals(now.atZone(ZoneId.systemDefault()).toLocalDate(), state.givenAt)
        assertEquals("HUF", state.currencyCode)
        assertEquals(listOf(anna, bela), state.personOptions)
    }

    @Test
    fun `the route can preselect direction and person`() {
        val state = viewModel(GiftEditorTarget.New(GiftDirection.RECEIVED, anna.id)).uiState.value

        assertEquals(GiftDirection.RECEIVED, state.direction)
        assertEquals(anna, state.selectedPerson)
    }

    @Test
    fun `a preselected person who is not available is dropped`() {
        val state = viewModel(GiftEditorTarget.New(personId = UUID.randomUUID())).uiState.value

        assertNull(state.personId)
    }

    @Test
    fun `only the selected person's occasions are offered`() {
        val viewModel = viewModel()

        viewModel.onPersonChange(anna)

        assertEquals(listOf(null, annasBirthday), viewModel.uiState.value.occasionOptions)
    }

    @Test
    fun `changing the person clears an occasion that belongs to someone else`() {
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)
        viewModel.onOccasionChange(annasBirthday)

        viewModel.onPersonChange(bela)

        val state = viewModel.uiState.value
        assertNull(state.occasionId)
        assertEquals(listOf(null, belasNameDay), state.occasionOptions)
    }

    @Test
    fun `saving a given gift stores the person as recipient and parses the price exactly`() = runTest {
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)
        viewModel.onOccasionChange(annasBirthday)
        viewModel.onNameChange(" Book ")
        viewModel.onPriceChange("12500")
        viewModel.onDateChange(LocalDate.of(2026, 3, 12))

        viewModel.save()

        assertEquals(GiftEditorStatus.CLOSED, viewModel.uiState.value.status)
        val saved = gifts.lastSaved!!
        assertNull(saved.giverPersonId)
        assertEquals(anna.id, saved.recipientPersonId)
        assertEquals(annasBirthday.id, saved.occasionId)
        assertEquals("Book", saved.name)
        assertEquals(Money(12500, "HUF"), saved.price)
        assertEquals(LocalDate.of(2026, 3, 12), saved.givenAt)
    }

    @Test
    fun `saving a received gift stores the person as giver`() = runTest {
        val viewModel = viewModel()
        viewModel.onDirectionChange(GiftDirection.RECEIVED)
        viewModel.onPersonChange(anna)
        viewModel.onNameChange("Headphones")

        viewModel.save()

        assertEquals(anna.id, gifts.lastSaved!!.giverPersonId)
        assertNull(gifts.lastSaved!!.recipientPersonId)
    }

    @Test
    fun `missing details and an invalid price are all reported without saving`() = runTest {
        val viewModel = viewModel()
        viewModel.onPriceChange("12.5")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(setOf(GiftInputError.NAME_REQUIRED, GiftInputError.RECIPIENT_REQUIRED), state.errors)
        assertEquals(PriceError.TOO_PRECISE, state.priceError)
        assertEquals(GiftEditorStatus.EDITING, state.status)
        assertNull(gifts.lastSaved)
    }

    @Test
    fun `a negative price is rejected`() = runTest {
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)
        viewModel.onNameChange("Book")
        viewModel.onPriceChange("-5")

        viewModel.save()

        assertEquals(PriceError.NEGATIVE, viewModel.uiState.value.priceError)
        assertNull(gifts.lastSaved)
    }

    @Test
    fun `without any people the editor asks to add someone first`() {
        people.givenPeople()

        assertTrue(viewModel().uiState.value.needsPerson)
    }

    @Test
    fun `a gift whose person was deleted can still be edited and keeps that person`() = runTest {
        val gift = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Book"), anna)
        gifts.givenGifts(gift)
        people.deletePerson(anna.id)
        gifts.people.remove(anna.id)
        val viewModel = viewModel(GiftEditorTarget.Existing(gift.gift.id))

        val loaded = viewModel.uiState.value
        assertEquals(anna, loaded.selectedPerson)
        assertEquals(listOf(bela, anna), loaded.personOptions)

        viewModel.onNameChange("Signed book")
        viewModel.save()

        assertEquals(GiftEditorStatus.CLOSED, viewModel.uiState.value.status)
        assertEquals(anna.id, gifts.lastSaved!!.recipientPersonId)
    }

    @Test
    fun `switching away from a deleted person does not offer them again`() = runTest {
        val gift = giftEntry(giftDetails(GiftDirection.GIVEN, anna, "Book"), anna)
        gifts.givenGifts(gift)
        people.deletePerson(anna.id)
        val viewModel = viewModel(GiftEditorTarget.Existing(gift.gift.id))

        viewModel.onPersonChange(bela)

        assertEquals(listOf(bela), viewModel.uiState.value.personOptions)
    }

    @Test
    fun `editing loads the price in its own currency`() {
        val gift = giftEntry(giftDetails(GiftDirection.GIVEN, anna).copy(price = Money(1250, "EUR")), anna)
        gifts.givenGifts(gift)

        val state = viewModel(GiftEditorTarget.Existing(gift.gift.id)).uiState.value

        assertEquals("12.50", state.price)
        assertEquals("EUR", state.currencyCode)
    }

    @Test
    fun `a person deleted while editing is reported instead of failing silently`() = runTest {
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)
        viewModel.onNameChange("Book")
        gifts.writeFailure = PersonNotFoundException(anna.id)

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(GiftWriteError.PERSON_UNAVAILABLE, state.writeError)
        assertFalse(state.isBusy)
        assertEquals(GiftEditorStatus.EDITING, state.status)
    }

    @Test
    fun `a failed write keeps the input`() = runTest {
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)
        viewModel.onNameChange("Book")
        gifts.writeFailure = IOException("disk full")

        viewModel.save()

        assertEquals(GiftWriteError.FAILED, viewModel.uiState.value.writeError)
        assertEquals("Book", viewModel.uiState.value.name)
    }

    @Test
    fun `a missing gift shows not found`() {
        val state = viewModel(GiftEditorTarget.Existing(UUID.randomUUID())).uiState.value

        assertEquals(GiftEditorStatus.NOT_FOUND, state.status)
    }
}
