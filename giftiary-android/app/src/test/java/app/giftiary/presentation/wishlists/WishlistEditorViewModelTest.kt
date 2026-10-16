package app.giftiary.presentation.wishlists

import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.PersonRepository
import app.giftiary.domain.usecase.SaveWishlistUseCase
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.person
import app.giftiary.testing.wishlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

class WishlistEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val wishlists = FakeWishlistRepository()
    private val people = FakePersonRepository()
    private val anna = person("Anna")

    private fun editor(target: WishlistEditorTarget) =
        WishlistEditorViewModel(target, wishlists, people, SaveWishlistUseCase(wishlists))

    @Test
    fun `a new wishlist is the user's own by default and opens once saved`() = runTest {
        people.givenPeople(anna)
        val viewModel = editor(WishlistEditorTarget.New())

        assertNull(viewModel.uiState.value.selectedPerson)
        assertEquals(listOf(null, anna), viewModel.uiState.value.personOptions)
        viewModel.onNameChange(" Birthday ")
        viewModel.save()

        val created = wishlists.currentWishlists.single()
        assertEquals("Birthday", created.name)
        assertNull(created.personId)
        assertEquals(WishlistEditorStatus.SAVED, viewModel.uiState.value.status)
        assertEquals(created.id, viewModel.uiState.value.savedWishlistId)
    }

    @Test
    fun `a chosen person is who the new wishlist is for`() = runTest {
        people.givenPeople(anna)
        wishlists.activePeopleIds = setOf(anna.id)
        val viewModel = editor(WishlistEditorTarget.New())

        viewModel.onPersonChange(anna)
        viewModel.onNameChange("Christmas")
        viewModel.save()

        assertEquals(anna.id, wishlists.currentWishlists.single().personId)
    }

    @Test
    fun `an active person handed in is preselected, an unavailable or malformed one is dropped`() = runTest {
        people.givenPeople(anna)

        assertEquals(anna, editor(WishlistEditorTarget.New(anna.id)).uiState.value.selectedPerson)
        val stranger = editor(WishlistEditorTarget.New(UUID.randomUUID()))
        assertNull(stranger.uiState.value.personId)
        assertEquals(WishlistEditorTarget.New(null), WishlistEditorTarget.newFor("not-a-uuid"))
        assertEquals(WishlistEditorTarget.New(anna.id), WishlistEditorTarget.newFor(anna.id.toString()))
    }

    @Test
    fun `a chosen person who is deleted meanwhile is dropped, not kept`() = runTest {
        people.givenPeople(anna)
        val viewModel = editor(WishlistEditorTarget.New())
        viewModel.onPersonChange(anna)

        people.givenPeople()

        assertNull(viewModel.uiState.value.personId)
    }

    @Test
    fun `a person the repository refuses is reported and nothing is created`() = runTest {
        people.givenPeople(anna)
        val viewModel = editor(WishlistEditorTarget.New(anna.id))
        viewModel.onNameChange("Christmas")

        // Not an active person of the account as far as storage is concerned.
        viewModel.save()

        assertEquals(WishlistWriteError.PERSON_UNAVAILABLE, viewModel.uiState.value.writeError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(wishlists.currentWishlists.isEmpty())
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val viewModel = editor(WishlistEditorTarget.New())
        viewModel.onNameChange("   ")

        viewModel.save()

        assertTrue(viewModel.uiState.value.showNameRequired)
        assertEquals(WishlistEditorStatus.EDITING, viewModel.uiState.value.status)
        assertTrue(wishlists.currentWishlists.isEmpty())
    }

    @Test
    fun `nothing is saved before the people are known`() = runTest {
        // Storage would accept Anna, so only the editor's own wait keeps it from saving.
        wishlists.activePeopleIds = setOf(anna.id)
        val peopleNeverLoad = object : PersonRepository by people {
            override fun observePeople(): Flow<List<Person>> = emptyFlow()
        }
        val viewModel = WishlistEditorViewModel(WishlistEditorTarget.New(anna.id), wishlists, peopleNeverLoad, SaveWishlistUseCase(wishlists))
        viewModel.onNameChange("Christmas")

        viewModel.save()

        assertFalse(viewModel.uiState.value.peopleLoaded)
        assertTrue(wishlists.currentWishlists.isEmpty())
    }

    @Test
    fun `renaming shows who the wishlist is for, cannot change it, and closes once saved`() = runTest {
        people.givenPeople(anna)
        val forAnna = wishlist("Christmas", personId = anna.id).also { wishlists.givenWishlists(it) }
        val viewModel = editor(WishlistEditorTarget.Existing(forAnna.id))

        assertEquals(WishlistOwner.Person("Anna"), viewModel.uiState.value.owner)
        assertEquals("Christmas", viewModel.uiState.value.name)
        viewModel.onPersonChange(null)
        viewModel.onNameChange("Name day")
        viewModel.save()

        assertEquals(forAnna.copy(name = "Name day"), wishlists.currentWishlists.single())
        assertEquals(WishlistEditorStatus.SAVED, viewModel.uiState.value.status)
    }

    @Test
    fun `renaming a wishlist of a deleted person names no one`() = runTest {
        val orphan = wishlist("Old", personId = UUID.randomUUID()).also { wishlists.givenWishlists(it) }

        assertEquals(WishlistOwner.PersonRemoved, editor(WishlistEditorTarget.Existing(orphan.id)).uiState.value.owner)
    }

    @Test
    fun `a wishlist that is gone cannot be renamed`() = runTest {
        val viewModel = editor(WishlistEditorTarget.Existing(UUID.randomUUID()))
        assertEquals(WishlistEditorStatus.NOT_FOUND, viewModel.uiState.value.status)

        val list = wishlist("Birthday").also { wishlists.givenWishlists(it) }
        val renaming = editor(WishlistEditorTarget.Existing(list.id))
        wishlists.givenWishlists()
        renaming.onNameChange("Other")
        renaming.save()
        assertEquals(WishlistEditorStatus.NOT_FOUND, renaming.uiState.value.status)
    }

    @Test
    fun `a failed write is reported and the form stays`() = runTest {
        wishlists.writeFailure = IOException("disk")
        val viewModel = editor(WishlistEditorTarget.New())
        viewModel.onNameChange("Birthday")

        viewModel.save()

        assertEquals(WishlistWriteError.FAILED, viewModel.uiState.value.writeError)
        assertEquals(WishlistEditorStatus.EDITING, viewModel.uiState.value.status)
        viewModel.onWriteErrorShown()
        assertNull(viewModel.uiState.value.writeError)
    }
}
