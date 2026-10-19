package app.giftiary.presentation.navigation

import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.SyncItem
import app.giftiary.domain.model.SyncItemType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object HomeRoute

@Serializable
data object PeopleRoute

@Serializable
data object SyncAttentionRoute

@Serializable
data object SettingsRoute

@Serializable
data class PersonDetailRoute(val personId: String)

@Serializable
data object AddPersonRoute

@Serializable
data class EditPersonRoute(val personId: String)

/** Without a person, a new occasion of the user's own. */
@Serializable
data class AddOccasionRoute(val personId: String? = null)

@Serializable
data object OwnOccasionsRoute

@Serializable
data class EditOccasionRoute(val occasionId: String)

@Serializable
data object GiftHistoryRoute

@Serializable
data class GiftDetailRoute(val giftId: String)

/** Both arguments only preselect the editor; [direction] is a [GiftDirection] name. */
@Serializable
data class AddGiftRoute(val direction: String? = null, val personId: String? = null)

@Serializable
data class EditGiftRoute(val giftId: String)

@Serializable
data class GiftIdeaDetailRoute(val giftIdeaId: String)

/** [personId] only preselects the recipient. */
/** [personId] and [occasionId] preselect the new idea's recipient and occasion; a malformed one is ignored. */
@Serializable
data class AddGiftIdeaRoute(val personId: String? = null, val occasionId: String? = null)

@Serializable
data class OccasionPlanRoute(val occasionId: String)

/** Coming up and Your occasions open an occasion's plan. Person detail still opens its editor. */
internal fun occasionPlanRoute(occasionId: UUID) = OccasionPlanRoute(occasionId.toString())

/** A new idea from an occasion's plan: for the occasion, and for its person if it has one. */
internal fun addIdeaRoute(occasion: Occasion) =
    AddGiftIdeaRoute(personId = occasion.personId?.toString(), occasionId = occasion.id.toString())

@Serializable
data class EditGiftIdeaRoute(val giftIdeaId: String)

@Serializable
data class MarkGiftIdeaGivenRoute(val giftIdeaId: String)

@Serializable
data object WishlistsRoute

@Serializable
data class WishlistDetailRoute(val wishlistId: String)

/** [personId] only preselects who the new wishlist is for; a malformed or unavailable one is ignored. */
@Serializable
data class AddWishlistRoute(val personId: String? = null)

@Serializable
data class EditWishlistRoute(val wishlistId: String)

@Serializable
data class WishDetailRoute(val wishId: String)

@Serializable
data class AddWishRoute(val wishlistId: String)

@Serializable
data class EditWishRoute(val wishId: String)

@Serializable
data class WishlistAccessRoute(val wishlistId: String)

@Serializable
data object SharedWishlistsRoute

@Serializable
data class SharedWishlistRoute(val wishlistId: String)

/** Takes an opened join link from memory when it opens; a token is never a route argument. */
@Serializable
data object JoinWishlistRoute

/** The existing screen of an item that needs attention: its detail screen, or the editor for an occasion. */
internal fun routeOf(item: SyncItem): Any = when (item.type) {
    SyncItemType.PERSON -> PersonDetailRoute(item.id.toString())
    SyncItemType.OCCASION -> EditOccasionRoute(item.id.toString())
    SyncItemType.GIFT_IDEA -> GiftIdeaDetailRoute(item.id.toString())
    SyncItemType.GIFT -> GiftDetailRoute(item.id.toString())
    SyncItemType.WISHLIST -> WishlistDetailRoute(item.id.toString())
    SyncItemType.WISH -> WishDetailRoute(item.id.toString())
}

/** A joined list opens in place of the join screen, so back leads to where the join started. */
internal fun joinedRoute(wishlistId: UUID) = SharedWishlistRoute(wishlistId.toString())

/** A route argument as an id; null when it is malformed, which callers treat as absent. */
internal fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
