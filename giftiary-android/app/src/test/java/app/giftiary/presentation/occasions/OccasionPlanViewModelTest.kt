package app.giftiary.presentation.occasions

import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.usecase.occasionPlans
import app.giftiary.testing.FakeGiftIdeaRepository
import app.giftiary.testing.FakeOccasionRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.MutableClock
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class OccasionPlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = person("Anna")
    private val bela = person("Béla")
    private val occasions = FakeOccasionRepository().apply { people.value = mapOf(anna.id to anna, bela.id to bela) }
    private val ideas = FakeGiftIdeaRepository().apply {
        people[anna.id] = anna
        people[bela.id] = bela
    }
    private val clock = MutableClock(Instant.parse("2026-05-10T10:00:00Z"))

    private val annasBirthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 5, 1990, true))
    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))

    @Test
    fun `a person's occasion shows its person, next date, days to go and the age turned`() = runTest {
        occasions.givenOccasions(annasBirthday)

        val state = loaded(annasBirthday.id)

        assertEquals(anna, state.person)
        assertEquals(LocalDate.of(2026, 5, 12), state.nextDate)
        assertEquals(2, state.daysUntil)
        assertEquals(36, state.turningAge)
    }

    @Test
    fun `a general occasion has no person, and lists the ideas of everyone linked to it`() = runTest {
        occasions.givenOccasions(christmas, annasBirthday)
        val forAnna = idea(anna, christmas, GiftIdeaStatus.PURCHASED)
        val forBela = idea(bela, christmas, GiftIdeaStatus.IDEA)
        ideas.givenIdeas(forAnna, forBela, idea(anna, annasBirthday, GiftIdeaStatus.IDEA), ideaEntry(ideaDetails(anna), anna))

        val state = loaded(christmas.id)

        assertNull(state.person)
        assertEquals(OccasionIdeas.Loaded(listOf(forAnna, forBela)), state.ideas)
        assertEquals(OccasionPlan(ideas = 2, bought = 1), state.plan)
        assertEquals(occasionPlans(listOf(forAnna.idea, forBela.idea))[christmas.id], state.plan)
    }

    @Test
    fun `ideas follow as they are added, bought and given`() = runTest {
        occasions.givenOccasions(annasBirthday)
        val viewModel = collected(annasBirthday.id)
        assertEquals(OccasionIdeas.Loaded(emptyList()), loaded(viewModel).ideas)
        assertNull(loaded(viewModel).plan)

        val scarf = ideas.addGiftIdea(ideaDetails(anna).copy(occasionId = annasBirthday.id))
        assertEquals(OccasionPlan(1, 0), loaded(viewModel).plan)
        ideas.updateStatus(scarf.id, GiftIdeaStatus.PURCHASED)
        assertEquals(OccasionPlan(1, 1), loaded(viewModel).plan)
        ideas.markAsGiven(scarf.id, LocalDate.of(2026, 5, 12), null)

        assertEquals(OccasionIdeas.Loaded(emptyList()), loaded(viewModel).ideas)
        assertNull(loaded(viewModel).plan)
    }

    @Test
    fun `an occasion that is deleted, unknown or another account's is not found`() = runTest {
        // The repository leaves out occasions whose person is not this account's active one.
        val strangers = occasion(person("Stranger").id)
        occasions.givenOccasions(annasBirthday, strangers)

        assertEquals(OccasionPlanUiState.NotFound, collected(UUID.randomUUID()).uiState.value)
        assertEquals(OccasionPlanUiState.NotFound, collected(strangers.id).uiState.value)

        val viewModel = collected(annasBirthday.id)
        occasions.deleteOccasion(annasBirthday.id)
        assertEquals(OccasionPlanUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `ideas that cannot be read leave the occasion shown`() = runTest {
        occasions.givenOccasions(annasBirthday)
        ideas.readFailure = IOException("disk")

        val state = loaded(annasBirthday.id)

        assertEquals(OccasionIdeas.Unavailable, state.ideas)
        assertNull(state.plan)
        assertEquals(anna, state.person)
    }

    @Test
    fun `an occasion that cannot be read fails to load`() = runTest {
        occasions.readFailure = IOException("disk")

        assertEquals(OccasionPlanUiState.LoadFailed, collected(annasBirthday.id).uiState.value)
    }

    @Test
    fun `a one-time occasion that has passed shows no next date`() = runTest {
        val trip = occasion(null, OccasionDetails(OccasionType.CUSTOM, "Trip", 1, 3, 2026, false))
        occasions.givenOccasions(trip)

        val state = loaded(trip.id)

        assertNull(state.nextDate)
        assertNull(state.daysUntil)
    }

    private fun idea(person: Person, occasion: Occasion, status: GiftIdeaStatus) =
        ideaEntry(ideaDetails(person, status = status).copy(occasionId = occasion.id), person)

    private fun TestScope.collected(occasionId: UUID): OccasionPlanViewModel {
        val viewModel = OccasionPlanViewModel(occasionId, occasions, ideas, clock, ZoneOffset.UTC)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.loaded(occasionId: UUID) = loaded(collected(occasionId))

    private fun loaded(viewModel: OccasionPlanViewModel) = viewModel.uiState.value as OccasionPlanUiState.Loaded
}
