package app.giftiary.presentation.sharing

import app.giftiary.domain.model.ReservationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReservationPresentationTest {

    @Test
    fun `a free wish can be reserved, the guest's own can be released`() {
        assertEquals(
            ReservationPresentation(ReservationLabel.AVAILABLE, ReservationAction.RESERVE),
            reservationPresentation(ReservationState.AVAILABLE),
        )
        assertEquals(
            ReservationPresentation(ReservationLabel.RESERVED_BY_YOU, ReservationAction.RELEASE),
            reservationPresentation(ReservationState.RESERVED_BY_ME),
        )
    }

    @Test
    fun `someone else's reservation is only Reserved, with nothing to do`() {
        val other = reservationPresentation(ReservationState.RESERVED_BY_OTHER)

        assertEquals(ReservationLabel.RESERVED, other.label)
        assertNull(other.action)
    }

    @Test
    fun `an unknown state shows and offers nothing`() {
        assertEquals(ReservationPresentation(null, null), reservationPresentation(null))
    }
}
