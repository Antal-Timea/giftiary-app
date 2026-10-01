package app.gifty.presentation.occasions

import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.usecase.OccasionInputError
import app.gifty.domain.usecase.SaveOccasionUseCase
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.occasion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class OccasionEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val personId = UUID.randomUUID()
    private val repository = FakeOccasionRepository().apply { knownPersonIds += personId }

    private fun viewModel(target: OccasionEditorTarget = OccasionEditorTarget.New(personId)) =
        OccasionEditorViewModel(target, repository, SaveOccasionUseCase(repository))

    @Test
    fun `a new occasion starts as a birthday without year`() {
        val state = viewModel().uiState.value

        assertTrue(state.isNew)
        assertEquals(OccasionEditorStatus.EDITING, state.status)
        assertEquals(OccasionType.BIRTHDAY, state.type)
        assertFalse(state.showsTitle)
        assertFalse(state.showsRepeatsYearly)
    }

    @Test
    fun `saving a valid birthday closes the editor`() = runTest {
        val viewModel = viewModel()

        viewModel.onDayChange("12")
        viewModel.onMonthChange(3)
        viewModel.save()

        assertEquals(OccasionEditorStatus.CLOSED, viewModel.uiState.value.status)
        val saved = repository.currentOccasions.single()
        assertEquals(personId, saved.personId)
        assertNull(saved.year)
    }

    @Test
    fun `invalid leap day stays in the editor and clears once the date changes`() = runTest {
        val viewModel = viewModel()
        viewModel.onDayChange("29")
        viewModel.onMonthChange(2)
        viewModel.onYearChange("2025")

        viewModel.save()
        assertEquals(setOf(OccasionInputError.NOT_A_LEAP_YEAR), viewModel.uiState.value.errors)
        assertEquals(OccasionEditorStatus.EDITING, viewModel.uiState.value.status)

        viewModel.onYearChange("2024")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun `custom occasions ask for a title and whether they repeat`() = runTest {
        val viewModel = viewModel()

        viewModel.onTypeChange(OccasionType.CUSTOM)
        viewModel.onDayChange("18")
        viewModel.onMonthChange(6)
        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.showsTitle)
        assertTrue(state.showsRepeatsYearly)
        assertEquals(setOf(OccasionInputError.TITLE_REQUIRED), state.errors)

        viewModel.onTitleChange("Our anniversary")
        assertTrue(viewModel.uiState.value.errors.isEmpty())
    }

    @Test
    fun `turning off yearly repetition makes the year required`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeChange(OccasionType.CUSTOM)

        viewModel.onRepeatsYearlyChange(false)

        assertTrue(viewModel.uiState.value.yearRequired)
    }

    @Test
    fun `an anniversary offers the repeat choice and defaults to yearly`() {
        val viewModel = viewModel()

        viewModel.onTypeChange(OccasionType.ANNIVERSARY)

        val state = viewModel.uiState.value
        assertTrue(state.showsRepeatsYearly)
        assertTrue(state.repeatsYearly)
        assertFalse(state.yearRequired)
    }

    @Test
    fun `birthdays, name days and Christmas hide the repeat choice`() {
        val viewModel = viewModel()

        listOf(OccasionType.BIRTHDAY, OccasionType.NAME_DAY, OccasionType.CHRISTMAS).forEach { type ->
            viewModel.onTypeChange(type)
            assertFalse("$type", viewModel.uiState.value.showsRepeatsYearly)
        }
    }

    @Test
    fun `switching type resets repetition to yearly`() {
        val viewModel = viewModel()
        viewModel.onTypeChange(OccasionType.CUSTOM)
        viewModel.onRepeatsYearlyChange(false)

        viewModel.onTypeChange(OccasionType.ANNIVERSARY)
        assertTrue(viewModel.uiState.value.repeatsYearly)

        viewModel.onRepeatsYearlyChange(false)
        viewModel.onTypeChange(OccasionType.BIRTHDAY)
        assertTrue(viewModel.uiState.value.repeatsYearly)

        viewModel.onTypeChange(OccasionType.CUSTOM)
        assertTrue(viewModel.uiState.value.repeatsYearly)
        assertTrue(viewModel.uiState.value.showsRepeatsYearly)
    }

    @Test
    fun `reselecting the current type keeps the repeat choice`() {
        val viewModel = viewModel()
        viewModel.onTypeChange(OccasionType.ANNIVERSARY)
        viewModel.onRepeatsYearlyChange(false)

        viewModel.onTypeChange(OccasionType.ANNIVERSARY)

        assertFalse(viewModel.uiState.value.repeatsYearly)
    }

    @Test
    fun `a one-time anniversary is saved once it has a year`() = runTest {
        val viewModel = viewModel()
        viewModel.onTypeChange(OccasionType.ANNIVERSARY)
        viewModel.onRepeatsYearlyChange(false)
        viewModel.onDayChange("18")
        viewModel.onMonthChange(6)

        viewModel.save()
        assertEquals(setOf(OccasionInputError.YEAR_REQUIRED), viewModel.uiState.value.errors)
        assertTrue(viewModel.uiState.value.yearRequired)

        viewModel.onYearChange("2015")
        viewModel.save()

        assertEquals(OccasionEditorStatus.CLOSED, viewModel.uiState.value.status)
        val saved = repository.currentOccasions.single()
        assertEquals(OccasionType.ANNIVERSARY, saved.type)
        assertFalse(saved.repeatsYearly)
        assertEquals(2015, saved.year)
    }

    @Test
    fun `day and year accept digits only`() {
        val viewModel = viewModel()

        viewModel.onDayChange("1a")
        viewModel.onDayChange("123")
        viewModel.onYearChange("19-8")
        viewModel.onYearChange("19885")

        assertEquals("", viewModel.uiState.value.day)
        assertEquals("", viewModel.uiState.value.year)
    }

    @Test
    fun `editing loads the stored occasion`() = runTest {
        val stored = occasion(personId, OccasionDetails(OccasionType.CUSTOM, "Graduation", 15, 6, 2027, false))
        repository.givenOccasions(stored)

        val state = viewModel(OccasionEditorTarget.Existing(stored.id)).uiState.value

        assertFalse(state.isNew)
        assertEquals(OccasionType.CUSTOM, state.type)
        assertEquals("Graduation", state.title)
        assertEquals("15", state.day)
        assertEquals(6, state.month)
        assertEquals("2027", state.year)
        assertFalse(state.repeatsYearly)
    }

    @Test
    fun `an existing occasion of a type not offered for people stays selectable`() = runTest {
        val christmas = occasion(personId, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
        repository.givenOccasions(christmas)

        val state = viewModel(OccasionEditorTarget.Existing(christmas.id)).uiState.value

        assertTrue(OccasionType.CHRISTMAS in state.typeOptions)
        assertFalse(OccasionType.CHRISTMAS in viewModel().uiState.value.typeOptions)
    }

    @Test
    fun `a missing occasion shows not found`() = runTest {
        val state = viewModel(OccasionEditorTarget.Existing(UUID.randomUUID())).uiState.value

        assertEquals(OccasionEditorStatus.NOT_FOUND, state.status)
    }

    @Test
    fun `adding for a person that is gone shows person not found`() = runTest {
        val viewModel = viewModel(OccasionEditorTarget.New(UUID.randomUUID()))
        viewModel.onDayChange("12")
        viewModel.onMonthChange(3)

        viewModel.save()

        assertEquals(OccasionEditorStatus.PERSON_NOT_FOUND, viewModel.uiState.value.status)
    }

    @Test
    fun `deleting asks for confirmation and closes the editor`() = runTest {
        val stored = occasion(personId)
        repository.givenOccasions(stored)
        val viewModel = viewModel(OccasionEditorTarget.Existing(stored.id))

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertEquals(listOf(stored), repository.currentOccasions)

        viewModel.onDeleteConfirmed()
        assertEquals(OccasionEditorStatus.CLOSED, viewModel.uiState.value.status)
        assertTrue(repository.currentOccasions.isEmpty())
    }

    @Test
    fun `a failed write keeps the input and reports the error`() = runTest {
        repository.writeFailure = IOException("disk full")
        val viewModel = viewModel()
        viewModel.onDayChange("12")
        viewModel.onMonthChange(3)

        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.writeFailed)
        assertFalse(state.isBusy)
        assertEquals(OccasionEditorStatus.EDITING, state.status)
        assertEquals("12", state.day)
    }
}
