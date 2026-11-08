package app.giftiary.presentation.people

import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.PhotoKind
import app.giftiary.domain.usecase.SavePersonUseCase
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.person
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class PersonEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePersonRepository()
    private val photos = FakeGiftPhotoRepository()

    private fun viewModel(personId: UUID? = null) =
        PersonEditorViewModel(personId, repository, SavePersonUseCase(repository), photos)

    @Test
    fun `editing starts with the stored details`() = runTest {
        val anna = person("Anna", relationship = "Sister")
        repository.givenPeople(anna)

        val state = viewModel(anna.id).uiState.value

        assertFalse(state.isNewPerson)
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals("Anna", state.name)
        assertEquals("Sister", state.relationship)
    }

    @Test
    fun `editing a person that no longer exists shows not found`() = runTest {
        assertEquals(EditorStatus.NOT_FOUND, viewModel(UUID.randomUUID()).uiState.value.status)
    }

    @Test
    fun `saving a blank name shows the validation message until the name changes`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("  ")
        viewModel.save()
        assertTrue(viewModel.uiState.value.showNameRequired)
        assertEquals(EditorStatus.EDITING, viewModel.uiState.value.status)

        viewModel.onNameChange("A")
        assertFalse(viewModel.uiState.value.showNameRequired)
    }

    @Test
    fun `a successful save closes the editor`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange(" Anna ")
        viewModel.onRelationshipChange("Sister")
        viewModel.save()

        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
        val anna = repository.currentPeople.single()
        assertEquals("Anna", anna.name)
        assertEquals("the person to open next", anna.id, viewModel.uiState.value.savedPersonId)
    }

    @Test
    fun `repeated save taps while saving store the person once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val viewModel = viewModel()

        viewModel.onNameChange("Anna")
        viewModel.save()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.save()
        gate.complete(Unit)

        assertEquals(1, repository.currentPeople.size)
        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
    }

    @Test
    fun `a failed save keeps the input and reports the error`() = runTest {
        repository.writeFailure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onNameChange("Anna")
        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals("Anna", state.name)
    }

    @Test
    fun `saving a person deleted in the meantime shows not found`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)
        repository.givenPeople()

        viewModel.onNameChange("Anna K.")
        viewModel.save()

        assertEquals(EditorStatus.NOT_FOUND, viewModel.uiState.value.status)
    }

    @Test
    fun `a new person offers no delete`() {
        val viewModel = viewModel()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertFalse(viewModel.uiState.value.confirmingDelete)
        assertEquals(EditorStatus.EDITING, viewModel.uiState.value.status)
    }

    @Test
    fun `deleting asks first, and dismissing keeps the person and the edits`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)
        viewModel.onNameChange("Anna K.")

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertEquals("the confirmation names the stored person", "Anna", viewModel.uiState.value.savedName)
        viewModel.onDeleteDismissed()

        val state = viewModel.uiState.value
        assertFalse(state.confirmingDelete)
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals("Anna K.", state.name)
        assertEquals(listOf(anna), repository.currentPeople)
    }

    @Test
    fun `confirming deletes the person and closes as deleted`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertEquals(EditorStatus.DELETED, viewModel.uiState.value.status)
        assertTrue(repository.currentPeople.isEmpty())
    }

    @Test
    fun `a failed delete stays open with the edits and reports it`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)
        viewModel.onNameChange("Anna K.")
        repository.writeFailure = IOException("disk full")

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertEquals(EditorStatus.EDITING, state.status)
        assertTrue(state.deleteFailed)
        assertFalse(state.isBusy)
        assertEquals("Anna K.", state.name)
        viewModel.onDeleteErrorShown()
        assertFalse(viewModel.uiState.value.deleteFailed)
    }

    // ---- photo ----

    @Test
    fun `a new person saved with a picked photo stores it in the same add, shaped as an avatar`() = runTest {
        val viewModel = viewModel()

        viewModel.onPhotoPicked("content://picked")
        val path = viewModel.uiState.value.photoPath
        assertNotNull(viewModel.uiState.value.photo)
        assertEquals(listOf(PhotoKind.PERSON), photos.importedKinds)
        viewModel.onNameChange("Anna")
        viewModel.save()

        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
        assertEquals(path, repository.currentPeople.single().photoPath)
        assertTrue("a new person needs no second write", repository.photoWrites.isEmpty())
    }

    @Test
    fun `a photo taken with the camera is imported as an avatar, and a cancelled one is discarded`() = runTest {
        val viewModel = viewModel()
        val taken = viewModel.newCaptureTarget()!!
        photos.captured[taken] = "camera bytes"

        viewModel.onPhotoCaptured(taken)
        assertNotNull(viewModel.uiState.value.photoPath)
        assertEquals(listOf(PhotoKind.PERSON), photos.importedKinds)

        val cancelled = viewModel.newCaptureTarget()!!
        viewModel.onCaptureCancelled(cancelled)
        assertEquals(listOf(cancelled), photos.discarded)
        assertTrue(photos.openCaptures.isEmpty())
    }

    @Test
    fun `without a photo a new person is saved with none`() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("Anna")

        viewModel.save()

        assertNull(repository.currentPeople.single().photoPath)
    }

    @Test
    fun `an unreadable image is reported and changes nothing`() = runTest {
        photos.unreadable += "content://broken"
        val viewModel = viewModel()

        viewModel.onPhotoPicked("content://broken")

        assertEquals(PersonPhotoError.UNREADABLE, viewModel.uiState.value.photoError)
        assertNull(viewModel.uiState.value.photoPath)
        viewModel.onPhotoErrorShown()
        assertNull(viewModel.uiState.value.photoError)
    }

    @Test
    fun `editing shows the stored photo, and saving unchanged writes no photo`() = runTest {
        val stored = storedPhoto()
        val anna = person("Anna", photoPath = stored)
        repository.givenPeople(anna)
        val viewModel = viewModel(anna.id)

        assertEquals(stored, viewModel.uiState.value.photoPath)
        assertNotNull(viewModel.uiState.value.photo)
        viewModel.save()

        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
        assertTrue(repository.photoWrites.isEmpty())
    }

    @Test
    fun `changing and removing an existing person's photo go through setPersonPhoto`() = runTest {
        val anna = person("Anna", photoPath = storedPhoto())
        repository.givenPeople(anna)

        val changing = viewModel(anna.id)
        changing.onPhotoPicked("content://new")
        val new = changing.uiState.value.photoPath
        changing.save()

        val removing = viewModel(anna.id)
        removing.onPhotoRemoved()
        assertNull(removing.uiState.value.photo)
        removing.save()

        assertEquals(listOf(anna.id to new, anna.id to null), repository.photoWrites)
        assertEquals(EditorStatus.SAVED, removing.uiState.value.status)
    }

    @Test
    fun `a photo that cannot be stored keeps the editor open, never shown as saved`() = runTest {
        val anna = person("Anna")
        repository.givenPeople(anna)
        repository.photoWriteFailure = IOException("disk")
        val viewModel = viewModel(anna.id)
        viewModel.onPhotoPicked("content://new")
        val picked = viewModel.uiState.value.photoPath

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(EditorStatus.EDITING, state.status)
        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
        assertEquals("the pick stays for another try", picked, state.photoPath)

        repository.photoWriteFailure = null
        viewModel.save()
        assertEquals(EditorStatus.SAVED, viewModel.uiState.value.status)
        assertEquals(listOf(anna.id to picked), repository.photoWrites)
    }

    @Test
    fun `a chosen photo gone from the device on save puts the stored one back and says so`() = runTest {
        val stored = storedPhoto()
        val anna = person("Anna", photoPath = stored)
        repository.givenPeople(anna)
        repository.photoWriteFailure = GiftPhotoUnavailableException()
        val viewModel = viewModel(anna.id)
        viewModel.onPhotoPicked("content://new")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(EditorStatus.EDITING, state.status)
        assertEquals(PersonPhotoError.UNAVAILABLE_ON_SAVE, state.photoError)
        assertEquals(stored, state.photoPath)
        assertNotNull(state.photo)
    }

    private fun storedPhoto(): String =
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/20000000-0000-4000-8000-000000000001.jpg".also { photos.stored[it] = byteArrayOf(1, 2) }
}
