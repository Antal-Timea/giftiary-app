package app.giftiary.presentation.sharing

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.giftiary.R
import app.giftiary.domain.repository.SharingFailure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A person's name as the server gives it, or a neutral label; never anything like their email. */
@Composable
internal fun memberName(displayName: String?): String = displayName ?: stringResource(R.string.sharing_member_fallback)

/** For a failure that trying again may fix; an unavailable answer has its own wording per screen. */
@Composable
internal fun retryableFailureText(failure: SharingFailure): String = when (failure) {
    SharingFailure.NETWORK -> stringResource(R.string.sharing_network_error)
    else -> stringResource(R.string.sharing_server_error)
}

@Composable
internal fun sharingDate(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(instant.atZone(ZoneId.systemDefault()))
}

/**
 * Who a shared list is for and who shared it, never one standing in for the other: "For Áron ·
 * Shared by Timi", or only "Shared by Timi" for the sharer's own list. The two are never matched
 * by name.
 */
@Composable
internal fun sharedListWhose(personName: String?, ownerDisplayName: String?): String = listOfNotNull(
    personName?.let { stringResource(R.string.shared_wishlist_for_person_short, it) },
    stringResource(R.string.shared_wishlist_shared_by, memberName(ownerDisplayName)),
).joinToString(" · ")
