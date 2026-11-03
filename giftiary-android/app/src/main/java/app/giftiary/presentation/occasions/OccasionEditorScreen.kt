package app.giftiary.presentation.occasions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.usecase.OccasionInputError
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDropdownField
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

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

    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            OccasionEditorStatus.LOADING -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            OccasionEditorStatus.NOT_FOUND ->
                GiftiaryTaskStatus(stringResource(R.string.occasion_editor_not_found), actions.onBack, contentModifier)
            OccasionEditorStatus.PERSON_NOT_FOUND ->
                GiftiaryTaskStatus(stringResource(R.string.person_editor_not_found), actions.onBack, contentModifier)
            OccasionEditorStatus.LOAD_FAILED ->
                GiftiaryTaskStatus(stringResource(R.string.occasion_editor_load_failed), actions.onBack, contentModifier)
            OccasionEditorStatus.EDITING, OccasionEditorStatus.CLOSED -> DecoratedPage(top = GiftCutout.LARGE) {
                OccasionForm(state = state, actions = actions, modifier = contentModifier)
            }
        }
    }

    if (state.confirmingDelete) {
        val occasionName = state.title.trim().takeIf { state.showsTitle && it.isNotEmpty() }
            ?: occasionTypeLabel(state.type)
        GiftiaryConfirmDialog(
            title = stringResource(R.string.occasion_delete_dialog_title, occasionName),
            message = stringResource(R.string.occasion_delete_dialog_message),
            confirmLabel = stringResource(R.string.occasion_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

/**
 * Add and Edit occasion, by analogy with the approved editors (there is no board): the task
 * header with Delete in its menu, the fields, and one Save. The date stays day, month and year,
 * as the year may be unknown.
 */
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
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(if (state.isNew) R.string.occasion_editor_add_title else R.string.occasion_editor_edit_title),
            subtitle = stringResource(if (state.isNew) R.string.occasion_editor_add_subtitle else R.string.occasion_editor_edit_subtitle),
            onBack = actions.onBack,
            menu = if (state.isNew) null else {
                {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.occasion_editor_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = canEdit)
                    }
                }
            },
        )
        Column(
            modifier = Modifier.padding(top = GiftiarySpacing.section),
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.fieldGroup),
        ) {
            GiftiaryDropdownField(
                label = stringResource(R.string.occasion_editor_type),
                options = state.typeOptions,
                selected = state.type,
                optionLabel = { occasionTypeLabel(it) },
                onSelect = actions.onTypeChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
            )
            if (state.showsTitle) {
                GiftiaryTextField(
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
            GiftiaryTextField(
                value = state.day,
                onValueChange = actions.onDayChange,
                label = stringResource(R.string.occasion_editor_day),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = errorText(state, OccasionInputError.DAY_REQUIRED, OccasionInputError.DAY_NOT_IN_MONTH),
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            GiftiaryDropdownField(
                label = stringResource(R.string.occasion_editor_month),
                options = MONTHS,
                selected = state.month,
                optionLabel = { monthName(it) },
                onSelect = actions.onMonthChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                errorMessage = errorText(state, OccasionInputError.MONTH_REQUIRED),
            )
            GiftiaryTextField(
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
        }
        GiftiaryPrimaryButton(
            text = stringResource(R.string.occasion_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xxl),
            enabled = state.status == OccasionEditorStatus.EDITING,
            loading = state.isBusy,
        )
    }
}

/** Styled as the Settings switches: teal, as coral is kept for creating. */
@Composable
private fun RepeatsYearlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.occasion_editor_repeats_yearly),
                style = GiftiaryType.actionLarge,
                color = if (enabled) colors.textPrimary else colors.textMuted,
            )
            Text(
                text = stringResource(R.string.occasion_editor_repeats_yearly_hint),
                style = GiftiaryType.bodySmall,
                color = colors.textSecondary,
            )
        }
        // The whole row is the toggle, so the switch itself takes no clicks or focus.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.support,
                checkedBorderColor = colors.support,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.borderStrong,
            ),
        )
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
    GiftiaryTheme {
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
    GiftiaryTheme {
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
