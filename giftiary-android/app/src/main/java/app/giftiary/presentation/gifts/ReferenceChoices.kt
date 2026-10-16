package app.giftiary.presentation.gifts

import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.OccasionEntry
import java.util.UUID

/**
 * Selectable people for a gift or idea: the active ones, plus the person the record was loaded
 * with while it stays selected. That person may have been deleted since; once the user picks
 * someone else, they are no longer offered.
 */
fun personChoices(active: List<Person>, original: Person?, selectedId: UUID?): List<Person> =
    active + listOfNotNull(original?.takeIf { it.id == selectedId && active.none { person -> person.id == it.id } })

/**
 * "No occasion", the selected person's active occasions, then the user's own general ones, which
 * fit anyone, and, while selected, the original one if it is offered no longer.
 */
fun occasionChoices(
    personOccasions: List<Occasion>,
    generalOccasions: List<Occasion>,
    original: Occasion?,
    selectedId: UUID?,
): List<Occasion?> {
    val offered = (personOccasions + generalOccasions).distinctBy { it.id }
    return listOf(null) + offered + listOfNotNull(original?.takeIf { it.id == selectedId && offered.none { occasion -> occasion.id == it.id } })
}

/**
 * Whether the chosen occasion still fits once [personId] is the record's person: a general one
 * fits anyone, a person's only that person.
 */
fun Occasion.fitsPerson(personId: UUID): Boolean = this.personId == null || this.personId == personId

/** The user's own occasions, of no person, in calendar order. */
fun generalOccasions(entries: List<OccasionEntry>): List<Occasion> =
    entries.filter { it.person == null }
        .map { it.occasion }
        .sortedWith(compareBy<Occasion>({ it.month }, { it.day }, { it.id }))
