package app.giftiary.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.giftiaryReadableWidth
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
    )
}

class EditProfileActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onMessageShown: () -> Unit,
)

/** The name people the user shares wishlists with see: the profile's one editable field. */
@Composable
fun EditProfileContent(state: EditProfileUiState, actions: EditProfileActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = when {
        state.saved -> stringResource(R.string.settings_name_saved)
        state.saveFailure == ProfileFailure.NETWORK -> stringResource(R.string.sharing_network_error)
        state.saveFailure != null -> stringResource(R.string.sharing_server_error)
        else -> null
    }
    LaunchedEffect(state.saved, state.saveFailure) {
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
            NameForm(state, actions)
        }
    }
}

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
