package app.giftiary.presentation.sharing

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryBackButton
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.OptionsMenuScope
import app.giftiary.presentation.components.giftiaryDashedOutline
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun WishlistAccessScreen(viewModel: WishlistAccessViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Also on returning from Settings, where the name may just have been added.
    LifecycleResumeEffect(viewModel) {
        viewModel.checkOwnerName()
        onPauseOrDispose {}
    }
    val name by viewModel.wishlistName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val chooserTitle = stringResource(R.string.wishlist_access_share_chooser)
    WishlistAccessScreen(
        state = state,
        wishlistName = name,
        actions = WishlistAccessActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onCreateInvite = viewModel::onCreateInvite,
            onShare = { invite ->
                val link = joinLink(invite.token)
                val text = name?.let { resources.getString(R.string.wishlist_access_share_text, it, link) }
                    ?: resources.getString(R.string.wishlist_access_share_text_unnamed, link)
                shareText(context, text, chooserTitle)
            },
            onRevokeInvite = viewModel::onRevokeInvite,
            onRemoveRequested = viewModel::onRemoveRequested,
            onRemoveConfirmed = viewModel::onRemoveConfirmed,
            onRemoveDismissed = viewModel::onRemoveDismissed,
            onMessageShown = viewModel::onMessageShown,
            onAddName = onOpenSettings,
            onNamePromptDismissed = viewModel::onNamePromptDismissed,
        ),
    )
}

/** Hands the link to the app the user picks; Giftiary itself never sends it anywhere. */
private fun shareText(context: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

class WishlistAccessActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onCreateInvite: () -> Unit,
    val onShare: (CreatedInvite) -> Unit,
    val onRevokeInvite: (UUID) -> Unit,
    val onRemoveRequested: (WishlistMember) -> Unit,
    val onRemoveConfirmed: () -> Unit,
    val onRemoveDismissed: () -> Unit,
    val onMessageShown: () -> Unit,
    val onAddName: () -> Unit,
    val onNamePromptDismissed: () -> Unit,
)

@Composable
fun WishlistAccessScreen(
    state: WishlistAccessUiState,
    wishlistName: String?,
    actions: WishlistAccessActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.let { accessFailureText(it) }
    LaunchedEffect(state.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            AccessContent.Loading -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            is AccessContent.Failed -> Column(modifier = contentModifier.fillMaxSize()) {
                GiftiaryBackButton(
                    onClick = actions.onBack,
                    modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter),
                )
                FailureContent(
                    message = accessFailureText(content.failure),
                    // Unavailable stays so until the list syncs; retrying is still harmless.
                    onRetry = actions.onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
            is AccessContent.Loaded -> AccessContents(state, content.access, wishlistName, actions, contentModifier)
        }
    }

    state.confirmingRemoval?.let { member ->
        val name = memberName(member.displayName)
        GiftiaryConfirmDialog(
            title = stringResource(R.string.wishlist_access_remove_dialog_title, name),
            message = stringResource(R.string.wishlist_access_remove_dialog_message),
            confirmLabel = stringResource(R.string.wishlist_access_remove),
            onConfirm = actions.onRemoveConfirmed,
            onDismiss = actions.onRemoveDismissed,
        )
    }
}

/**
 * The approved Share & Invite: the list, the people with access and "Share with someone", then the
 * note on reservations. People are shown only as the server returns them: a name, or a neutral
 * label, and when they joined; nothing the user keeps about them, so no relationship line. Sharing
 * works through invite links, so "Share with someone" creates one, and the open links follow the
 * people with access. Removing, withdrawing and the rest stay behind each row's options.
 */
@Composable
private fun AccessContents(
    state: WishlistAccessUiState,
    access: WishlistAccess,
    wishlistName: String?,
    actions: WishlistAccessActions,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(
            start = GiftiarySpacing.taskGutter,
            end = GiftiarySpacing.taskGutter,
            top = GiftiarySpacing.browseGutter,
            bottom = GiftiarySpacing.xxl,
        ),
    ) {
        item {
            GiftiaryTaskHeader(
                title = stringResource(R.string.wishlist_access_title),
                subtitle = stringResource(R.string.wishlist_access_intro),
                onBack = actions.onBack,
                wordmark = false,
                context = wishlistName?.let { name -> { SharedListName(name) } },
            )
            // Keeps what is shown while it is refreshed.
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = GiftiarySpacing.l),
                    color = colors.primaryAction,
                )
            }
        }
        if (state.showNamePrompt) {
            item { NamePrompt(onAddName = actions.onAddName, onDismiss = actions.onNamePromptDismissed) }
        }

        item { AccessHeading(stringResource(R.string.wishlist_access_members)) }
        if (access.members.isEmpty()) item { QuietLine(stringResource(R.string.wishlist_access_no_members)) }
        items(access.members, key = { it.userId }) { member ->
            val name = memberName(member.displayName)
            AccessRow(
                leading = { GiftiaryAvatar(name = name) },
                title = name,
                detail = stringResource(R.string.wishlist_access_member_joined, sharingDate(member.joinedAt)),
                optionsDescription = stringResource(R.string.wishlist_access_member_more, name),
            ) {
                Option(
                    text = stringResource(R.string.wishlist_access_remove_description, name),
                    icon = R.drawable.ic_delete,
                    onClick = { actions.onRemoveRequested(member) },
                    enabled = member.userId !in state.changing,
                )
            }
        }
        item {
            // Never waits for the name prompt: an invite can be created either way.
            val invite = state.newInvite
            if (invite != null) {
                NewInviteCard(invite = invite, onShare = { actions.onShare(invite) })
            } else {
                ShareWithSomeone(onClick = actions.onCreateInvite, creating = state.isCreating)
            }
        }

        if (access.invites.isNotEmpty()) {
            item { AccessHeading(stringResource(R.string.wishlist_access_invites)) }
            items(access.invites, key = { it.id }) { invite ->
                val created = sharingDate(invite.createdAt)
                AccessRow(
                    leading = { LinkMark() },
                    title = stringResource(R.string.wishlist_access_invite),
                    detail = stringResource(R.string.wishlist_access_invite_dates, created, sharingDate(invite.expiresAt)),
                    optionsDescription = stringResource(R.string.wishlist_access_invite_more, created),
                ) {
                    Option(
                        text = stringResource(R.string.wishlist_access_revoke_description, created),
                        icon = R.drawable.ic_close,
                        onClick = { actions.onRevokeInvite(invite.id) },
                        enabled = invite.id !in state.changing,
                    )
                }
            }
        }

        item { ReservationsNote() }
    }
}

/** The list being shared, under the title: its cover and name. */
@Composable
private fun SharedListName(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GiftiaryAvatar(name = name, size = 24.dp)
        Text(text = name, style = GiftiaryType.contentTitle, color = GiftiaryTheme.colors.textLabel)
    }
}

@Composable
private fun AccessHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = GiftiarySpacing.section, bottom = GiftiarySpacing.s)
            .semantics { heading() },
        style = GiftiaryType.sectionTitle.copy(fontSize = 18.sp),
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = GiftiarySpacing.s),
        style = GiftiaryType.body,
        color = GiftiaryTheme.colors.textSecondary,
    )
}

/** A person or an invite: a 48dp mark, two lines, and its actions behind one quiet ⋮. */
@Composable
private fun AccessRow(
    leading: @Composable () -> Unit,
    title: String,
    detail: String,
    optionsDescription: String,
    options: @Composable OptionsMenuScope.() -> Unit,
) {
    val colors = GiftiaryTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(vertical = GiftiarySpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = GiftiarySpacing.l),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = GiftiaryType.rowTitle, color = colors.textPrimary)
                Text(text = detail, style = GiftiaryType.body, color = colors.textSecondary)
            }
            OptionsMenu(R.drawable.ic_more_vertical, optionsDescription, items = options)
        }
        GiftiaryDivider(modifier = Modifier.padding(start = 64.dp))
    }
}

/** The board's dashed coral "Share with someone"; it creates an invite link to share. */
@Composable
private fun ShareWithSomeone(onClick: () -> Unit, creating: Boolean) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = !creating, role = Role.Button, onClick = onClick)
            .padding(vertical = GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .giftiaryDashedOutline(colors.primaryActionText, 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryActionText, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.primaryActionText)
            }
        }
        Text(
            text = stringResource(R.string.wishlist_access_create_invite),
            modifier = Modifier.padding(start = GiftiarySpacing.l),
            style = GiftiaryType.rowTitle,
            color = colors.primaryActionText,
        )
    }
}

@Composable
private fun LinkMark() {
    val colors = GiftiaryTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(colors.surfaceContext, GiftiaryShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_link), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textPrimary)
    }
}

@Composable
private fun NewInviteCard(invite: CreatedInvite, onShare: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .padding(vertical = GiftiarySpacing.m)
            .fillMaxWidth()
            .background(colors.infoSurface, GiftiaryShapes.container)
            .padding(GiftiarySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
    ) {
        Text(
            text = stringResource(R.string.wishlist_access_new_invite_title),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.rowTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.wishlist_access_new_invite_message, sharingDate(invite.expiresAt)),
            style = GiftiaryType.body,
            color = colors.textPrimary,
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.wishlist_access_share_link),
            onClick = onShare,
            modifier = Modifier
                .padding(top = GiftiarySpacing.xs)
                .fillMaxWidth(),
        )
    }
}

/** Suggests a name so members know whose list it is; it asks, it never blocks. */
@Composable
private fun NamePrompt(onAddName: () -> Unit, onDismiss: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .padding(top = GiftiarySpacing.l)
            .fillMaxWidth()
            .background(colors.infoSurface, GiftiaryShapes.container)
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.s, top = GiftiarySpacing.m),
    ) {
        Text(
            text = stringResource(R.string.wishlist_access_name_prompt),
            modifier = Modifier.padding(end = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textPrimary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)) {
                Text(stringResource(R.string.wishlist_access_name_prompt_later))
            }
            TextButton(onClick = onAddName, colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText)) {
                Text(stringResource(R.string.wishlist_access_name_prompt_add))
            }
        }
    }
}

/** Owners never learn about reservations; the server does not tell this screen about them at all. */
@Composable
private fun ReservationsNote() {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier.padding(top = GiftiarySpacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
    ) {
        Icon(
            painterResource(R.drawable.ic_gift_glyph),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            tint = colors.textSecondary,
        )
        Text(text = stringResource(R.string.wishlist_access_reservations_note), style = GiftiaryType.body, color = colors.textSecondary)
    }
}

@Composable
private fun accessFailureText(failure: SharingFailure): String = when (failure) {
    SharingFailure.UNAVAILABLE -> stringResource(R.string.wishlist_access_unavailable)
    SharingFailure.INVITE_LIMIT -> stringResource(R.string.wishlist_access_invite_limit)
    else -> retryableFailureText(failure)
}

@PreviewLightDark
@Composable
private fun WishlistAccessPreview() {
    val now = Instant.parse("2026-10-17T10:00:00Z")
    val invite = ActiveInvite(UUID.randomUUID(), now, now.plusSeconds(7 * 86_400))
    GiftiaryTheme {
        WishlistAccessScreen(
            state = WishlistAccessUiState(
                content = AccessContent.Loaded(
                    WishlistAccess(
                        invites = listOf(invite),
                        members = listOf(
                            WishlistMember(UUID.randomUUID(), "Anna", now),
                            WishlistMember(UUID.randomUUID(), null, now),
                        ),
                    ),
                ),
                newInvite = CreatedInvite(invite.id, InviteToken.of("A".repeat(43))!!, invite.expiresAt),
            ),
            wishlistName = "Birthday 2027",
            actions = WishlistAccessActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
