package app.giftiary.presentation.profile

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryBackButton
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.giftiaryReadableWidth
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
 * The approved Profile board as far as the account has the data: the monogram (there is no profile
 * photo), the name others see or else the email, Edit profile for that name, what this device
 * keeps, then Settings and Sign out.
 */
@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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

/** The monogram is decorative: the name or email is written right beside it. */
@Composable
private fun Identity(state: ProfileUiState, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    val title = state.displayName ?: state.email
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same initial as on Home, which knows only the account's email.
        GiftiaryAvatar(name = state.email ?: title.orEmpty(), size = 88.dp)
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.screenTitle,
                    color = colors.textPrimary,
                )
            }
            if (state.displayName != null && state.email != null) {
                Text(
                    text = state.email,
                    modifier = Modifier.padding(top = GiftiarySpacing.xs),
                    style = GiftiaryType.body,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Story(stats: ProfileStats, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_story).uppercase(),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.wordmark,
            color = colors.textLabel,
        )
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
