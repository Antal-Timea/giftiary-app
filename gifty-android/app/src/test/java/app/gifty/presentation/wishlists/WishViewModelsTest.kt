package app.gifty.presentation.wishlists

import app.gifty.domain.model.Money
import app.gifty.domain.repository.WishDetails
import app.gifty.domain.usecase.SaveWishUseCase
import app.gifty.presentation.gifts.PriceError
import app.gifty.presentation.gifts.PriceFormat
import app.gifty.testing.FakeWishlistRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.wish
import app.gifty.testing.wishlist
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    private fun editor(target: WishEditorTarget) = WishEditorViewModel(target, repository, SaveWishUseCase(repository))

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

    // ---- detail ----

    @Test
    fun `a wish shows with its wishlist's name and follows edits`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository)
        collect(viewModel)

        assertEquals(WishDetailContent.Loaded(lamp, "Birthday"), viewModel.uiState.value.content)

        repository.updateWish(lamp.id, WishDetails("Brass lamp", null, null, null))
        repository.renameWishlist(list.id, "Name day")

        val content = viewModel.uiState.value.content as WishDetailContent.Loaded
        assertEquals("Brass lamp", content.wish.name)
        assertEquals("Name day", content.wishlistName)
    }

    @Test
    fun `a wish deleted elsewhere, or with its wishlist, shows not found`() = runTest {
        val lamp = wish(list.id, "Lamp").also { repository.givenWishes(it) }
        val viewModel = WishDetailViewModel(lamp.id, repository)
        collect(viewModel)

        repository.deleteWishlist(list.id)

        assertEquals(WishDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun `deleting a wish asks first, then deletes only that wish`() = runTest {
        val lamp = wish(list.id, "Lamp")
        val mug = wish(list.id, "Mug")
        repository.givenWishes(lamp, mug)
        val viewModel = WishDetailViewModel(lamp.id, repository)
        collect(viewModel)

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
        val viewModel = WishDetailViewModel(lamp.id, repository)
        collect(viewModel)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.uiState.value.actions.writeFailed)
        assertTrue(viewModel.uiState.value.content is WishDetailContent.Loaded)
    }

    private fun TestScope.collect(viewModel: WishDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }
}
