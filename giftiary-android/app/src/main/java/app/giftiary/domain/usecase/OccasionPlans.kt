package app.giftiary.domain.usecase

import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.OccasionPlan
import java.util.UUID

/**
 * The plan of every occasion that has open ideas, by occasion id. [ideas] are expected to be the
 * active ones only, neither deleted nor given; ideas of no occasion count nowhere. A general
 * occasion such as Christmas gathers the ideas of everyone it is linked to.
 */
fun occasionPlans(ideas: List<GiftIdea>): Map<UUID, OccasionPlan> =
    ideas.mapNotNull { idea -> idea.occasionId?.let { it to idea } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, linked) -> OccasionPlan(ideas = linked.size, bought = linked.count { it.status == GiftIdeaStatus.PURCHASED }) }
