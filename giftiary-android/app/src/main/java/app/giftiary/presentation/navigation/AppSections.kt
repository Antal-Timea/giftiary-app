package app.giftiary.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import app.giftiary.R
import app.giftiary.presentation.components.WidthClass
import kotlin.reflect.KClass

/** The parts of the app the navigation rail leads to on wide windows. */
enum class AppSection(@StringRes val label: Int, @DrawableRes val icon: Int, val root: Any) {
    HOME(R.string.nav_home, R.drawable.ic_nav_home, HomeRoute),
    PEOPLE(R.string.people_title, R.drawable.ic_nav_people, PeopleRoute),
    GIFTS(R.string.gifts_title, R.drawable.ic_nav_gifts, GiftHistoryRoute),
    WISHLISTS(R.string.wishlists_title, R.drawable.ic_nav_wishlists, WishlistsRoute),
    SETTINGS(R.string.settings_title, R.drawable.ic_nav_settings, SettingsRoute),
}

/** Wide windows navigate by the rail; phones keep Home's buttons and back arrows. */
val WidthClass.hasNavigationRail: Boolean get() = this != WidthClass.COMPACT

/**
 * The section every signed-in destination belongs to, so the rail marks its parent: details and
 * editors their list's section, occasions Home (where they are planned), sharing Wishlists.
 */
internal val routeSections: Map<KClass<*>, AppSection> = mapOf(
    HomeRoute::class to AppSection.HOME,
    SyncAttentionRoute::class to AppSection.HOME,
    OwnOccasionsRoute::class to AppSection.HOME,
    AddOccasionRoute::class to AppSection.HOME,
    EditOccasionRoute::class to AppSection.HOME,
    OccasionPlanRoute::class to AppSection.HOME,
    PeopleRoute::class to AppSection.PEOPLE,
    PersonDetailRoute::class to AppSection.PEOPLE,
    AddPersonRoute::class to AppSection.PEOPLE,
    EditPersonRoute::class to AppSection.PEOPLE,
    GiftHistoryRoute::class to AppSection.GIFTS,
    GiftDetailRoute::class to AppSection.GIFTS,
    AddGiftRoute::class to AppSection.GIFTS,
    EditGiftRoute::class to AppSection.GIFTS,
    GiftIdeaDetailRoute::class to AppSection.GIFTS,
    AddGiftIdeaRoute::class to AppSection.GIFTS,
    EditGiftIdeaRoute::class to AppSection.GIFTS,
    MarkGiftIdeaGivenRoute::class to AppSection.GIFTS,
    WishlistsRoute::class to AppSection.WISHLISTS,
    WishlistDetailRoute::class to AppSection.WISHLISTS,
    AddWishlistRoute::class to AppSection.WISHLISTS,
    EditWishlistRoute::class to AppSection.WISHLISTS,
    WishDetailRoute::class to AppSection.WISHLISTS,
    AddWishRoute::class to AppSection.WISHLISTS,
    EditWishRoute::class to AppSection.WISHLISTS,
    WishlistAccessRoute::class to AppSection.WISHLISTS,
    SharedWishlistsRoute::class to AppSection.WISHLISTS,
    SharedWishlistRoute::class to AppSection.WISHLISTS,
    JoinWishlistRoute::class to AppSection.WISHLISTS,
    SettingsRoute::class to AppSection.SETTINGS,
)

@Suppress("UNCHECKED_CAST")
internal fun NavDestination.section(): AppSection? =
    routeSections.entries.firstOrNull { (route, _) -> hasRoute(route as KClass<Any>) }?.value

/**
 * The usual top-level navigation: one stack per section, kept while another section is open and
 * restored on return; reselecting a section never stacks a second copy of it.
 */
internal fun sectionNavOptions(startDestinationId: Int): NavOptions = navOptions {
    popUpTo(startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

internal fun NavController.navigateToSection(section: AppSection) =
    navigate(section.root, sectionNavOptions(graph.findStartDestination().id))
