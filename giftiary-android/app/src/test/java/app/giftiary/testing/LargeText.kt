package app.giftiary.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue

/** [content] with the system text size set to [fontScale], as Settings › Font size does. */
@Composable
fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)
}

/** How many lines the node's text was laid out on; a word squeezed to a letter per line shows here. */
fun SemanticsNodeInteraction.lineCount(): Int {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single().lineCount
}

/**
 * Fails for any text under this node that is cut off: given less height than its lines need, as in
 * a fixed-height hero, or laid out whole but clipped by an ancestor.
 */
fun SemanticsNodeInteraction.assertNoClippedText() {
    fun check(node: SemanticsNode) {
        val text = node.config.getOrNull(SemanticsProperties.Text)
        if (text != null) {
            val shown = node.boundsInRoot.height
            assertTrue("'${text.joinToString()}' is clipped: $shown of ${node.size.height}px shown", shown >= node.size.height - 1)
            val layout = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
            assertTrue("'${text.joinToString()}' has less height than its lines need", layout.none { it.didOverflowHeight })
        }
        node.children.forEach(::check)
    }
    check(fetchSemanticsNode())
}
