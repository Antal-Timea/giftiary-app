package app.gifty.presentation.wishlists

import app.gifty.testing.FakePersonRepository
import app.gifty.testing.FakeWishlistRepository
import app.gifty.testing.MainDispatcherRule
import app.gifty.testing.person
import app.gifty.testing.wishlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class WishlistsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val wishlists = FakeWishlistRepository()
    private val people = FakePersonRepository()

    @Test
    fun `own wishlists come first and those for people second, each by name`() = runTest {
        val anna = person("Anna")
        val zsofi = person("Zsófi")
        people.givenPeople(anna, zsofi)
        val own = wishlist("Birthday")
        val ownLater = wishlist("Wedding")
        val forZsofi = wishlist("A Zsófi list", personId = zsofi.id)
        val forAnna = wishlist("Christmas", personId = anna.id)
        wishlists.givenWishlists(ownLater, forAnna, own, forZsofi)

        val content = loaded(WishlistsViewModel(wishlists, people))

        assertEquals(listOf(own, ownLater), content.own)
        assertEquals(listOf(WishlistOfPerson(forZsofi, "Zsófi"), WishlistOfPerson(forAnna, "Anna")), content.forPeople)
    }

    @Test
    fun `a wishlist whose person was deleted stays, without a name`() = runTest {
        val orphan = wishlist("Old list", personId = UUID.randomUUID())
        wishlists.givenWishlists(orphan)

        val content = loaded(WishlistsViewModel(wishlists, people))

        assertEquals(listOf(WishlistOfPerson(orphan, null)), content.forPeople)
        assertTrue(content.own.isEmpty())
    }

    @Test
    fun `no wishlists is the empty state`() = runTest {
        assertTrue(loaded(WishlistsViewModel(wishlists, people)).isEmpty)
    }

    @Test
    fun `the list follows changes, including a person being renamed or deleted`() = runTest {
        val anna = person("Anna")
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id)
        wishlists.givenWishlists(forAnna)
        val viewModel = WishlistsViewModel(wishlists, people)
        collect(viewModel)

        people.givenPeople(anna.copy(name = "Anna K."))
        assertEquals("Anna K.", (viewModel.content.value as WishlistsContent.Loaded).forPeople.single().personName)

        people.givenPeople()
        assertEquals(null, (viewModel.content.value as WishlistsContent.Loaded).forPeople.single().personName)
    }

    @Test
    fun `a failing read shows the load error`() = runTest {
        wishlists.flowFailure = IOException("disk")
        val viewModel = WishlistsViewModel(wishlists, people)
        collect(viewModel)

        assertEquals(WishlistsContent.LoadFailed, viewModel.content.value)
    }

    private suspend fun TestScope.loaded(viewModel: WishlistsViewModel): WishlistsContent.Loaded {
        collect(viewModel)
        return viewModel.content.first { it is WishlistsContent.Loaded } as WishlistsContent.Loaded
    }

    private fun TestScope.collect(viewModel: WishlistsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect {} }
    }
}
