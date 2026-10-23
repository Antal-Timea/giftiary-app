package app.giftiary.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Keeps [flow] collected until the test ends, so a state shared `WhileSubscribed` keeps its
 * upstream running and its `value` follows every change at once. Cancelled with the test's
 * background scope.
 */
fun TestScope.keepSubscribed(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}
