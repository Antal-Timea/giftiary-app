package app.giftiary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.giftiary.presentation.theme.GiftiaryLayout
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * A list of [GiftiaryLayout.listPaneWidth] with the selected item beside it, for expanded windows.
 * Only the arrangement: what is selected, Back and narrowing stay with each screen.
 */
@Composable
fun ListDetailPanes(
    list: @Composable (Modifier) -> Unit,
    detail: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        list(
            Modifier
                .width(GiftiaryLayout.listPaneWidth)
                .fillMaxHeight(),
        )
        VerticalDivider(color = GiftiaryTheme.colors.divider)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(GiftiaryTheme.colors.background),
            content = detail,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun ListDetailPanesPreview() {
    GiftiaryTheme {
        ListDetailPanes(
            list = { modifier -> Box(modifier) { Text("List") } },
            detail = { Text("Detail") },
        )
    }
}
