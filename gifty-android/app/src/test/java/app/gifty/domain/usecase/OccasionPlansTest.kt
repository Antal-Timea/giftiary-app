package app.gifty.domain.usecase

import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionPlan
import app.gifty.domain.model.Person
import app.gifty.testing.ideaDetails
import app.gifty.testing.ideaEntry
import app.gifty.testing.person
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class OccasionPlansTest {

    private val anna = person("Anna")
    private val bela = person("Béla")
    private val birthday = UUID.randomUUID()
    private val christmas = UUID.randomUUID()

    @Test
    fun `every open idea counts for its occasion, and a bought one also as bought`() {
        val plans = occasionPlans(
            listOf(
                idea(anna, birthday, GiftIdeaStatus.IDEA),
                idea(anna, birthday, GiftIdeaStatus.PLANNED),
                idea(anna, birthday, GiftIdeaStatus.PURCHASED),
            ),
        )

        assertEquals(mapOf(birthday to OccasionPlan(ideas = 3, bought = 1)), plans)
    }

    @Test
    fun `a general occasion gathers everyone's ideas, each occasion separately`() {
        val plans = occasionPlans(
            listOf(
                idea(anna, christmas, GiftIdeaStatus.PURCHASED),
                idea(bela, christmas, GiftIdeaStatus.IDEA),
                idea(anna, birthday, GiftIdeaStatus.IDEA),
            ),
        )

        assertEquals(mapOf(christmas to OccasionPlan(2, 1), birthday to OccasionPlan(1, 0)), plans)
    }

    @Test
    fun `ideas of no occasion count nowhere, and no ideas means no plans`() {
        assertEquals(emptyMap<UUID, OccasionPlan>(), occasionPlans(listOf(idea(anna, null, GiftIdeaStatus.PURCHASED))))
        assertEquals(emptyMap<UUID, OccasionPlan>(), occasionPlans(emptyList()))
    }

    private fun idea(person: Person, occasionId: UUID?, status: GiftIdeaStatus) =
        ideaEntry(ideaDetails(person, status = status).copy(occasionId = occasionId), person).idea
}
