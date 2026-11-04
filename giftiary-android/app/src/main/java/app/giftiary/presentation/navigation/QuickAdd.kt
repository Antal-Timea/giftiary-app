package app.giftiary.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import app.giftiary.R
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.RowSupportingText
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

internal enum class QuickAddGroup(@StringRes val label: Int) {
    REMEMBER(R.string.quick_add_remember),
    PLAN(R.string.quick_add_plan),
    ORGANIZE(R.string.quick_add_organize),
}

/** The Quick Add entries, in the sheet's order. There is deliberately no occasion entry. */
internal enum class QuickAddAction(
    val group: QuickAddGroup,
    @StringRes val title: Int,
    @StringRes val description: Int,
    paths: List<String>,
    dots: List<String> = emptyList(),
) {
    GIFT_GIVEN(
        QuickAddGroup.REMEMBER,
        R.string.quick_add_gift_given,
        R.string.quick_add_gift_given_description,
        listOf("M5,12h14M13,6l6,6 -6,6"),
    ),
    GIFT_RECEIVED(
        QuickAddGroup.REMEMBER,
        R.string.quick_add_gift_received,
        R.string.quick_add_gift_received_description,
        listOf("M19,12H5M11,6l-6,6 6,6"),
    ),
    GIFT_IDEA(
        QuickAddGroup.PLAN,
        R.string.quick_add_gift_idea,
        R.string.quick_add_gift_idea_description,
        listOf(
            "M9.4,18h5.2",
            "M10.2,21h3.6",
            "M12,3a6.2,6.2 0 0,0 -3.6,11.2c0.5,0.4 0.8,0.9 0.8,1.5v0.3h5.6v-0.3c0,-0.6 0.3,-1.1 0.8,-1.5A6.2,6.2 0 0,0 12,3Z",
        ),
    ),
    WISH(
        QuickAddGroup.PLAN,
        R.string.quick_add_wish,
        R.string.quick_add_wish_description,
        listOf(
            "M3.5,7.5h17v4h-17z",
            "M5.5,11.5v8.2a1,1 0 0,0 1,1h11a1,1 0 0,0 1,-1v-8.2",
            "M12,7.5v13.2",
            "M12,7.5c-1.1,-2.8 -3.2,-3.8 -4.5,-2.8S6.6,7.3 8,7.5h4ZM12,7.5c1.1,-2.8 3.2,-3.8 4.5,-2.8S17.4,7.3 16,7.5h-4Z",
        ),
    ),
    PERSON(
        QuickAddGroup.ORGANIZE,
        R.string.quick_add_person,
        R.string.quick_add_person_description,
        listOf(
            "M5.75,8a3.25,3.25 0 1,0 6.5,0a3.25,3.25 0 1,0 -6.5,0",
            "M3.5,19.5c0,-3 2.4,-5.2 5.5,-5.2s5.5,2.2 5.5,5.2",
            "M14.5,9a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
            "M15.5,14.2c2.4,0.3 4,2.2 4,4.8",
        ),
    ),
    LIST(
        QuickAddGroup.ORGANIZE,
        R.string.quick_add_list,
        R.string.quick_add_list_description,
        listOf("M9,6h11M9,12h11M9,18h11"),
        dots = listOf(
            "M3.25,6a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
            "M3.25,12a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
            "M3.25,18a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
        ),
    ),
    ;

    val icon: ImageVector by lazy { strokedIcon(paths, dots, strokeWidth = 1.9f) }
}

/**
 * Where an entry leads: always an existing add flow, so the sheet holds no form logic of its own.
 * A wish needs its wishlist: the one open behind the sheet, or else the Lists section to pick one.
 */
internal fun quickAddRoute(action: QuickAddAction, openWishlistId: String?): Any = when (action) {
    QuickAddAction.GIFT_GIVEN -> AddGiftRoute(direction = GiftDirection.GIVEN.name)
    QuickAddAction.GIFT_RECEIVED -> AddGiftRoute(direction = GiftDirection.RECEIVED.name)
    QuickAddAction.GIFT_IDEA -> AddGiftIdeaRoute()
    QuickAddAction.WISH -> openWishlistId?.let(::AddWishRoute) ?: WishlistsRoute
    QuickAddAction.PERSON -> AddPersonRoute()
    QuickAddAction.LIST -> AddWishlistRoute()
}

/** Opens [action]'s flow over the current screen; a section root is opened as a section instead. */
internal fun NavController.openQuickAdd(action: QuickAddAction) {
    val entry = currentBackStackEntry
    val openWishlistId = entry?.takeIf { it.destination.hasRoute<WishlistDetailRoute>() }
        ?.toRoute<WishlistDetailRoute>()?.wishlistId
    val route = quickAddRoute(action, openWishlistId)
    val section = AppSection.entries.find { it.root == route }
    if (section != null) navigateToSection(section) else navigate(route)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(onDismiss: () -> Unit, onAction: (QuickAddAction) -> Unit) {
    val colors = GiftiaryTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = GiftiaryShapes.sheet,
        containerColor = colors.background,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.borderStrong, GiftiaryShapes.pill),
            )
        },
    ) {
        QuickAddContent(onAction = onAction)
    }
}

@Composable
internal fun QuickAddContent(onAction: (QuickAddAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, bottom = 26.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m)) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(32.dp)
                    .background(colors.primaryAction, GiftiaryShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = PlusIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimaryAction)
            }
            Column {
                Text(
                    text = stringResource(R.string.quick_add_title),
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.sheetTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(GiftiarySpacing.xs))
                Text(text = stringResource(R.string.quick_add_subtitle), style = GiftiaryType.bodySmall, color = colors.textSecondary)
            }
        }
        QuickAddGroup.entries.forEach { group ->
            Text(
                text = stringResource(group.label).uppercase(),
                modifier = Modifier
                    .padding(top = if (group == QuickAddGroup.REMEMBER) 14.dp else GiftiarySpacing.l, bottom = GiftiarySpacing.s)
                    .semantics { heading() },
                style = GiftiaryType.overline,
                color = colors.textLabel,
            )
            val actions = QuickAddAction.entries.filter { it.group == group }
            if (group == QuickAddGroup.REMEMBER) {
                // Remembering is the app's first job: those two entries are larger, on parchment.
                Column(
                    Modifier
                        .background(colors.surfaceAlt, GiftiaryShapes.container)
                        .padding(horizontal = 14.dp, vertical = GiftiarySpacing.xs),
                ) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) GiftiaryDivider(startIndent = 60.dp)
                        QuickAddRow(action, tileSize = 46.dp, iconSize = 21.dp, titleStyle = GiftiaryType.actionLarge, onAction = onAction)
                    }
                }
            } else {
                actions.forEach { action ->
                    QuickAddRow(action, tileSize = 38.dp, iconSize = 17.dp, titleStyle = GiftiaryType.contentTitle.copy(fontWeight = FontWeight.Bold), onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun QuickAddRow(
    action: QuickAddAction,
    tileSize: Dp,
    iconSize: Dp,
    titleStyle: TextStyle,
    onAction: (QuickAddAction) -> Unit,
) {
    val colors = GiftiaryTheme.colors
    GiftiaryRow(
        title = stringResource(action.title),
        supporting = { RowSupportingText(stringResource(action.description)) },
        leading = {
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .background(colors.avatarFallback, GiftiaryShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = colors.textPrimary)
            }
        },
        modifier = Modifier.semantics { role = Role.Button },
        titleStyle = titleStyle,
        contentPadding = PaddingValues(vertical = if (tileSize > 40.dp) 10.dp else 3.dp),
        onClick = { onAction(action) },
    )
}

@Preview
@Composable
private fun QuickAddContentPreview() {
    GiftiaryTheme {
        QuickAddContent(onAction = {}, modifier = Modifier.background(GiftiaryTheme.colors.background))
    }
}
