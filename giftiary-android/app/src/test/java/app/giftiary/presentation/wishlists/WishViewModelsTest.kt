package app.giftiary.presentation.wishlists

import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.WishDetails
import app.giftiary.domain.usecase.SaveWishUseCase
import app.giftiary.presentation.gifts.GiftPhotoError
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.gifts.PriceFormat
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.wish
import app.giftiary.testing.wishlist
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** The wish editor and the wish detail screen. */
class WishViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeWishlistRepository()
    private val list = wishlist("Birthday").also { repository.givenWishlists(it) }

    private val photos = FakeGiftPhotoRepository()

    private fun editor(target: WishEditorTarget) = WishEditorViewModel(target, repository, SaveWishUseCase(repository), photos)

    // ---- editor ----

    @Test
    fun `a new wish is stored on its wishlist, with blank optional fields as nothing`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))
        assertEquals(WishEditorStatus.EDITING, viewModel.uiState.value.status)

        viewModel.onNameChange(" Lamp ")
        viewModel.onPriceChange("12990")
        viewModel.onProductUrlChange("  ")
        viewModel.onNoteChange(" ")
        viewModel.save()

        val stored = repository.currentWishes.single()
        assertEquals(list.id, stored.wishlistId)
        assertEquals("Lamp", stored.name)
        assertEquals((PriceFormat.parse("12990", viewModel.uiState.value.currencyCode) as PriceFormat.ParseResult.Valid).money, stored.estimatedPrice)
        assertNull(stored.productUrl)
        assertNull(stored.note)
        assertEquals(WishEditorStatus.SAVED, viewModel.uiState.value.status)
    }

    @Test
    fun `a missing name and an invalid price are both shown and nothing is stored`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))
        viewModel.onNameChange(" ")
        viewModel.onPriceChange("-5")

        viewModel.save()

        assertTrue(viewModel.uiState.value.showNameRequired)
        assertEquals(PriceError.NEGATIVE, viewModel.uiState.value.priceError)
        assertTrue(repository.currentWishes.isEmpty())
        viewModel.onPriceChange("5")
        assertNull(viewModel.uiState.value.priceError)
    }

    @Test
    fun `editing starts from the stored wish and keeps it on its wishlist`() = runTest {
        val lamp = wish(list.id, "Lamp", note = "Brass", estimatedPrice = Money(12990, "HUF"), productUrl = "https://example.com")
        repository.givenWishes(lamp)
        val viewModel = editor(WishEditorTarget.Existing(lamp.id))

        val state = viewModel.uiState.value
        assertEquals("Lamp", state.name)
        assertEquals("Brass", state.note)
        assertEquals("HUF", state.currencyCode)
        assertEquals(PriceFormat.formatForInput(Money(12990, "HUF"), Locale.getDefault()), state.price)
        viewModel.onNoteChange("")
        viewModel.save()

        val stored = repository.currentWishes.single()
        assertEquals(lamp.copy(note = null), stored)
    }

    @Test
    fun `a wish cannot be added to a wishlist that is gone`() = runTest {
        assertEquals(WishEditorStatus.WISHLIST_NOT_FOUND, editor(WishEditorTarget.New(UUID.randomUUID())).uiState.value.status)

        val viewModel = editor(WishEditorTarget.New(list.id))
        repository.givenWishlists()
        viewModel.onNameChange("Lamp")
        viewModel.save()
        assertEquals(WishEditorStatus.WISHLIST_NOT_FOUND, viewModel.uiState.value.status)
        assertTrue(repository.currentWishes.isEmpty())
    }

    @Test
    fun `a wish that is gone cannot be edited`() = runTest {
        assertEquals(WishEditorStatus.NOT_FOUND, editor(WishEditorTarget.Existing(UUID.randomUUID())).uiState.value.status)
    }

    @Test
    fun `a failed write is reported and the form stays`() = runTest {
        repository.writeFailure = IOException("disk")
        val viewModel = editor(WishEditorTarget.New(list.id))
        viewModel.onNameChange("Lamp")

        viewModel.save()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(WishEditorStatus.EDITING, viewModel.uiState.value.status)
    }

    // ---- photo ----

    @Test
    fun `a new wish can be saved with a picked photo`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))
        viewModel.onNameChange("Lamp")
        viewModel.onPhotoPicked("content://lamp")

        val path = viewModel.uiState.value.photoPath!!
        assertEquals("content://lamp", viewModel.uiState.value.photo!!.bytes.decodeToString())
        viewModel.save()

        assertEquals(path, repository.currentWishes.single().photoPath)
    }

    @Test
    fun `a wish saved without a photo names none`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))
        viewModel.onNameChange("Lamp")
        viewModel.save()

        assertNull(repository.currentWishes.single().photoPath)
        assertTrue(photos.imported.isEmpty())
    }

    @Test
    fun `an existing wish shows its photo, and it can be added, replaced or removed`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = editor(WishEditorTarget.Existing(lamp.id))
        assertNull(viewModel.uiState.value.photo)

        viewModel.onPhotoPicked("content://first")
        viewModel.save()
        val first = repository.currentWishes.single().photoPath!!

        val again = editor(WishEditorTarget.Existing(lamp.id))
        assertEquals("the stored photo is shown", "content://first", again.uiState.value.photo!!.bytes.decodeToString())
        again.onPhotoPicked("content://second")
        again.save()
        val second = repository.currentWishes.single().photoPath!!
        assertTrue(second != first)

        val last = editor(WishEditorTarget.Existing(lamp.id))
        last.onPhotoRemoved()
        assertNull(last.uiState.value.photo)
        last.save()
        assertNull(repository.currentWishes.single().photoPath)
        assertEquals("the other fields are kept", "Lamp", repository.currentWishes.single().name)
    }

    @Test
    fun `a camera photo is used, and a cancelled capture discards its file`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))
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
        val viewModel = editor(WishEditorTarget.New(list.id))
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
        repository.writeFailure = GiftPhotoUnavailableException()
        val viewModel = editor(WishEditorTarget.New(list.id))
        viewModel.onNameChange("Lamp")
        viewModel.onPhotoPicked("content://lamp")

        viewModel.save()

        val state = viewModel.uiState.value
        assertTrue(state.photoUnavailableOnSave)
        assertFalse(state.saveFailed)
        assertNull(state.photoPath)
        assertEquals("Lamp", state.name)
        assertEquals(WishEditorStatus.EDITING, state.status)
        viewModel.onSaveErrorShown()
        assertFalse(viewModel.uiState.value.photoUnavailableOnSave)
    }

    // ---- detail ----

    @Test
    fun `a wish's photo is shown, follows a replacement, and is gone once removed`() = runTest {
        val first = photos.importPhoto("content://first")
        val lamp = wish(list.id, "Lamp", photoPath = first).also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals("content://first", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())

        val second = photos.importPhoto("content://second")
        repository.updateWish(lamp.id, WishDetails("Lamp", null, null, null, second))
        assertEquals("content://second", viewModel.uiState.value.photo!!.photo!!.bytes.decodeToString())

        repository.updateWish(lamp.id, WishDetails("Lamp", null, null, null, null))
        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun `a wish without a photo shows none, and one that cannot be fetched is not loading`() = runTest {
        val plain = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val withoutPhoto = WishDetailViewModel(plain.id, repository, photos)
        keepSubscribed(withoutPhoto.uiState)
        assertNull(withoutPhoto.uiState.value.photo)
        assertTrue(photos.loads.isEmpty())

        val missing = wish(list.id, "Mug", photoPath = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000099.jpg")
        repository.givenWishes(plain, missing)
        val unavailable = WishDetailViewModel(missing.id, repository, photos)
        keepSubscribed(unavailable.uiState)
        assertEquals(WishDetailPhoto(photo = null, loading = false), unavailable.uiState.value.photo)
    }

    @Test
    fun `a wish shows with its wishlist's name and follows edits`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(WishDetailContent.Loaded(lamp, "Birthday"), viewModel.uiState.value.content)

        repository.updateWish(lamp.id, WishDetails("Brass lamp", null, null, null, null))
        repository.renameWishlist(list.id, "Name day")

        val content = viewModel.uiState.value.content as WishDetailContent.Loaded
        assertEquals("Brass lamp", content.wish.name)
        assertEquals("Name day", content.wishlistName)
    }

    @Test
    fun `a wish deleted elsewhere, or with its wishlist, shows not found`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        repository.deleteWishlist(list.id)

        assertEquals(WishDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun `deleting a wish asks first, then deletes only that wish`() = runTest {
        val lamp = wish(list.id, "Lamp")
        val mug = wish(list.id, "Mug")
        repository.givenWishes(lamp, mug)
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(2, repository.currentWishes.size)
        viewModel.onDeleteConfirmed()

        assertEquals(WishDetailContent.Deleted, viewModel.uiState.value.content)
        assertEquals(listOf(mug), repository.currentWishes)
        assertEquals(listOf(list), repository.currentWishlists)
    }

    @Test
    fun `a failed wish deletion is reported and the wish stays`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        repository.writeFailure = IOException("disk")
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.uiState.value.actions.writeFailed)
        assertTrue(viewModel.uiState.value.content is WishDetailContent.Loaded)
    }

    @Test
    fun `the wish editor names the list the wish is on, adding or editing`() = runTest {
        val lamp = wish(list.id, "Lamp")
        repository.givenWishes(lamp)

        assertEquals("Birthday", editor(WishEditorTarget.New(list.id)).uiState.value.wishlistName)
        assertEquals("Birthday", editor(WishEditorTarget.Existing(lamp.id)).uiState.value.wishlistName)
    }

    @Test
    fun `a new wish offers no delete`() = runTest {
        val viewModel = editor(WishEditorTarget.New(list.id))

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertFalse(viewModel.uiState.value.confirmingDelete)
        assertEquals(WishEditorStatus.EDITING, viewModel.uiState.value.status)
    }

    @Test
    fun `deleting from the wish editor asks first, and dismissing keeps the wish and the edits`() = runTest {
        val lamp = wish(list.id, "Lamp")
        repository.givenWishes(lamp)
        val viewModel = editor(WishEditorTarget.Existing(lamp.id))
        viewModel.onNameChange("Brass lamp")

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertEquals("Lamp", viewModel.uiState.value.savedName)
        viewModel.onDeleteDismissed()

        val state = viewModel.uiState.value
        assertFalse(state.confirmingDelete)
        assertEquals(WishEditorStatus.EDITING, state.status)
        assertEquals("Brass lamp", state.name)
        assertEquals(listOf(lamp), repository.currentWishes)
    }

    @Test
    fun `confirming in the wish editor deletes only that wish and closes as deleted`() = runTest {
        val lamp = wish(list.id, "Lamp")
        val book = wish(list.id, "Book")
        repository.givenWishes(lamp, book)
        val viewModel = editor(WishEditorTarget.Existing(lamp.id))

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertEquals(WishEditorStatus.DELETED, viewModel.uiState.value.status)
        assertEquals(listOf(book), repository.currentWishes)
    }

    @Test
    fun `a failed delete from the wish editor stays open with the edits and reports it`() = runTest {
        val lamp = wish(list.id, "Lamp")
        repository.givenWishes(lamp)
        val viewModel = editor(WishEditorTarget.Existing(lamp.id))
        viewModel.onNameChange("Brass lamp")
        repository.writeFailure = IOException("disk full")

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        val state = viewModel.uiState.value
        assertEquals(WishEditorStatus.EDITING, state.status)
        assertTrue(state.deleteFailed)
        assertFalse(state.isBusy)
        assertEquals("Brass lamp", state.name)
        viewModel.onDeleteErrorShown()
        assertFalse(viewModel.uiState.value.deleteFailed)
    }

    @Test
    fun `a wish's detail shows waiting to sync only for its own queued change`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository, photos)
        keepSubscribed(viewModel.uiState)
        assertFalse(viewModel.uiState.value.waitingToSync)

        repository.waitingToSync.value = setOf(list.id)
        assertFalse("the list waiting is not the wish waiting", viewModel.uiState.value.waitingToSync)

        repository.wishesWaitingToSync.value = setOf(lamp.id)
        assertTrue(viewModel.uiState.value.waitingToSync)
    }
}
