package app.gifty.presentation.gifts

import app.gifty.domain.model.Occasion
import app.gifty.domain.model.Person
import java.util.UUID

/**
 * Selectable people for a gift or idea: the active ones, plus the person the record was loaded
 * with while it stays selected. That person may have been deleted since; once the user picks
 * someone else, they are no longer offered.
 */
fun personChoices(active: List<Person>, original: Person?, selectedId: UUID?): List<Person> =
    active + listOfNotNull(original?.takeIf { it.id == selectedId && active.none { person -> person.id == it.id } })

/** "No occasion", the selected person's active occasions and, while selected, the original one. */
fun occasionChoices(personOccasions: List<Occasion>, original: Occasion?, selectedId: UUID?): List<Occasion?> =
    listOf(null) + personOccasions +
        listOfNotNull(original?.takeIf { it.id == selectedId && personOccasions.none { occasion -> occasion.id == it.id } })
