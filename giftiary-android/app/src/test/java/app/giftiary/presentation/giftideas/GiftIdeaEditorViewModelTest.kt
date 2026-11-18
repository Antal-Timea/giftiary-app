package app.giftiary.presentation.giftideas

import app.giftiary.presentation.gifts.GiftPhotoError
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.PhotoEdit
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.usecase.GiftIdeaInputError
import app.giftiary.domain.usecase.SaveGiftIdeaUseCase
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftPhotoRepository
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
    private val photos = FakeGiftPhotoRepository()
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
        GiftIdeaEditorViewModel(target, people, occasions, ideas, SaveGiftIdeaUseCase(ideas), photos)

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

    @Test
    fun `a new idea offers no delete`() {
        val viewModel = viewModel()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertFalse(viewModel.uiState.value.confirmingDelete)
        assertEquals(GiftIdeaEditorStatus.EDITING, viewModel.uiState.value.status)
    }

    @Test
    fun `deleting an idea asks first, and dismissing keeps it and the edits`() {
        val scarf = ideaEntry(ideaDetails(anna, name = "Scarf"), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        viewModel.onNameChange("Wool scarf")

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertEquals("Scarf", viewModel.uiState.value.savedName)
        viewModel.onDeleteDismissed()

        val state = viewModel.uiState.value
        assertFalse(state.confirmingDelete)
        assertEquals(GiftIdeaEditorStatus.EDITING, state.status)
        assertEquals("Wool scarf", state.name)
        assertEquals(listOf(scarf), ideas.currentIdeas)
    }

    @Test
    fun `confirming deletes the idea and closes as deleted`() {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertEquals(GiftIdeaEditorStatus.DELETED, viewModel.uiState.value.status)
        assertTrue(ideas.currentIdeas.isEmpty())
    }

    @Test
    fun `a failed idea delete stays open with the edits and reports it`() {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        viewModel.onNameChange("Wool scarf")
        ideas.writeFailure = IOException("disk full")

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertEquals(GiftIdeaEditorStatus.EDITING, state.status)
        assertEquals(GiftIdeaWriteError.DELETE_FAILED, state.writeError)
        assertFalse(state.isBusy)
        assertEquals("Wool scarf", state.name)
    }

    // ---- photo ----

    @Test
    fun `a new idea can be saved with a picked photo, and without one names none`() = runTest {
        val withPhoto = viewModel(GiftIdeaEditorTarget.New(anna.id))
        withPhoto.onNameChange("Scarf")
        withPhoto.onPhotoPicked("content://scarf")
        val path = withPhoto.uiState.value.photoPath!!
        assertEquals("content://scarf", withPhoto.uiState.value.photo!!.bytes.decodeToString())
        withPhoto.save()
        assertEquals(PhotoEdit.Replace(path), ideas.lastSaved!!.photo)

        val plain = viewModel(GiftIdeaEditorTarget.New(anna.id))
        plain.onNameChange("Mug")
        plain.save()
        assertNull(ideas.currentIdeas.last().idea.photoPath)
        assertEquals(listOf("content://scarf"), photos.imported)
    }

    @Test
    fun `an existing idea shows its photo, and it can be added, replaced or removed`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna, status = GiftIdeaStatus.PLANNED), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        assertNull(viewModel.uiState.value.photo)

        viewModel.onPhotoPicked("content://first")
        viewModel.save()
        val first = ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath!!

        val again = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        assertEquals("the stored photo is shown", "content://first", again.uiState.value.photo!!.bytes.decodeToString())
        again.onPhotoPicked("content://second")
        again.save()
        val second = ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath!!
        assertTrue(second != first)

        val last = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        last.onPhotoRemoved()
        assertNull(last.uiState.value.photo)
        last.save()
        assertNull(ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath)
        assertEquals("the other fields are kept", GiftIdeaStatus.PLANNED, ideas.getGiftIdea(scarf.idea.id)!!.idea.status)
    }

    @Test
    fun `a name or price save keeps a photo another device changed while the editor was open`() = runTest {
        val opened = photos.importPhoto("content://opened")
        val scarf = ideaEntry(ideaDetails(anna, photoPath = opened), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        // Synced in while the editor is open: replaced on another device.
        val synced = photos.importPhoto("content://synced")
        ideas.updateGiftIdea(scarf.idea.id, ideaDetails(anna, photoPath = synced))

        viewModel.onNameChange("Green scarf")
        viewModel.onEstimatedPriceChange("9900")
        viewModel.save()

        assertEquals(PhotoEdit.Unchanged, ideas.lastSaved!!.photo)
        val saved = ideas.getGiftIdea(scarf.idea.id)!!.idea
        assertEquals("Green scarf", saved.name)
        assertEquals(synced, saved.photoPath)
    }

    @Test
    fun `a photo removed on another device is not brought back by a save here`() = runTest {
        val opened = photos.importPhoto("content://opened")
        val scarf = ideaEntry(ideaDetails(anna, photoPath = opened), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        ideas.updateGiftIdea(scarf.idea.id, ideaDetails(anna).copy(photo = PhotoEdit.Remove))

        viewModel.onNameChange("Green scarf")
        viewModel.save()

        assertNull(ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath)
    }

    @Test
    fun `a failed photo read leaves the save unchanged, not a removal`() = runTest {
        val opened = photos.importPhoto("content://opened")
        val scarf = ideaEntry(ideaDetails(anna, photoPath = opened), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        photos.unreadable += "content://bad"

        viewModel.onPhotoPicked("content://bad")
        viewModel.save()

        assertEquals(PhotoEdit.Unchanged, ideas.lastSaved!!.photo)
        assertEquals(opened, ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath)
    }

    @Test
    fun `editing other fields or the status keeps the photo`() = runTest {
        val photo = photos.importPhoto("content://scarf")
        val scarf = ideaEntry(ideaDetails(anna, photoPath = photo), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))

        viewModel.onNameChange("Green scarf")
        viewModel.onIdeaStatusChange(GiftIdeaStatus.PURCHASED)
        viewModel.save()

        val saved = ideas.getGiftIdea(scarf.idea.id)!!.idea
        assertEquals("Green scarf", saved.name)
        assertEquals(GiftIdeaStatus.PURCHASED, saved.status)
        assertEquals(photo, saved.photoPath)
    }

    @Test
    fun `a camera photo is used, and a cancelled capture discards its file`() = runTest {
        val viewModel = viewModel(GiftIdeaEditorTarget.New(anna.id))
        val cancelled = viewModel.newCaptureTarget()!!
        viewModel.onCaptureCancelled(cancelled)
        assertEquals(listOf(cancelled), photos.discarded)
        assertNull(viewModel.uiState.value.photoPath)

        val target = viewModel.newCaptureTarget()!!
        photos.captured[target] = "camera"
        viewModel.onPhotoCaptured(target)

        assertEquals("camera", viewModel.uiState.value.photo!!.bytes.decodeToString())
        assertTrue(photos.openCaptures.isEmpty())
    }

    @Test
    fun `an unreadable photo is reported and the earlier one kept`() = runTest {
        val viewModel = viewModel(GiftIdeaEditorTarget.New(anna.id))
        viewModel.onPhotoPicked("content://good")
        val kept = viewModel.uiState.value.photoPath
        photos.unreadable += "content://bad"

        viewModel.onPhotoPicked("content://bad")

        assertEquals(GiftPhotoError.UNREADABLE, viewModel.uiState.value.photoError)
        assertEquals(kept, viewModel.uiState.value.photoPath)
        viewModel.onPhotoErrorShown()
        assertNull(viewModel.uiState.value.photoError)
    }

    @Test
    fun `a photo gone from the device when saving is dropped and reported, and the form stays`() = runTest {
        ideas.writeFailure = GiftPhotoUnavailableException()
        val viewModel = viewModel(GiftIdeaEditorTarget.New(anna.id))
        viewModel.onNameChange("Scarf")
        viewModel.onPhotoPicked("content://scarf")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(GiftIdeaWriteError.PHOTO_UNAVAILABLE, state.writeError)
        assertNull(state.photoPath)
        assertNull(state.photo)
        assertEquals("back to no change", PhotoEdit.Unchanged, state.photoEdit)
        assertFalse(state.isBusy)
        assertEquals("Scarf", state.name)
        assertEquals(GiftIdeaEditorStatus.EDITING, state.status)
    }

    @Test
    fun `a replacement gone from the device returns to the idea's photo, so the next save keeps it`() = runTest {
        val opened = photos.importPhoto("content://opened")
        val scarf = ideaEntry(ideaDetails(anna, photoPath = opened), anna)
        ideas.givenIdeas(scarf)
        val viewModel = viewModel(GiftIdeaEditorTarget.Existing(scarf.idea.id))
        viewModel.onPhotoPicked("content://new")
        ideas.writeFailure = GiftPhotoUnavailableException()

        viewModel.save()
        assertEquals(opened, viewModel.uiState.value.photoPath)
        assertEquals("content://opened", viewModel.uiState.value.photo!!.bytes.decodeToString())
        ideas.writeFailure = null
        viewModel.save()

        assertEquals(PhotoEdit.Unchanged, ideas.lastSaved!!.photo)
        assertEquals(opened, ideas.getGiftIdea(scarf.idea.id)!!.idea.photoPath)
    }
}
