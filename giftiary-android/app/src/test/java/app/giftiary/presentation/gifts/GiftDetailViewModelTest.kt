package app.giftiary.presentation.gifts

import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class GiftDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftRepository()
    private val photos = FakeGiftPhotoRepository()
    private val book = giftEntry(giftDetails(person = anna), anna)

    @Test
    fun `shows the gift and follows edits`() = runTest {
        repository.people[anna.id] = anna
        repository.givenGifts(book)
        val viewModel = GiftDetailViewModel(book.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftDetailContent.Loaded(book), viewModel.uiState.value.content)

        repository.updateGift(book.gift.id, giftDetails(person = anna, name = "Signed book"))
        val content = viewModel.uiState.value.content as GiftDetailContent.Loaded
        assertEquals("Signed book", content.entry.gift.name)
    }

    @Test
    fun `a gift's photo is shown, marked while it waits to upload, and gone once removed`() = runTest {
        val path = photos.importPhoto("content://photo")
        repository.people[anna.id] = anna
        val withPhoto = giftEntry(giftDetails(person = anna).copy(photoPath = path), anna)
        repository.givenGifts(withPhoto)
        photos.pendingUploads.value = setOf(path)
        val viewModel = GiftDetailViewModel(withPhoto.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        val shown = viewModel.uiState.value.photo!!
        assertEquals("content://photo", shown.photo!!.bytes.decodeToString())
        assertFalse(shown.loading)
        assertTrue(shown.uploadPending)

        photos.pendingUploads.value = emptySet()
        assertFalse(viewModel.uiState.value.photo!!.uploadPending)

        repository.updateGift(withPhoto.gift.id, giftDetails(person = anna))
        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun `a change still waiting to sync, or a photo waiting to upload, is pending`() = runTest {
        val path = photos.importPhoto("content://photo")
        val withPhoto = giftEntry(giftDetails(person = anna).copy(photoPath = path), anna)
        repository.givenGifts(withPhoto)
        val viewModel = GiftDetailViewModel(withPhoto.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)
        assertFalse(viewModel.uiState.value.pendingSync)

        repository.waitingToSync.value = setOf(withPhoto.gift.id)
        assertTrue(viewModel.uiState.value.waitingToSync)
        assertTrue(viewModel.uiState.value.pendingSync)

        repository.waitingToSync.value = emptySet()
        photos.pendingUploads.value = setOf(path)
        assertFalse(viewModel.uiState.value.waitingToSync)
        assertTrue(viewModel.uiState.value.pendingSync)
    }

    @Test
    fun `a gift without a photo shows none`() = runTest {
        repository.people[anna.id] = anna
        repository.givenGifts(book)
        val viewModel = GiftDetailViewModel(book.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun `a photo that cannot be fetched shows as unavailable, not loading`() = runTest {
        val missing = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-00000000ffff.jpg"
        repository.people[anna.id] = anna
        val entry = giftEntry(giftDetails(person = anna).copy(photoPath = missing), anna)
        repository.givenGifts(entry)
        val viewModel = GiftDetailViewModel(entry.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftDetailPhoto(photo = null, loading = false), viewModel.uiState.value.photo)
    }

    @Test
    fun `a missing gift shows not found`() = runTest {
        val viewModel = GiftDetailViewModel(UUID.randomUUID(), repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun `deleting asks for confirmation and then reports the deletion`() = runTest {
        repository.givenGifts(book)
        val viewModel = GiftDetailViewModel(book.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(listOf(book), repository.currentGifts)

        viewModel.onDeleteConfirmed()
        assertEquals(GiftDetailContent.Deleted, viewModel.uiState.value.content)
        assertTrue(repository.currentGifts.isEmpty())
    }

    @Test
    fun `a failed deletion keeps the gift and reports the error`() = runTest {
        repository.givenGifts(book)
        repository.writeFailure = IOException("disk full")
        val viewModel = GiftDetailViewModel(book.gift.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertTrue(state.actions.deleteFailed)
        assertFalse(state.actions.isDeleting)
        assertEquals(GiftDetailContent.Loaded(book), state.content)
    }
}
