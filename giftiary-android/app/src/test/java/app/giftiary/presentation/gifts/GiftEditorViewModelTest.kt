package app.giftiary.presentation.gifts

import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.PersonNotFoundException
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.domain.usecase.SaveGiftUseCase
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.MutableClock
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
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
    private val photos = FakeGiftPhotoRepository()
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
        // Parsing ignores the locale, but existing prices are pre-filled with the default one.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        occasions.givenOccasions(annasBirthday, belasNameDay)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(target: GiftEditorTarget = GiftEditorTarget.New()) =
        GiftEditorViewModel(target, people, occasions, gifts, SaveGiftUseCase(gifts), photos, clock)

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

    // General occasions, of no person, such as Christmas

    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
    private val newYear = occasion(null, OccasionDetails(OccasionType.CUSTOM, "New Year", 1, 1, null, true))

    @Test
    fun `the person's occasions come first, then the general ones in calendar order`() {
        occasions.givenOccasions(christmas, annasBirthday, belasNameDay, newYear)
        val viewModel = viewModel()

        assertEquals("before anyone is chosen", listOf(null, newYear, christmas), viewModel.uiState.value.occasionOptions)
        viewModel.onPersonChange(anna)
        assertEquals(listOf(null, annasBirthday, newYear, christmas), viewModel.uiState.value.occasionOptions)
    }

    @Test
    fun `a received gift is saved with a general occasion, which a change of person keeps`() = runTest {
        occasions.givenOccasions(annasBirthday, christmas)
        val viewModel = viewModel(GiftEditorTarget.New(GiftDirection.RECEIVED, anna.id))
        viewModel.onOccasionChange(christmas)
        viewModel.onPersonChange(bela)
        viewModel.onNameChange("Book")

        viewModel.save()

        val saved = gifts.lastSaved!!
        assertEquals(christmas.id, saved.occasionId)
        assertEquals(bela.id, saved.giverPersonId)
    }

    @Test
    fun `a gift's deleted general occasion stays offered once while selected`() {
        occasions.givenOccasions(christmas)
        val deletedNewYear = giftEntry(giftDetails(person = anna).copy(occasionId = newYear.id), anna).copy(occasion = newYear)
        gifts.givenGifts(deletedNewYear)

        val state = viewModel(GiftEditorTarget.Existing(deletedNewYear.gift.id)).uiState.value

        assertEquals(listOf(null, christmas, newYear), state.occasionOptions)
        assertEquals(newYear, state.selectedOccasion)
    }

    @Test
    fun `general occasions that cannot be read leave the editor usable`() {
        occasions.readFailure = IOException("disk")
        val viewModel = viewModel()
        viewModel.onPersonChange(anna)

        assertEquals(GiftEditorStatus.EDITING, viewModel.uiState.value.status)
        assertEquals(listOf(null, annasBirthday), viewModel.uiState.value.occasionOptions)
    }

    // ---- photo ----

    @Test
    fun `a picked photo is shown and saved with the gift`() = runTest {
        val viewModel = viewModel(GiftEditorTarget.New(personId = anna.id))
        viewModel.onNameChange("Book")

        viewModel.onPhotoPicked("content://picked/1")

        val state = viewModel.uiState.value
        val path = state.photoPath!!
        assertFalse(state.photoBusy)
        assertEquals("content://picked/1", state.photo!!.bytes.decodeToString())
        viewModel.save()
        assertEquals(path, gifts.lastSaved!!.photoPath)
    }

    @Test
    fun `a gift without a photo is saved without one`() = runTest {
        val viewModel = viewModel(GiftEditorTarget.New(personId = anna.id))
        viewModel.onNameChange("Book")

        viewModel.save()

        assertNull(gifts.lastSaved!!.photoPath)
        assertTrue(photos.imported.isEmpty())
    }

    @Test
    fun `an existing photo is loaded, and can be replaced or removed`() = runTest {
        val path = photos.importPhoto("content://old")
        val entry = giftEntry(giftDetails(person = anna).copy(photoPath = path), anna)
        gifts.givenGifts(entry)
        val viewModel = viewModel(GiftEditorTarget.Existing(entry.gift.id))
        assertEquals("content://old", viewModel.uiState.value.photo!!.bytes.decodeToString())

        viewModel.onPhotoPicked("content://new")
        val replacement = viewModel.uiState.value.photoPath
        assertTrue(replacement != path)
        viewModel.onPhotoRemoved()
        assertNull(viewModel.uiState.value.photo)

        viewModel.save()
        assertNull(gifts.lastSaved!!.photoPath)
    }

    @Test
    fun `an unreadable pick keeps the previous photo and says so`() = runTest {
        photos.unreadable += "content://broken"
        val viewModel = viewModel(GiftEditorTarget.New(personId = anna.id))
        viewModel.onPhotoPicked("content://fine")
        val kept = viewModel.uiState.value.photoPath

        viewModel.onPhotoPicked("content://broken")

        assertEquals(GiftPhotoError.UNREADABLE, viewModel.uiState.value.photoError)
        assertEquals(kept, viewModel.uiState.value.photoPath)
        viewModel.onPhotoErrorShown()
        assertNull(viewModel.uiState.value.photoError)
    }

    @Test
    fun `a photo gone from the device when saving is dropped and reported`() = runTest {
        gifts.writeFailure = GiftPhotoUnavailableException()
        val viewModel = viewModel(GiftEditorTarget.New(personId = anna.id))
        viewModel.onNameChange("Book")
        viewModel.onPhotoPicked("content://picked")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(GiftWriteError.PHOTO_UNAVAILABLE, state.writeError)
        assertNull(state.photoPath)
        assertEquals(GiftEditorStatus.EDITING, state.status)
    }
}
