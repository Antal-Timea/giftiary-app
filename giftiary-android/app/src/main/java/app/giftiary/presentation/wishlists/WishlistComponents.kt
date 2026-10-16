package app.giftiary.presentation.wishlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** Who a wishlist is for, as shown to the user. */
sealed interface WishlistOwner {
    data object Me : WishlistOwner
    data class Person(val name: String) : WishlistOwner

    /** The person was deleted; the wishlist stays, but there is no one to open. */
    data object PersonRemoved : WishlistOwner
}

@Composable
internal fun wishlistOwnerText(owner: WishlistOwner): String = when (owner) {
    WishlistOwner.Me -> stringResource(R.string.wishlist_for_me)
    is WishlistOwner.Person -> stringResource(R.string.wishlist_for_person, owner.name)
    WishlistOwner.PersonRemoved -> stringResource(R.string.wishlist_person_removed)
}

/** A row of a list: a title, and an optional second line; the whole row opens the item. */
@Composable
internal fun ListRow(title: String, detail: String?, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.people_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.l, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.s)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
internal fun AddButton(text: String, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    // The content overload keeps the label in the semantics tree, as on the People screen.
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

/** Keeps the last row clear of the floating add button. */
internal val FAB_CLEARANCE = 96.dp
