package app.gifty.presentation.occasions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.OccasionType
import app.gifty.domain.usecase.OccasionInputError
import app.gifty.presentation.components.GiftyConfirmDialog
import app.gifty.presentation.components.GiftyDropdownField
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTextField
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun OccasionEditorScreen(
    viewModel: OccasionEditorViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == OccasionEditorStatus.CLOSED) onClose()
    }
    OccasionEditorScreen(
        state = state,
        actions = OccasionEditorActions(
            onBack = onClose,
            onTypeChange = viewModel::onTypeChange,
            onTitleChange = viewModel::onTitleChange,
            onDayChange = viewModel::onDayChange,
            onMonthChange = viewModel::onMonthChange,
            onYearChange = viewModel::onYearChange,
            onRepeatsYearlyChange = viewModel::onRepeatsYearlyChange,
            onSave = viewModel::save,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onErrorShown = viewModel::onErrorShown,
        ),
    )
}

/** Groups the editor's many callbacks so the screen signature stays readable. */
class OccasionEditorActions(
    val onBack: () -> Unit,
    val onTypeChange: (OccasionType) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onDayChange: (String) -> Unit,
    val onMonthChange: (Int) -> Unit,
    val onYearChange: (String) -> Unit,
    val onRepeatsYearlyChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeleteDismissed: () -> Unit,
    val onErrorShown: () -> Unit,
)

@Composable
fun OccasionEditorScreen(
    state: OccasionEditorUiState,
    actions: OccasionEditorActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.occasion_editor_write_failed)
    LaunchedEffect(state.writeFailed) {
        if (state.writeFailed) {
            snackbarHostState.showSnackbar(writeFailedMessage)
            actions.onErrorShown()
        }
    }

    val title = if (state.isNew) R.string.occasion_editor_add_title else R.string.occasion_editor_edit_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            OccasionEditorStatus.LOADING -> LoadingContent(contentModifier)
            OccasionEditorStatus.NOT_FOUND ->
                MessageContent(stringResource(R.string.occasion_editor_not_found), contentModifier)
            OccasionEditorStatus.PERSON_NOT_FOUND ->
                MessageContent(stringResource(R.string.person_editor_not_found), contentModifier)
            OccasionEditorStatus.LOAD_FAILED ->
                MessageContent(stringResource(R.string.occasion_editor_load_failed), contentModifier)
            OccasionEditorStatus.EDITING, OccasionEditorStatus.CLOSED ->
                OccasionForm(state = state, actions = actions, modifier = contentModifier)
        }
    }

    if (state.confirmingDelete) {
        val occasionName = state.title.trim().takeIf { state.showsTitle && it.isNotEmpty() }
            ?: occasionTypeLabel(state.type)
        GiftyConfirmDialog(
            title = stringResource(R.string.occasion_delete_dialog_title, occasionName),
            message = stringResource(R.string.occasion_delete_dialog_message),
            confirmLabel = stringResource(R.string.occasion_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

@Composable
private fun OccasionForm(
    state: OccasionEditorUiState,
    actions: OccasionEditorActions,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == OccasionEditorStatus.EDITING && !state.isBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        GiftyDropdownField(
            label = stringResource(R.string.occasion_editor_type),
            options = state.typeOptions,
            selected = state.type,
            optionLabel = { occasionTypeLabel(it) },
            onSelect = actions.onTypeChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
        )
        if (state.showsTitle) {
            GiftyTextField(
                value = state.title,
                onValueChange = actions.onTitleChange,
                label = stringResource(R.string.occasion_editor_title),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.occasion_editor_title_hint),
                errorMessage = errorText(state, OccasionInputError.TITLE_REQUIRED),
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
        }
        GiftyTextField(
            value = state.day,
            onValueChange = actions.onDayChange,
            label = stringResource(R.string.occasion_editor_day),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = errorText(state, OccasionInputError.DAY_REQUIRED, OccasionInputError.DAY_NOT_IN_MONTH),
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        GiftyDropdownField(
            label = stringResource(R.string.occasion_editor_month),
            options = MONTHS,
            selected = state.month,
            optionLabel = { monthName(it) },
            onSelect = actions.onMonthChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            errorMessage = errorText(state, OccasionInputError.MONTH_REQUIRED),
        )
        GiftyTextField(
            value = state.year,
            onValueChange = actions.onYearChange,
            label = stringResource(
                if (state.yearRequired) R.string.occasion_editor_year else R.string.occasion_editor_year_optional,
            ),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = errorText(
                state,
                OccasionInputError.NOT_A_LEAP_YEAR,
                OccasionInputError.YEAR_REQUIRED,
                OccasionInputError.INVALID_YEAR,
            ),
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        if (state.showsRepeatsYearly) {
            RepeatsYearlyRow(
                checked = state.repeatsYearly,
                onCheckedChange = actions.onRepeatsYearlyChange,
                enabled = canEdit,
            )
        }
        GiftyPrimaryButton(
            text = stringResource(R.string.occasion_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftySpacing.s),
            enabled = state.status == OccasionEditorStatus.EDITING,
            loading = state.isBusy,
        )
        if (!state.isNew) {
            DeleteOccasionButton(onClick = actions.onDeleteRequested, enabled = canEdit)
        }
    }
}

@Composable
private fun RepeatsYearlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    val colors = GiftyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.occasion_editor_repeats_yearly),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        // The whole row is the toggle, so the switch itself takes no clicks or focus.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimaryAction,
                checkedTrackColor = colors.primaryAction,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.inputSurface,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

@Composable
private fun DeleteOccasionButton(onClick: () -> Unit, enabled: Boolean) {
    // Deliberately quiet: deleting should not compete with saving.
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textSecondary),
    ) {
        Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
        Spacer(Modifier.width(GiftySpacing.s))
        Text(stringResource(R.string.occasion_editor_delete))
    }
}

@Composable
private fun errorText(state: OccasionEditorUiState, vararg shown: OccasionInputError): String? =
    shown.firstOrNull { it in state.errors }?.let { error ->
        when (error) {
            OccasionInputError.TITLE_REQUIRED -> stringResource(R.string.occasion_error_title_required)
            OccasionInputError.DAY_REQUIRED -> stringResource(R.string.occasion_error_day_required)
            OccasionInputError.MONTH_REQUIRED -> stringResource(R.string.occasion_error_month_required)
            OccasionInputError.DAY_NOT_IN_MONTH -> stringResource(R.string.occasion_error_day_not_in_month)
            OccasionInputError.NOT_A_LEAP_YEAR ->
                stringResource(R.string.occasion_error_not_a_leap_year, state.year.toInt())
            OccasionInputError.INVALID_YEAR -> stringResource(R.string.occasion_error_invalid_year)
            OccasionInputError.YEAR_REQUIRED -> stringResource(R.string.occasion_error_year_required)
        }
    }

private val MONTHS = (1..12).toList()

private val previewActions = OccasionEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@PreviewLightDark
@Composable
private fun OccasionEditorBirthdayPreview() {
    GiftyTheme {
        OccasionEditorScreen(
            state = OccasionEditorUiState(
                isNew = false,
                status = OccasionEditorStatus.EDITING,
                type = OccasionType.BIRTHDAY,
                day = "29",
                month = 2,
                year = "2025",
                errors = setOf(OccasionInputError.NOT_A_LEAP_YEAR),
            ),
            actions = previewActions,
        )
    }
}

@PreviewLightDark
@Composable
private fun OccasionEditorCustomPreview() {
    GiftyTheme {
        OccasionEditorScreen(
            state = OccasionEditorUiState(
                isNew = true,
                status = OccasionEditorStatus.EDITING,
                type = OccasionType.CUSTOM,
                repeatsYearly = false,
                errors = setOf(OccasionInputError.TITLE_REQUIRED, OccasionInputError.DAY_REQUIRED),
            ),
            actions = previewActions,
        )
    }
}
