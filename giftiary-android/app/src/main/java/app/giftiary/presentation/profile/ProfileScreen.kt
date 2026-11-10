package app.giftiary.presentation.profile

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.EmailAddress
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryBackButton
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.people.PersonAvatar
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    ProfileContent(state, ProfileActions(onBack, onEditProfile, onOpenSettings, onSignOut))
}

class ProfileActions(
    val onBack: () -> Unit,
    val onEditProfile: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSignOut: () -> Unit,
)

/**
 * The approved Profile board as far as the account has the data: the avatar or else the monogram,
 * the name others see or else the email, Edit profile for that name, what this device
 * keeps, then Settings and Sign out.
 */
@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    DecoratedPage(top = GiftCutout.SMALL, modifier = modifier.background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.browseGutter),
        ) {
            GiftiaryBackButton(onClick = actions.onBack)
            Identity(state, Modifier.padding(top = GiftiarySpacing.xl))
            GiftiarySecondaryButton(
                text = stringResource(R.string.profile_edit),
                onClick = actions.onEditProfile,
                modifier = Modifier.padding(top = GiftiarySpacing.xl),
                leadingIcon = painterResource(R.drawable.ic_edit),
                leadingIconTint = colors.textPrimary,
            )
            state.stats?.let { Story(it, Modifier.padding(top = GiftiarySpacing.xxl)) }
            Column(Modifier.padding(top = GiftiarySpacing.l)) {
                ProfileRow(R.drawable.ic_nav_settings, stringResource(R.string.settings_title), actions.onOpenSettings, chevron = true)
                GiftiaryDivider(startIndent = ROW_TEXT_INDENT)
                ProfileRow(R.drawable.ic_sign_out, stringResource(R.string.auth_sign_out), actions.onSignOut, chevron = false)
            }
        }
    }
}

/** The avatar is decorative: the name or email is written right beside it. */
@Composable
private fun Identity(state: ProfileUiState, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same initial as on Home.
        PersonAvatar(name = ownMonogramName(state.displayName, state.email).orEmpty(), photo = state.avatar, size = 88.dp)
        // Short of the corner gift, which reaches down beside this row on a phone.
        Column(Modifier.weight(1f).padding(end = GIFT_CLEARANCE)) {
            if (state.displayName != null) {
                Text(
                    text = state.displayName,
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.screenTitle,
                    color = colors.textPrimary,
                )
                if (state.email != null) {
                    EmailAddress(
                        email = state.email,
                        modifier = Modifier.padding(top = GiftiarySpacing.xs),
                        style = GiftiaryType.body,
                        color = colors.textSecondary,
                    )
                }
            } else if (state.email != null) {
                // Standing in for a name, an address is set smaller, as a row's title, so that at large
                // text on a 390dp phone its domain still fits a line of its own beside the avatar.
                EmailAddress(
                    email = state.email,
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.contentTitle,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun Story(stats: ProfileStats, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier) {
        StoryHeading(stringResource(R.string.profile_story).uppercase())
        Row(
            modifier = Modifier
                .padding(top = GiftiarySpacing.m)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.surfaceContext, GiftiaryShapes.container)
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        ) {
            Stat(stats.people, R.plurals.profile_stat_people)
            StatDivider()
            Stat(stats.gifts, R.plurals.profile_stat_gifts)
            StatDivider()
            Stat(stats.wishlists, R.plurals.profile_stat_lists)
        }
        Text(
            text = stringResource(R.string.profile_story_caption),
            modifier = Modifier.padding(top = GiftiarySpacing.l),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
    }
}

/**
 * The story overline, broken only between words: "GIFTIARY-TÖRTÉNETED" is one word, not split at
 * its hyphen nor hyphenated. When even the longest word is wider than the page (large text on a
 * phone), the overline is set smaller rather than split.
 */
@Composable
private fun StoryHeading(text: String) {
    // The word joiner after a hyphen removes the break the line breaker would otherwise allow there.
    val joined = text.replace("-", "-\u2060")
    val base = GiftiaryType.wordmark.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val longestWord = joined.split(' ').maxOf { measurer.measure(it, base, softWrap = false).size.width }
        // A little short of the full width: a word measured as just fitting can still be wrapped.
        val available = constraints.maxWidth * 0.95f
        // Letter spacing is in em, so it shrinks along with the size.
        val style = if (longestWord <= available) base else base.copy(fontSize = base.fontSize * (available / longestWord))
        Text(
            text = joined,
            modifier = Modifier.semantics { heading() },
            style = style,
            color = GiftiaryTheme.colors.textLabel,
        )
    }
}

@Composable
private fun RowScope.Stat(count: Int, @PluralsRes label: Int) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = count.toString(), style = GiftiaryType.screenTitle, color = colors.textPrimary)
        Text(text = pluralStringResource(label, count), style = GiftiaryType.label, color = colors.textPrimary)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = GiftiarySpacing.m)
            .width(1.dp)
            .fillMaxHeight()
            .background(GiftiaryTheme.colors.borderStrong),
    )
}

@Composable
private fun ProfileRow(@DrawableRes icon: Int, title: String, onClick: () -> Unit, chevron: Boolean) {
    GiftiaryRow(
        title = title,
        leading = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = GiftiaryTheme.colors.textPrimary,
            )
        },
        trailing = if (chevron) {
            { RowChevron() }
        } else {
            null
        },
        titleStyle = GiftiaryType.actionLarge,
        contentPadding = PaddingValues(vertical = GiftiarySpacing.l),
        onClick = onClick,
    )
}

// The corner gift's reach past the page gutter beside the identity row (measured in the test).
internal val GIFT_CLEARANCE = 48.dp

// Past the 22dp icon and the row's 14dp gap, so the hairline starts under the titles, as on the board.
private val ROW_TEXT_INDENT = 36.dp

@Preview
@Composable
private fun ProfilePreview() {
    GiftiaryTheme {
        ProfileContent(
            state = ProfileUiState(email = "anna@example.com", displayName = "Anna", stats = ProfileStats(4, 12, 2)),
            actions = ProfileActions({}, {}, {}, {}),
        )
    }
}
