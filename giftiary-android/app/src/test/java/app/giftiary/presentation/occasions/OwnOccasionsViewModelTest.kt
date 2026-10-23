package app.giftiary.presentation.occasions

import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class OwnOccasionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeOccasionRepository()
    private val anna = person("Anna")
    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
    private val anniversary = occasion(null, OccasionDetails(OccasionType.ANNIVERSARY, null, 18, 6, 2015, true))
    private val newYear = occasion(null, OccasionDetails(OccasionType.CUSTOM, "New Year", 1, 1, null, true))

    init {
        repository.people.value = mapOf(anna.id to anna)
    }

    @Test
    fun `only the user's own occasions are listed, in calendar order`() = runTest {
        repository.givenOccasions(christmas, occasion(anna.id), anniversary, newYear)

        assertEquals(OwnOccasionsContent.Loaded(listOf(newYear, anniversary, christmas)), collected().content.value)
    }

    @Test
    fun `nothing is shown before the occasions are read`() {
        assertEquals(OwnOccasionsContent.Loading, OwnOccasionsViewModel(repository).content.value)
    }

    @Test
    fun `adding, editing and deleting show at once`() = runTest {
        val viewModel = collected()
        assertEquals(OwnOccasionsContent.Loaded(emptyList()), viewModel.content.value)

        val added = repository.addOccasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 25, 12, null, true))
        assertEquals(listOf(added), loaded(viewModel))

        repository.updateOccasion(added.id, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
        assertEquals(listOf(24), loaded(viewModel).map { it.day })

        repository.deleteOccasion(added.id)
        assertEquals(emptyList<Any>(), loaded(viewModel))
    }

    @Test
    fun `occasions that cannot be read show as unavailable`() = runTest {
        repository.readFailure = IOException("disk")

        assertEquals(OwnOccasionsContent.LoadFailed, collected().content.value)
    }

    @Test
    fun `occasions of another account's people are never among them`() = runTest {
        // The repository leaves out occasions whose person is not one of this account's: here, an unknown one.
        val strangers = occasion(person("Stranger").id)
        repository.givenOccasions(christmas, strangers)

        assertEquals(listOf(christmas), loaded(collected()))
    }

    private fun TestScope.collected(): OwnOccasionsViewModel {
        val viewModel = OwnOccasionsViewModel(repository)
        keepSubscribed(viewModel.content)
        return viewModel
    }

    private fun loaded(viewModel: OwnOccasionsViewModel) = (viewModel.content.value as OwnOccasionsContent.Loaded).occasions
}
