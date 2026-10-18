package app.giftiary.presentation.sharing

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.wishlists.SectionHeading
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
        topBar = { GiftiaryTopBar(title = wishlistName ?: stringResource(R.string.wishlist_access_title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            AccessContent.Loading -> LoadingContent(contentModifier)
            is AccessContent.Failed -> FailureContent(
                message = accessFailureText(content.failure),
                // Unavailable stays so until the list syncs; retrying is still harmless.
                onRetry = actions.onRetry,
                modifier = contentModifier,
            )
            is AccessContent.Loaded -> AccessContents(state, content.access, actions, contentModifier)
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

@Composable
private fun AccessContents(
    state: WishlistAccessUiState,
    access: WishlistAccess,
    actions: WishlistAccessActions,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl)) {
        item {
            // Keeps what is shown while it is refreshed.
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.primaryAction)
            }
            Text(
                text = stringResource(R.string.wishlist_access_intro),
                modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
        if (state.showNamePrompt) {
            item { NamePrompt(onAddName = actions.onAddName, onDismiss = actions.onNamePromptDismissed) }
        }
        item {
            // Never waits for the name prompt: an invite can be created either way.
            val invite = state.newInvite
            if (invite != null) {
                NewInviteCard(invite = invite, onShare = { actions.onShare(invite) })
            } else {
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.wishlist_access_create_invite),
                    onClick = actions.onCreateInvite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                    loading = state.isCreating,
                )
            }
        }
        item { SectionHeading(stringResource(R.string.wishlist_access_invites)) }
        if (access.invites.isEmpty()) item { ListNote(stringResource(R.string.wishlist_access_no_invites)) }
        items(access.invites, key = { it.id }) { invite ->
            val created = sharingDate(invite.createdAt)
            ActionRow(
                title = stringResource(R.string.wishlist_access_invite),
                detail = stringResource(R.string.wishlist_access_invite_dates, created, sharingDate(invite.expiresAt)),
                actionLabel = stringResource(R.string.wishlist_access_revoke),
                actionDescription = stringResource(R.string.wishlist_access_revoke_description, created),
                onAction = { actions.onRevokeInvite(invite.id) },
                actionEnabled = invite.id !in state.changing,
            )
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
        item { SectionHeading(stringResource(R.string.wishlist_access_members)) }
        if (access.members.isEmpty()) item { ListNote(stringResource(R.string.wishlist_access_no_members)) }
        items(access.members, key = { it.userId }) { member ->
            val name = memberName(member.displayName)
            ActionRow(
                title = name,
                detail = stringResource(R.string.wishlist_access_member_joined, sharingDate(member.joinedAt)),
                actionLabel = stringResource(R.string.wishlist_access_remove),
                actionDescription = stringResource(R.string.wishlist_access_remove_description, name),
                onAction = { actions.onRemoveRequested(member) },
                actionEnabled = member.userId !in state.changing,
            )
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
    }
}

@Composable
private fun NewInviteCard(invite: CreatedInvite, onShare: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        shape = GiftiaryShapes.container,
        color = colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.padding(GiftiarySpacing.l), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            Text(
                text = stringResource(R.string.wishlist_access_new_invite_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.wishlist_access_new_invite_message, sharingDate(invite.expiresAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            GiftiaryPrimaryButton(
                text = stringResource(R.string.wishlist_access_share_link),
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Suggests a name so members know whose list it is; it asks, it never blocks. */
@Composable
private fun NamePrompt(onAddName: () -> Unit, onDismiss: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        shape = GiftiaryShapes.container,
        color = colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.padding(start = GiftiarySpacing.l, end = GiftiarySpacing.s, top = GiftiarySpacing.m)) {
            Text(
                text = stringResource(R.string.wishlist_access_name_prompt),
                modifier = Modifier.padding(end = GiftiarySpacing.s),
                style = MaterialTheme.typography.bodyMedium,
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
