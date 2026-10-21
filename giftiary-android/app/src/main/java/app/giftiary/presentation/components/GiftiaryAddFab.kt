package app.giftiary.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** The floating button that adds an item to the list it floats over. */
@Composable
fun GiftiaryAddFab(text: String, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    // The content overload keeps the label in the semantics tree; the animated text/icon
    // overload left the button unlabeled for accessibility services.
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.primaryAction,
        contentColor = colors.onPrimaryAction,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(GiftiarySpacing.m))
        Text(text)
    }
}

/** Bottom padding that keeps a list's last row reachable above a [GiftiaryAddFab]. */
val GiftiaryAddFabClearance = 96.dp

@PreviewLightDark
@Composable
private fun GiftiaryAddFabPreview() {
    GiftiaryTheme {
        GiftiaryAddFab(text = stringResource(R.string.people_add), onClick = {})
    }
}
