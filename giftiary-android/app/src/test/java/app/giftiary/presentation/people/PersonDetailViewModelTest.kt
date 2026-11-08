package app.giftiary.presentation.people

import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeGiftPhotoRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PersonDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val people = FakePersonRepository()
    private val occasions = FakeOccasionRepository()
    private val gifts = FakeGiftRepository()
    private val ideas = FakeGiftIdeaRepository()
    private val wishlists = FakeWishlistRepository()
    private val photos = FakeGiftPhotoRepository()
    private val anna = person("Anna", relationship = "Sister")

    @Test
    fun `shows the person with their occasions in calendar order`() = runTest {
        val nameDay = occasion(anna.id, OccasionDetails(OccasionType.NAME_DAY, null, 26, 7, null, true))
        val birthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
        people.givenPeople(anna)
        occasions.givenOccasions(nameDay, birthday)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(PersonDetailUiState.Loaded(anna, listOf(birthday, nameDay), emptyList(), emptyList(), emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `edits to the person and new occasions appear without reloading`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
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
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        assertEquals(PersonDetailUiState.Loaded(anna, emptyList(), emptyList(), listOf(book), emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `shows the active gift ideas for the person`() = runTest {
        val scarf = ideaEntry(ideaDetails(anna), anna)
        val forSomeoneElse = person("Béla").let { bela -> ideaEntry(ideaDetails(bela), bela) }
        people.givenPeople(anna)
        ideas.givenIdeas(scarf, forSomeoneElse)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
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
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        val loaded = viewModel.uiState.value as PersonDetailUiState.Loaded

        assertEquals(listOf(PersonWishlist(birthday, 0), PersonWishlist(christmas, 2)), loaded.wishlists)
    }

    @Test
    fun `new wishes and deleted wishlists show without reloading`() = runTest {
        people.givenPeople(anna)
        val christmas = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        wishlists.addWish(christmas.id, WishDetails("Lamp", null, null, null))
        assertEquals(listOf(PersonWishlist(christmas, 1)), (viewModel.uiState.value as PersonDetailUiState.Loaded).wishlists)

        wishlists.deleteWishlist(christmas.id)
        assertEquals(emptyList<PersonWishlist>(), (viewModel.uiState.value as PersonDetailUiState.Loaded).wishlists)
    }

    @Test
    fun `a missing or deleted person shows not found`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        people.deletePerson(anna.id)

        assertEquals(PersonDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `deleting the person waits for confirmation, then reports it done`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.uiState)

        viewModel.onDeleteRequested()
        assertEquals(PersonDeletion(confirming = true), viewModel.deletion.value)
        assertEquals(listOf(anna), people.currentPeople)

        viewModel.onDeleteConfirmed()
        assertEquals(PersonDeletion(deleted = true), viewModel.deletion.value)
        assertEquals(emptyList<Any>(), people.currentPeople)
    }

    @Test
    fun `dismissing the confirmation keeps the person`() = runTest {
        people.givenPeople(anna)
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)

        viewModel.onDeleteRequested()
        viewModel.onDeleteDismissed()

        assertEquals(PersonDeletion(), viewModel.deletion.value)
        assertEquals(listOf(anna), people.currentPeople)
    }

    @Test
    fun `a failed deletion is reported once and keeps the person`() = runTest {
        people.givenPeople(anna)
        people.writeFailure = IOException("disk full")
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        assertEquals(PersonDeletion(failed = true), viewModel.deletion.value)
        assertEquals(listOf(anna), people.currentPeople)

        viewModel.onDeleteErrorShown()
        assertEquals(PersonDeletion(), viewModel.deletion.value)
    }

    @Test
    fun `the photos of the person's gifts are offered by path, those that cannot be read are left out`() = runTest {
        photos.stored["a/photo.jpg"] = byteArrayOf(1, 2)
        val withPhoto = giftEntry(giftDetails(person = anna, name = "Lamp").copy(photoPath = "a/photo.jpg"), anna)
        val missingPhoto = giftEntry(giftDetails(person = anna, name = "Scarf").copy(photoPath = "a/gone.jpg"), anna)
        people.givenPeople(anna)
        gifts.givenGifts(withPhoto, missingPhoto, giftEntry(giftDetails(person = anna, name = "Book"), anna))
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.giftPhotos)

        assertEquals(setOf("a/photo.jpg"), viewModel.giftPhotos.value.keys)
        assertTrue(byteArrayOf(1, 2).contentEquals(viewModel.giftPhotos.value.getValue("a/photo.jpg").bytes))
    }

    @Test
    fun `the hero gets the person's photo, downloaded if need be, and none without one`() = runTest {
        val path = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/30000000-0000-4000-8000-000000000001.jpg"
        photos.stored[path] = byteArrayOf(7)
        photos.remoteOnly += path
        people.givenPeople(anna.copy(photoPath = path))
        val viewModel = PersonDetailViewModel(anna.id, people, occasions, gifts, ideas, wishlists, photos)
        keepSubscribed(viewModel.personPhoto)

        assertEquals(listOf<Byte>(7), viewModel.personPhoto.value?.bytes?.toList())

        people.givenPeople(anna)
        assertNull(viewModel.personPhoto.value)
    }
}
