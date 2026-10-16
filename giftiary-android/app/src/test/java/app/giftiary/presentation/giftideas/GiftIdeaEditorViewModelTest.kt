package app.giftiary.presentation.giftideas

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.usecase.GiftIdeaInputError
import app.giftiary.domain.usecase.SaveGiftIdeaUseCase
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
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

    // General occasions, of no person, such as Christmas

    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
    private val newYear = occasion(null, OccasionDetails(OccasionType.CUSTOM, "New Year", 1, 1, null, true))
    private val belasNameDay = occasion(bela.id, OccasionDetails(OccasionType.NAME_DAY, null, 4, 11, null, true))

    @Test
    fun `the recipient's occasions come first, then the general ones in calendar order, never another person's`() {
        occasions.givenOccasions(christmas, annasBirthday, belasNameDay, newYear)

        val state = viewModel(GiftIdeaEditorTarget.New(anna.id)).uiState.value

        assertEquals(listOf(null, annasBirthday, newYear, christmas), state.occasionOptions)
    }

    @Test
    fun `general occasions are offered before anyone is chosen`() {
        occasions.givenOccasions(annasBirthday, christmas)

        assertEquals(listOf(null, christmas), viewModel().uiState.value.occasionOptions)
    }

    @Test
    fun `an idea is saved with a general occasion`() = runTest {
        occasions.givenOccasions(christmas)
        val viewModel = viewModel(GiftIdeaEditorTarget.New(anna.id))
        viewModel.onOccasionChange(christmas)
        viewModel.onNameChange("Scarf")

        viewModel.save()

        assertEquals(christmas.id, ideas.lastSaved!!.occasionId)
    }

    @Test
    fun `changing the recipient keeps a general occasion and clears the previous one's own`() {
        occasions.givenOccasions(annasBirthday, christmas)
        val viewModel = viewModel(GiftIdeaEditorTarget.New(anna.id))
        viewModel.onOccasionChange(christmas)
        viewModel.onRecipientChange(bela)
        assertEquals(christmas.id, viewModel.uiState.value.occasionId)

        viewModel.onRecipientChange(anna)
        viewModel.onOccasionChange(annasBirthday)
        viewModel.onRecipientChange(bela)

        assertNull(viewModel.uiState.value.occasionId)
    }

    @Test
    fun `an idea's general occasion is offered once, and while selected even after it was deleted`() {
        occasions.givenOccasions(christmas)
        val withChristmas = ideaEntry(ideaDetails(anna).copy(occasionId = christmas.id), anna).copy(occasion = christmas)
        val deletedNewYear = ideaEntry(ideaDetails(anna).copy(occasionId = newYear.id), anna).copy(occasion = newYear)
        ideas.givenIdeas(withChristmas, deletedNewYear)

        assertEquals(listOf(null, christmas), viewModel(GiftIdeaEditorTarget.Existing(withChristmas.idea.id)).uiState.value.occasionOptions)
        val editing = viewModel(GiftIdeaEditorTarget.Existing(deletedNewYear.idea.id))
        assertEquals(listOf(null, christmas, newYear), editing.uiState.value.occasionOptions)

        editing.onOccasionChange(christmas)
        assertEquals("not offered again once something else is chosen", listOf(null, christmas), editing.uiState.value.occasionOptions)
    }

    @Test
    fun `general occasions that cannot be read leave the editor and the idea's occasion alone`() = runTest {
        occasions.readFailure = IOException("disk")
        val withChristmas = ideaEntry(ideaDetails(anna).copy(occasionId = christmas.id), anna).copy(occasion = christmas)
        ideas.givenIdeas(withChristmas)

        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(withChristmas.idea.id))

        assertEquals(GiftIdeaEditorStatus.EDITING, viewModel.uiState.value.status)
        assertEquals(christmas, viewModel.uiState.value.selectedOccasion)
        viewModel.save()
        assertEquals(christmas.id, ideas.lastSaved!!.occasionId)
    }

    // Starting from an occasion's plan

    @Test
    fun `a new idea from a general occasion starts with it, and keeps it once a recipient is chosen`() {
        occasions.givenOccasions(annasBirthday, christmas)
        val viewModel = viewModel(GiftIdeaEditorTarget.New(occasionId = christmas.id))
        assertEquals(christmas, viewModel.uiState.value.selectedOccasion)
        assertNull(viewModel.uiState.value.recipientId)

        viewModel.onRecipientChange(bela)

        assertEquals(christmas, viewModel.uiState.value.selectedOccasion)
    }

    @Test
    fun `a new idea from a person's occasion starts with both`() {
        occasions.givenOccasions(annasBirthday, christmas)

        val state = viewModel(GiftIdeaEditorTarget.New(personId = anna.id, occasionId = annasBirthday.id)).uiState.value

        assertEquals(anna, state.selectedRecipient)
        assertEquals(annasBirthday, state.selectedOccasion)
    }

    @Test
    fun `a starting occasion that is no longer offered is dropped once the choices are known`() {
        occasions.givenOccasions(annasBirthday)
        val deleted = UUID.randomUUID()

        assertNull(viewModel(GiftIdeaEditorTarget.New(occasionId = deleted)).uiState.value.occasionId)
        assertNull(
            "another person's occasion is not offered for Béla",
            viewModel(GiftIdeaEditorTarget.New(personId = bela.id, occasionId = annasBirthday.id)).uiState.value.occasionId,
        )
    }

    @Test
    fun `a starting occasion that cannot be confirmed is dropped`() {
        occasions.givenOccasions(christmas)
        occasions.readFailure = IOException("disk")

        assertNull(viewModel(GiftIdeaEditorTarget.New(occasionId = christmas.id)).uiState.value.occasionId)
    }

    @Test
    fun `a route's malformed occasion is ignored, its person still applies`() {
        assertEquals(GiftIdeaEditorTarget.New(anna.id, null), GiftIdeaEditorTarget.newFor(anna.id.toString(), "not-a-uuid"))
        assertEquals(GiftIdeaEditorTarget.New(null, christmas.id), GiftIdeaEditorTarget.newFor("bad", christmas.id.toString()))
        assertEquals(GiftIdeaEditorTarget.New(anna.id, christmas.id), GiftIdeaEditorTarget.newFor(anna.id.toString(), christmas.id.toString()))
        assertEquals(GiftIdeaEditorTarget.New(), GiftIdeaEditorTarget.newFor(null, null))
    }
}
