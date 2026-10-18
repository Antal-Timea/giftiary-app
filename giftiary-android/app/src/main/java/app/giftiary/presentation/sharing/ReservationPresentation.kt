package app.giftiary.presentation.sharing

import app.giftiary.domain.model.ReservationState

enum class ReservationLabel { AVAILABLE, RESERVED_BY_YOU, RESERVED }

enum class ReservationAction { RESERVE, RELEASE }

/** What a shared wish row says about its reservation and what it lets the guest do. */
data class ReservationPresentation(val label: ReservationLabel?, val action: ReservationAction?)

/**
 * Someone else's reservation is only "Reserved": no action and nothing about who. A state this
 * version does not know shows nothing and offers nothing.
 */
fun reservationPresentation(state: ReservationState?): ReservationPresentation = when (state) {
    ReservationState.AVAILABLE -> ReservationPresentation(ReservationLabel.AVAILABLE, ReservationAction.RESERVE)
    ReservationState.RESERVED_BY_ME -> ReservationPresentation(ReservationLabel.RESERVED_BY_YOU, ReservationAction.RELEASE)
    ReservationState.RESERVED_BY_OTHER -> ReservationPresentation(ReservationLabel.RESERVED, null)
    null -> ReservationPresentation(null, null)
}
