package app.giftiary.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.GiftPhotoCamera
import app.giftiary.presentation.gifts.GiftPhotoSources
import app.giftiary.presentation.people.PersonAvatar
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun EditProfileScreen(viewModel: EditProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditProfileContent(
        state = state,
        actions = EditProfileActions(
            onBack = onBack,
            onNameChange = viewModel::onNameChange,
            onSave = viewModel::onSave,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown,
        ),
        avatar = EditProfileAvatarActions(
            onPick = viewModel::onAvatarPicked,
            camera = GiftPhotoCamera(
                newTarget = viewModel::newCaptureTarget,
                onCaptured = viewModel::onAvatarCaptured,
                onCancelled = viewModel::onCaptureCancelled,
                onUnavailable = viewModel::onCameraUnavailable,
            ),
            onRemove = viewModel::onAvatarRemoved,
            onRetry = viewModel::onAvatarRetry,
        ),
    )
}

class EditProfileActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onMessageShown: () -> Unit,
)

class EditProfileAvatarActions(
    val onPick: (String) -> Unit = {},
    val camera: GiftPhotoCamera = GiftPhotoCamera(newTarget = { null }, onCaptured = {}, onCancelled = {}, onUnavailable = {}),
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The profile's two editable parts: the avatar only the user sees, and the name people they share with see. */
@Composable
fun EditProfileContent(
    state: EditProfileUiState,
    actions: EditProfileActions,
    modifier: Modifier = Modifier,
    avatar: EditProfileAvatarActions = EditProfileAvatarActions(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = when {
        state.saved -> stringResource(R.string.settings_name_saved)
        state.saveFailure == ProfileFailure.NETWORK -> stringResource(R.string.sharing_network_error)
        state.saveFailure != null -> stringResource(R.string.sharing_server_error)
        state.avatarMessage == AvatarMessage.SAVED -> stringResource(R.string.profile_photo_saved)
        state.avatarMessage == AvatarMessage.REMOVED -> stringResource(R.string.profile_photo_removed)
        else -> null
    }
    LaunchedEffect(state.saved, state.saveFailure, state.avatarMessage) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.browseGutter),
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        ) {
            GiftiaryTaskHeader(
                title = stringResource(R.string.profile_edit),
                subtitle = stringResource(R.string.settings_display_name_hint),
                onBack = actions.onBack,
                modifier = Modifier.padding(bottom = GiftiarySpacing.s),
            )
            if (state.profile is ProfileContent.Loaded) {
                GiftPhotoSources(onPick = avatar.onPick, camera = avatar.camera) { choose ->
                    AvatarField(state = state, onChoose = choose, onRemove = avatar.onRemove, onRetry = avatar.onRetry)
                }
            }
            NameForm(state, actions)
        }
    }
}

/**
 * The saved avatar, or the monogram, with what can be done to it. The picture is decorative: the
 * actions say whether there is one. A failed change keeps the saved avatar and says so.
 */
@Composable
private fun AvatarField(state: EditProfileUiState, onChoose: () -> Unit, onRemove: () -> Unit, onRetry: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val enabled = !state.avatarBusy
    Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(name = state.email ?: state.nameInput, photo = state.avatar, size = AVATAR_SIZE)
            // Beside the avatar while they fit, under one another at large text.
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
                verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                GiftiarySecondaryButton(
                    text = stringResource(if (state.avatarPath == null) R.string.person_photo_add else R.string.person_photo_change),
                    onClick = onChoose,
                    enabled = enabled,
                )
                if (state.avatarPath != null) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.heightIn(min = 48.dp),
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                    ) {
                        Text(stringResource(R.string.person_photo_remove), style = GiftiaryType.actionSmall)
                    }
                }
                if (state.avatarBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryAction, strokeWidth = 2.dp)
                }
            }
        }
        Text(text = stringResource(R.string.profile_photo_private), style = GiftiaryType.metadata, color = colors.textSecondary)
        state.avatarFailure?.let { failure ->
            Text(
                text = avatarFailureText(failure),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = GiftiaryType.body,
                color = colors.textSecondary,
            )
            if (state.retry != null) {
                GiftiarySecondaryButton(text = stringResource(R.string.sharing_retry), onClick = onRetry, enabled = enabled)
            }
        }
    }
}

@Composable
private fun avatarFailureText(failure: AvatarFailure): String = when (failure) {
    AvatarFailure.UNREADABLE -> stringResource(R.string.gift_photo_unreadable)
    AvatarFailure.CAMERA_UNAVAILABLE -> stringResource(R.string.gift_photo_camera_unavailable)
    AvatarFailure.NOT_ON_DEVICE -> stringResource(R.string.gift_photo_unavailable_on_save)
    AvatarFailure.NETWORK -> stringResource(R.string.profile_photo_unchanged) + " " + stringResource(R.string.sharing_network_error)
    AvatarFailure.SERVER -> stringResource(R.string.profile_photo_unchanged) + " " + stringResource(R.string.sharing_server_error)
}

private val AVATAR_SIZE = 88.dp

@Composable
private fun NameForm(state: EditProfileUiState, actions: EditProfileActions) {
    val colors = GiftiaryTheme.colors
    when (val profile = state.profile) {
        ProfileContent.Loading -> CircularProgressIndicator(color = colors.primaryAction)
        is ProfileContent.Failed -> {
            Text(
                text = stringResource(
                    when (profile.failure) {
                        ProfileFailure.UNAVAILABLE -> R.string.settings_profile_unavailable
                        ProfileFailure.NETWORK -> R.string.sharing_network_error
                        ProfileFailure.SERVER -> R.string.sharing_server_error
                    },
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = GiftiaryType.body,
                color = colors.textSecondary,
            )
            GiftiarySecondaryButton(text = stringResource(R.string.sharing_retry), onClick = actions.onRetry)
        }
        is ProfileContent.Loaded -> {
            GiftiaryTextField(
                value = state.nameInput,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.settings_display_name),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = if (state.inputInvalid) stringResource(R.string.settings_display_name_invalid) else null,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
            )
            GiftiaryPrimaryButton(
                text = stringResource(R.string.settings_save_name),
                onClick = actions.onSave,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSaving,
            )
        }
    }
}

@Preview
@Composable
private fun EditProfilePreview() {
    GiftiaryTheme {
        EditProfileContent(
            state = EditProfileUiState(profile = ProfileContent.Loaded(saved = "Anna"), nameInput = "Anna"),
            actions = EditProfileActions({}, {}, {}, {}, {}),
        )
    }
}
