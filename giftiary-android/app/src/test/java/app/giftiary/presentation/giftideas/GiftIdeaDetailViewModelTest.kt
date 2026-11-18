package app.giftiary.presentation.giftideas

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.PhotoEdit
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
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

class GiftIdeaDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val repository = FakeGiftIdeaRepository()
    private val photos = FakeGiftPhotoRepository()
    private val scarf = ideaEntry(ideaDetails(anna), anna)

    @Test
    fun `status changes apply directly, forward and back`() = runTest {
        repository.givenIdeas(scarf)
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onStatusChange(GiftIdeaStatus.PURCHASED)
        assertEquals(GiftIdeaStatus.PURCHASED, loadedStatus(viewModel))

        viewModel.onStatusChange(GiftIdeaStatus.IDEA)
        assertEquals(GiftIdeaStatus.IDEA, loadedStatus(viewModel))
    }

    @Test
    fun `a failed status change is reported and the stored status stays`() = runTest {
        repository.givenIdeas(scarf)
        repository.writeFailure = IOException("disk full")
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onStatusChange(GiftIdeaStatus.PLANNED)

        assertTrue(viewModel.uiState.value.actions.writeFailed)
        assertFalse(viewModel.uiState.value.actions.isBusy)
        assertEquals(GiftIdeaStatus.IDEA, loadedStatus(viewModel))
    }

    @Test
    fun `deleting asks for confirmation and then reports the deletion`() = runTest {
        repository.givenIdeas(scarf)
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(listOf(scarf), repository.currentIdeas)

        viewModel.onDeleteConfirmed()
        assertEquals(GiftIdeaDetailContent.Deleted, viewModel.uiState.value.content)
        assertTrue(repository.currentIdeas.isEmpty())
    }

    @Test
    fun `an idea that is gone shows not found`() = runTest {
        val viewModel = GiftIdeaDetailViewModel(UUID.randomUUID(), repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(GiftIdeaDetailContent.NotFound, viewModel.uiState.value.content)
    }

    private fun loadedStatus(viewModel: GiftIdeaDetailViewModel): GiftIdeaStatus =
        (viewModel.uiState.value.content as GiftIdeaDetailContent.Loaded).entry.idea.status

    @Test
    fun `waiting to sync follows the idea's own queued change and is never assumed`() = runTest {
        repository.givenIdeas(scarf)
        val viewModel = GiftIdeaDetailViewModel(scarf.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)
        assertFalse(viewModel.uiState.value.waitingToSync)

        repository.waitingToSync.value = setOf(scarf.idea.id)
        assertTrue(viewModel.uiState.value.waitingToSync)

        repository.waitingToSync.value = setOf(UUID.randomUUID())
        assertFalse(viewModel.uiState.value.waitingToSync)
    }

    @Test
    fun `an idea's photo is shown, follows a replacement and status changes, and is gone once removed`() = runTest {
        val first = photos.importPhoto("content://first")
        val withPhoto = ideaEntry(ideaDetails(anna, photoPath = first), anna)
        repository.givenIdeas(withPhoto)
        val viewModel = GiftIdeaDetailViewModel(withPhoto.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)
        assertEquals("content://first", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())

        viewModel.onStatusChange(GiftIdeaStatus.PURCHASED)
        assertEquals("content://first", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())
        assertEquals("read once, not on every change", 1, photos.loads.size)

        val second = photos.importPhoto("content://second")
        repository.updateGiftIdea(withPhoto.idea.id, ideaDetails(anna, photoPath = second))
        assertEquals("content://second", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())

        repository.updateGiftIdea(withPhoto.idea.id, ideaDetails(anna).copy(photo = PhotoEdit.Remove))
        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun `a photo not available at first is shown on retry, with the same path and the page still open`() = runTest {
        val path = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000042.jpg"
        val synced = ideaEntry(ideaDetails(anna, photoPath = path), anna)
        repository.givenIdeas(synced)
        val viewModel = GiftIdeaDetailViewModel(synced.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)
        assertEquals("offline, never downloaded", GiftIdeaDetailPhoto(photo = null, loading = false), viewModel.uiState.value.photo)

        photos.stored[path] = "downloaded".encodeToByteArray()
        viewModel.onPhotoRetry()

        assertEquals("downloaded", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())
        assertEquals(listOf(path, path), photos.loads)
        assertTrue(viewModel.uiState.value.content is GiftIdeaDetailContent.Loaded)
    }

    @Test
    fun `a photo read that throws can be retried, and a later path change is still followed`() = runTest {
        val path = photos.importPhoto("content://scarf")
        val withPhoto = ideaEntry(ideaDetails(anna, photoPath = path), anna)
        repository.givenIdeas(withPhoto)
        photos.loadFailures += IOException("disk")
        val viewModel = GiftIdeaDetailViewModel(withPhoto.idea.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals("retryable, not \"no photo\"", GiftIdeaDetailPhoto(photo = null, loading = false), viewModel.uiState.value.photo)
        assertTrue(viewModel.uiState.value.content is GiftIdeaDetailContent.Loaded)

        viewModel.onPhotoRetry()
        assertEquals("content://scarf", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())
        assertEquals(listOf(path, path), photos.loads)

        val next = photos.importPhoto("content://next")
        repository.updateGiftIdea(withPhoto.idea.id, ideaDetails(anna, photoPath = next))
        assertEquals("content://next", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())
    }

    @Test
    fun `an idea without a photo shows none, and one that cannot be fetched is not loading`() = runTest {
        repository.givenIdeas(scarf)
        val withoutPhoto = GiftIdeaDetailViewModel(scarf.idea.id, repository, photos)
        keepSubscribed(withoutPhoto.uiState)
        assertNull(withoutPhoto.uiState.value.photo)
        assertTrue(photos.loads.isEmpty())

        val missing = ideaEntry(ideaDetails(anna, photoPath = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000099.jpg"), anna)
        repository.givenIdeas(scarf, missing)
        val unavailable = GiftIdeaDetailViewModel(missing.idea.id, repository, photos)
        keepSubscribed(unavailable.uiState)
        assertEquals(GiftIdeaDetailPhoto(photo = null, loading = false), unavailable.uiState.value.photo)
    }
}
