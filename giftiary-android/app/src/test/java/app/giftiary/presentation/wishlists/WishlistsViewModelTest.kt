package app.giftiary.presentation.wishlists

import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import app.giftiary.testing.wishlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
        keepSubscribed(viewModel.content)

        people.givenPeople(anna.copy(name = "Anna K."))
        assertEquals("Anna K.", (viewModel.content.value as WishlistsContent.Loaded).forPeople.single().personName)

        people.givenPeople()
        assertEquals(null, (viewModel.content.value as WishlistsContent.Loaded).forPeople.single().personName)
    }

    @Test
    fun `a failing read shows the load error`() = runTest {
        wishlists.flowFailure = IOException("disk")
        val viewModel = WishlistsViewModel(wishlists, people)
        keepSubscribed(viewModel.content)

        assertEquals(WishlistsContent.LoadFailed, viewModel.content.value)
    }

    private suspend fun TestScope.loaded(viewModel: WishlistsViewModel): WishlistsContent.Loaded {
        keepSubscribed(viewModel.content)
        return viewModel.content.first { it is WishlistsContent.Loaded } as WishlistsContent.Loaded
    }
}
