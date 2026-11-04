package app.giftiary.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.giftiary.R
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

internal const val NAVIGATION_RAIL_TAG = "navigation_rail"

/**
 * [content] with the app's navigation: on a phone the bottom navigation unit, where [showBar]; on a
 * wide window the navigation rail beside it. The content keeps its place in the composition either
 * way, so a resize or a route without the bar never resets it.
 */
@Composable
internal fun AdaptiveNavigationFrame(
    selected: AppSection?,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
    showBar: Boolean = false,
    onAdd: () -> Unit = {},
    /** Invitations waiting, shown on the Wishlists section; nothing while none do. */
    invitationCount: Int = 0,
    content: @Composable () -> Unit,
) {
    val hasRail = LocalWidthClass.current.hasNavigationRail
    val hasBar = showBar && !hasRail
    Row(modifier) {
        if (hasRail) {
            GiftiaryNavigationRail(selected = selected, onSelect = onSelect, invitationCount = invitationCount)
        }
        Column(Modifier.weight(1f)) {
            // The bar keeps clear of the system navigation bar itself; screens above it must not too.
            Box(Modifier.weight(1f).then(if (hasBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
                content()
            }
            if (hasBar) {
                GiftiaryNavigationBar(selected = selected, onSelect = onSelect, onAdd = onAdd, invitationCount = invitationCount)
            }
        }
    }
}

/** The sections down the side of a wide window, Settings apart at the bottom. */
@Composable
internal fun GiftiaryNavigationRail(
    selected: AppSection?,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
    invitationCount: Int = 0,
) {
    NavigationRail(
        modifier = modifier.testTag(NAVIGATION_RAIL_TAG),
        containerColor = GiftiaryTheme.colors.navigationSurface,
    ) {
        Spacer(Modifier.height(GiftiarySpacing.l))
        AppSection.entries.filter { it != AppSection.SETTINGS }.forEach { section ->
            RailItem(section, selected == section, onSelect, badge = if (section == AppSection.WISHLISTS) invitationCount else 0)
        }
        Spacer(Modifier.weight(1f))
        RailItem(AppSection.SETTINGS, selected == AppSection.SETTINGS, onSelect)
        Spacer(Modifier.height(GiftiarySpacing.l))
    }
}

@Composable
private fun RailItem(section: AppSection, selected: Boolean, onSelect: (AppSection) -> Unit, badge: Int = 0) {
    val colors = GiftiaryTheme.colors
    NavigationRailItem(
        selected = selected,
        onClick = { onSelect(section) },
        modifier = Modifier.invitationsState(badge),
        // The label names the item; the icon only illustrates it.
        icon = { WithInvitationBadge(badge) { Icon(painterResource(section.icon), contentDescription = null) } },
        // Long labels (e.g. "Kívánságlisták") wrap onto a second line rather than being cut.
        label = { Text(stringResource(section.label), textAlign = TextAlign.Center, maxLines = 2) },
        alwaysShowLabel = true,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.navigationSelected,
            selectedTextColor = colors.navigationSelected,
            indicatorColor = colors.surfaceContext,
            unselectedIconColor = colors.navigationUnselected,
            unselectedTextColor = colors.navigationUnselected,
        ),
    )
}

/** A coral count over [icon] while invitations wait; screen readers hear it as the item's state. */
@Composable
internal fun WithInvitationBadge(count: Int, icon: @Composable () -> Unit) {
    if (count <= 0) {
        icon()
        return
    }
    val colors = GiftiaryTheme.colors
    BadgedBox(
        badge = {
            Badge(
                modifier = Modifier.clearAndSetSemantics {},
                containerColor = colors.primaryAction,
                contentColor = colors.onPrimaryAction,
            ) { Text(if (count > 99) "99+" else count.toString()) }
        },
    ) { icon() }
}

@Composable
internal fun Modifier.invitationsState(count: Int): Modifier {
    if (count <= 0) return this
    val state = pluralStringResource(R.plurals.nav_invitations_badge, count, count)
    return semantics { stateDescription = state }
}

@PreviewLightDark
@Composable
private fun GiftiaryNavigationRailPreview() {
    GiftiaryTheme {
        GiftiaryNavigationRail(selected = AppSection.PEOPLE, onSelect = {})
    }
}
