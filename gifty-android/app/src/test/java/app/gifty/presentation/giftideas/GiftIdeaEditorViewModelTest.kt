package app.gifty.presentation.giftideas

import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.usecase.GiftIdeaInputError
import app.gifty.domain.usecase.SaveGiftIdeaUseCase
import app.gifty.presentation.gifts.PriceError
import app.gifty.testing.FakeGiftIdeaRepository
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.FakePersonRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.ideaDetails
import app.gifty.testing.ideaEntry
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
import java.util.Locale
import java.util.UUID

class GiftIdeaEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val bela = person("Béla")
    private val people = FakePersonRepository().apply { givenPeople(anna, bela) }
    private val occasions = FakeOccasionRepository()
    private val ideas = FakeGiftIdeaRepository().apply {
        people[anna.id] = anna
        people[bela.id] = bela
    }
    private val annasBirthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))

    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        // Existing prices are pre-filled with the default locale; keep it fixed for these tests.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        occasions.givenOccasions(annasBirthday)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(target: GiftIdeaEditorTarget = GiftIdeaEditorTarget.New()) =
        GiftIdeaEditorViewModel(target, people, occasions, ideas, SaveGiftIdeaUseCase(ideas))

    @Test
    fun `a new idea starts as an idea and can preselect its recipient`() {
        val state = viewModel(GiftIdeaEditorTarget.New(anna.id)).uiState.value

        assertTrue(state.isNew)
        assertEquals(GiftIdeaStatus.IDEA, state.ideaStatus)
        assertEquals(anna, state.selectedRecipient)
        assertFalse(state.showsActualPrice)
    }

    @Test
    fun `changing the recipient clears an occasion of the previous one`() {
        val viewModel = viewModel()
        viewModel.onRecipientChange(anna)
        viewModel.onOccasionChange(annasBirthday)
        assertEquals(listOf(null, annasBirthday), viewModel.uiState.value.occasionOptions)

        viewModel.onRecipientChange(bela)

        assertNull(viewModel.uiState.value.occasionId)
        assertEquals(listOf(null), viewModel.uiState.value.occasionOptions)
    }

    @Test
    fun `the actual price appears once purchased and stays while it has a value`() {
        val viewModel = viewModel()

        viewModel.onIdeaStatusChange(GiftIdeaStatus.PURCHASED)
        assertTrue(viewModel.uiState.value.showsActualPrice)

        viewModel.onActualPriceChange("9900")
        viewModel.onIdeaStatusChange(GiftIdeaStatus.PLANNED)
        assertTrue(viewModel.uiState.value.showsActualPrice)
        assertEquals("9900", viewModel.uiState.value.actualPrice)
    }

    @Test
    fun `saving parses both prices with the shared price rules`() = runTest {
        val viewModel = viewModel()
        viewModel.onRecipientChange(anna)
        viewModel.onOccasionChange(annasBirthday)
        viewModel.onNameChange(" Wool scarf ")
        viewModel.onIdeaStatusChange(GiftIdeaStatus.PURCHASED)
        viewModel.onEstimatedPriceChange("10000")
        viewModel.onActualPriceChange("9900")

        viewModel.save()

        assertEquals(GiftIdeaEditorStatus.CLOSED, viewModel.uiState.value.status)
        val saved = ideas.lastSaved!!
        assertEquals(anna.id, saved.recipientPersonId)
        assertEquals(annasBirthday.id, saved.occasionId)
        assertEquals("Wool scarf", saved.name)
        assertEquals(GiftIdeaStatus.PURCHASED, saved.status)
        assertEquals(Money(10000, "HUF"), saved.estimatedPrice)
        assertEquals(Money(9900, "HUF"), saved.actualPrice)
    }

    @Test
    fun `invalid input is reported per field without saving`() = runTest {
        val viewModel = viewModel()
        viewModel.onEstimatedPriceChange("12,50")
        viewModel.onActualPriceChange("1,234.50")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(setOf(GiftIdeaInputError.NAME_REQUIRED, GiftIdeaInputError.RECIPIENT_REQUIRED), state.errors)
        assertEquals(PriceError.TOO_PRECISE, state.estimatedPriceError)
        assertEquals(PriceError.INVALID, state.actualPriceError)
        assertNull(ideas.lastSaved)
    }

    @Test
    fun `an idea whose recipient was deleted can still be edited and keeps them`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna, estimatedPrice = Money(1250, "EUR")), anna)
        ideas.givenIdeas(scarf)
        people.deletePerson(anna.id)
        ideas.people.remove(anna.id)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))

        val loaded = viewModel.uiState.value
        assertEquals(anna, loaded.selectedRecipient)
        assertEquals(listOf(bela, anna), loaded.recipientOptions)
        assertEquals("12.50", loaded.estimatedPrice)
        assertEquals("EUR", loaded.estimatedCurrencyCode)

        viewModel.onNameChange("Wool scarf")
        viewModel.save()

        assertEquals(GiftIdeaEditorStatus.CLOSED, viewModel.uiState.value.status)
        assertEquals(anna.id, ideas.lastSaved!!.recipientPersonId)
    }

    @Test
    fun `a missing idea shows not found`() {
        val state = viewModel(GiftIdeaEditorTarget.Existing(UUID.randomUUID())).uiState.value

        assertEquals(GiftIdeaEditorStatus.NOT_FOUND, state.status)
    }
}
