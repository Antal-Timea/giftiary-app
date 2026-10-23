package app.giftiary.testing

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertTrue

/** Asserts that a measured position or size is [expected], give or take a pixel of rounding. */
fun assertNear(expected: Dp, actual: Dp, what: String) =
    assertTrue("$what: expected about $expected, was $actual", (actual - expected).value in -1f..1f)

/** Where content capped at [cap] begins when centred in a window [window] wide, before its own margin. */
fun centredStart(window: Dp, cap: Dp): Dp = (window - cap) / 2
