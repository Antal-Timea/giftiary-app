package app.giftiary.presentation.wishlists

import app.giftiary.domain.repository.SharingFailure
import app.giftiary.domain.repository.WishDetails
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.FakeWishlistSharingRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import app.giftiary.testing.wish
import app.giftiary.testing.wishlist
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class WishlistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val wishlists = FakeWishlistRepository()
    private val people = FakePersonRepository()
    private val sharing = FakeWishlistSharingRepository()
    private val anna = person("Anna")

    @Test
    fun `the user's own wishlist shows its wishes and opens no person`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val lamp = wish(own.id, "Lamp")
        val mug = wish(own.id, "Mug")
        wishlists.givenWishes(lamp, wish(UUID.randomUUID(), "Elsewhere"), mug)

        val content = loaded(viewModel(own.id))

        assertEquals(own, content.wishlist)
        assertEquals(WishlistOwner.Me, content.owner)
        assertNull(content.openablePersonId)
        assertEquals(listOf(lamp, mug), content.wishes)
    }

    @Test
    fun `a wishlist for an active person can open that person, one for a deleted person cannot`() = runTest {
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id)
        val orphan = wishlist("Old", personId = UUID.randomUUID())
        wishlists.givenWishlists(forAnna, orphan)

        val annas = loaded(viewModel(forAnna.id))
        val orphans = loaded(viewModel(orphan.id))

        assertEquals(WishlistOwner.Person("Anna"), annas.owner)
        assertEquals(anna.id, annas.openablePersonId)
        assertEquals(WishlistOwner.PersonRemoved, orphans.owner)
        assertNull(orphans.openablePersonId)
    }

    @Test
    fun `renames, new wishes and the person's deletion show at once`() = runTest {
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val viewModel = viewModel(forAnna.id)
        keepSubscribed(viewModel.uiState)

        wishlists.renameWishlist(forAnna.id, "Name day")
        wishlists.addWish(forAnna.id, WishDetails("Lamp", null, null, null, null))
        people.givenPeople()

        val content = viewModel.uiState.value.content as WishlistDetailContent.Loaded
        assertEquals("Name day", content.wishlist.name)
        assertEquals(listOf("Lamp"), content.wishes.map { it.name })
        assertEquals(WishlistOwner.PersonRemoved, content.owner)
        assertNull(content.openablePersonId)
    }

    @Test
    fun `a wishlist deleted elsewhere shows not found`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val viewModel = viewModel(own.id)
        keepSubscribed(viewModel.uiState)

        wishlists.givenWishlists()

        assertEquals(WishlistDetailContent.NotFound, viewModel.uiState.value.content)
    }

    @Test
    fun `deleting asks first, and then deletes the wishlist with its wishes`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        wishlists.givenWishes(wish(own.id, "Lamp"), wish(own.id, "Mug"))
        val viewModel = viewModel(own.id)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.actions.confirmingDelete)
        viewModel.onDeleteDismissed()
        assertFalse(viewModel.uiState.value.actions.confirmingDelete)
        assertEquals(1, wishlists.currentWishlists.size)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertEquals(WishlistDetailContent.Deleted, viewModel.uiState.value.content)
        assertTrue(wishlists.currentWishlists.isEmpty())
        assertTrue(wishlists.currentWishes.isEmpty())
    }

    @Test
    fun `a failed deletion is reported and the wishlist stays`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        wishlists.writeFailure = IOException("disk")
        val viewModel = viewModel(own.id)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.uiState.value.actions.writeFailed)
        assertFalse(viewModel.uiState.value.actions.isBusy)
        assertTrue(viewModel.uiState.value.content is WishlistDetailContent.Loaded)
    }

    @Test
    fun `a failing read shows the load error`() = runTest {
        wishlists.flowFailure = IOException("disk")
        val viewModel = viewModel(UUID.randomUUID())
        keepSubscribed(viewModel.uiState)

        assertEquals(WishlistDetailContent.LoadFailed, viewModel.uiState.value.content)
    }

    @Test
    fun `a change of the list waiting to sync is reported, beside the list`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val viewModel = viewModel(own.id)
        keepSubscribed(viewModel.uiState)
        assertFalse(viewModel.uiState.value.waitingToSync)

        wishlists.waitingToSync.value = setOf(own.id)

        assertTrue(viewModel.uiState.value.waitingToSync)
        assertEquals(own, (viewModel.uiState.value.content as WishlistDetailContent.Loaded).wishlist)
    }

    @Test
    fun `the owner of a list kept for a person sees which wishes are reserved, never by whom`() = runTest {
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val kite = wish(forAnna.id, "Kite")
        val ball = wish(forAnna.id, "Ball")
        wishlists.givenWishes(kite, ball)
        // An id the device no longer has, e.g. a wish deleted here and not yet synced, is ignored.
        sharing.reservedWishes = setOf(kite.id, UUID.randomUUID())
        val viewModel = viewModel(forAnna.id)
        keepSubscribed(viewModel.uiState)
        assertEquals(emptySet<UUID>(), loaded(viewModel).reservedWishIds)

        viewModel.onShown()

        assertEquals(setOf(kite.id), loaded(viewModel).reservedWishIds)
        assertEquals(listOf("reservedWishes:${forAnna.id}"), sharing.calls)
    }

    @Test
    fun `the owner of their own list is never told, and the server is not even asked`() = runTest {
        val own = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val lamp = wish(own.id, "Lamp").also { wishlists.givenWishes(it) }
        sharing.reservedWishes = setOf(lamp.id)
        val viewModel = viewModel(own.id)
        keepSubscribed(viewModel.uiState)

        viewModel.onShown()

        assertEquals(emptySet<UUID>(), loaded(viewModel).reservedWishIds)
        assertTrue(sharing.calls.isEmpty())
    }

    @Test
    fun `offline the last known reservations stay, and showing again refreshes them`() = runTest {
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val kite = wish(forAnna.id, "Kite")
        val ball = wish(forAnna.id, "Ball")
        wishlists.givenWishes(kite, ball)
        sharing.reservedWishes = setOf(kite.id)
        val viewModel = viewModel(forAnna.id)
        keepSubscribed(viewModel.uiState)
        viewModel.onShown()

        sharing.reservedWishes = setOf(ball.id)
        sharing.failNext += SharingFailure.NETWORK
        viewModel.onShown()
        assertEquals(setOf(kite.id), loaded(viewModel).reservedWishIds)

        viewModel.onShown()
        assertEquals(setOf(ball.id), loaded(viewModel).reservedWishIds)
    }

    private val photos = FakeGiftPhotoRepository()

    private fun viewModel(id: UUID) = WishlistDetailViewModel(id, wishlists, people, sharing, photos)

    private fun TestScope.loaded(viewModel: WishlistDetailViewModel): WishlistDetailContent.Loaded {
        keepSubscribed(viewModel.uiState)
        return viewModel.uiState.value.content as WishlistDetailContent.Loaded
    }

    @Test
    fun `the owner's wish photos are read as their own, only for the rows shown`() = runTest {
        val path = photos.importPhoto("content://lamp")
        val list = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val viewModel = viewModel(list.id)

        viewModel.photos.show(path)

        assertEquals(listOf(path), photos.loads)
        assertTrue("never through the member's loader", photos.sharedLoads.isEmpty())
        assertEquals("content://lamp", viewModel.photos[path]!!.bytes.decodeToString())
    }
}
