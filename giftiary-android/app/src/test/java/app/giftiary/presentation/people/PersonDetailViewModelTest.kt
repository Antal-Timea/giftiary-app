package app.giftiary.presentation.people

import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.domain.repository.WishDetails
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.wish
import app.giftiary.testing.wishlist
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PersonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePersonRepository()
    private val occasions = FakeOccasionRepository()
    private val gifts = FakeGiftRepository()
    private val ideas = FakeGiftIdeaRepository()
    private val wishlists = FakeWishlistRepository()
    private val anna = person("Anna", relationship = "Sister")

    @Test
    fun `shows the person with their occasions in calendar order`() = runTest {
        val nameDay = occasion(anna.id, OccasionDetails(OccasionType.NAME_DAY, null, 26, 7, null, true))
        val birthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
        people.givenPeople(anna)
        occasions.givenOccasions(nameDay, birthday)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        assertEquals(PersonDetailUiState.Loaded(anna, listOf(birthday, nameDay), emptyList(), emptyList(), emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `edits to the person and new occasions appear without reloading`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        people.updatePerson(anna.id, "Anna K.", "Sister")
        occasions.knownPersonIds += anna.id
        val added = occasions.addOccasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))

        assertEquals(
            PersonDetailUiState.Loaded(anna.copy(name = "Anna K."), listOf(added), emptyList(), emptyList(), emptyList()),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `shows the gifts the person gave or received`() = runTest {
        val book = giftEntry(giftDetails(person = anna), anna)
        people.givenPeople(anna)
        gifts.givenGifts(book, giftEntry(giftDetails(person = person("Béla")), person("Béla")))
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        assertEquals(PersonDetailUiState.Loaded(anna, emptyList(), emptyList(), listOf(book), emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `shows the active gift ideas for the person`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        val forSomeoneElse = person("Béla").let { bela -> ideaEntry(ideaDetails(bela), bela) }
        people.givenPeople(anna)
        ideas.givenIdeas(scarf, forSomeoneElse)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        assertEquals(PersonDetailUiState.Loaded(anna, emptyList(), listOf(scarf), emptyList(), emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `shows only this person's wishlists, by name, with how many wishes each has`() = runTest {
        val bela = person("Béla")
        people.givenPeople(anna, bela)
        val christmas = wishlist("Christmas", personId = anna.id)
        val birthday = wishlist("Birthday", personId = anna.id)
        val belas = wishlist("Béla's", personId = bela.id)
        val own = wishlist("Mine")
        wishlists.givenWishlists(christmas, belas, own, birthday)
        wishlists.givenWishes(wish(christmas.id, "Lamp"), wish(christmas.id, "Mug"), wish(belas.id, "Socks"), wish(own.id, "Book"))
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        val loaded = viewModel.uiState.value as PersonDetailUiState.Loaded

        assertEquals(listOf(PersonWishlist(birthday, 0), PersonWishlist(christmas, 2)), loaded.wishlists)
    }

    @Test
    fun `new wishes and deleted wishlists show without reloading`() = runTest {
        people.givenPeople(anna)
        val christmas = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        wishlists.addWish(christmas.id, WishDetails("Lamp", null, null, null))
        assertEquals(listOf(PersonWishlist(christmas, 1)), (viewModel.uiState.value as PersonDetailUiState.Loaded).wishlists)

        wishlists.deleteWishlist(christmas.id)
        assertEquals(emptyList<PersonWishlist>(), (viewModel.uiState.value as PersonDetailUiState.Loaded).wishlists)
    }

    @Test
    fun `a missing or deleted person shows not found`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists)
        keepSubscribed(viewModel.uiState)

        people.deletePerson(anna.id)

        assertEquals(PersonDetailUiState.NotFound, viewModel.uiState.value)
    }
}
