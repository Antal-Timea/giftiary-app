package app.giftiary.presentation.giftideas

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.presentation.components.GiftThumbnail
import app.giftiary.presentation.components.GiftiaryDateField
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.gifts.priceErrorText
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.LocalDate
import java.util.UUID

/**
 * Shown as a dialog destination over the idea, so the idea and the phone's navigation stay in
 * view behind the sheet, dimmed, as on the approved board.
 */
@Composable
fun MarkGiftIdeaGivenScreen(
    viewModel: MarkGiftIdeaGivenViewModel,
    onBack: () -> Unit,
    onGiven: (giftId: UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status, state.giftId) {
        val giftId = state.giftId
        if (state.status == MarkGivenStatus.DONE && giftId != null) onGiven(giftId)
    }
    // The sheet draws its own scrim; the dialog window's default dimming would double it.
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { dialogWindow?.setDimAmount(0f) }
    MarkGiftIdeaGivenScreen(
        state = state,
        onCancel = onBack,
        onDateChange = viewModel::onDateChange,
        onPriceChange = viewModel::onPriceChange,
        onConfirm = viewModel::confirm,
    )
}

@Composable
fun MarkGiftIdeaGivenScreen(
    state: MarkGivenUiState,
    onCancel: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        // Tapping beside the sheet cancels, as with any sheet; Cancel says the same to every user.
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .pointerInput(onCancel) { detectTapGestures { onCancel() } },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .giftiaryReadableWidth()
                .background(colors.background, GiftiaryShapes.sheet)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, bottom = GiftiarySpacing.l),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.borderStrong, GiftiaryShapes.pill),
            )
            Text(
                text = stringResource(R.string.gift_idea_mark_as_given),
                modifier = Modifier.semantics { heading() },
                style = GiftiaryType.display.copy(fontSize = GiftiaryType.screenTitle.fontSize),
                color = colors.textPrimary,
            )
            when (state.status) {
                MarkGivenStatus.LOADING -> SheetMessage(message = null)
                MarkGivenStatus.NOT_AVAILABLE -> SheetMessage(stringResource(R.string.gift_idea_not_found))
                MarkGivenStatus.LOAD_FAILED -> SheetMessage(stringResource(R.string.gift_idea_load_failed))
                MarkGivenStatus.ALREADY_GIVEN -> SheetMessage(stringResource(R.string.gift_idea_already_given))
                MarkGivenStatus.READY, MarkGivenStatus.DONE -> MarkGivenForm(
                    state = state,
                    onDateChange = onDateChange,
                    onPriceChange = onPriceChange,
                    onConfirm = onConfirm,
                )
            }
            GiftiaryTextAction(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = GiftiarySpacing.s),
            )
        }
    }
}

@Composable
private fun SheetMessage(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(vertical = GiftiarySpacing.l),
        contentAlignment = Alignment.Center,
    ) {
        if (message == null) {
            CircularProgressIndicator(color = GiftiaryTheme.colors.textPrimary)
        } else {
            Text(
                text = message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = GiftiaryType.body,
                color = GiftiaryTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * The board's sheet: the idea, when it was given and what it finally cost; the rest of the gift
 * is taken from the idea. The idea leaves the plans, which is said before saving.
 */
@Composable
private fun MarkGivenForm(
    state: MarkGivenUiState,
    onDateChange: (LocalDate) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    val canEdit = state.status == MarkGivenStatus.READY && !state.isBusy
    Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
        Row(
            modifier = Modifier
                .padding(top = GiftiarySpacing.l)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ideas have no photo; the branded glyph tile stands in, as in lists.
            GiftThumbnail(size = 54.dp, shape = GiftiaryShapes.photoCard)
            Column {
                Text(text = state.ideaName, style = GiftiaryType.actionLarge, color = colors.textPrimary)
                val context = listOfNotNull(
                    state.recipientName?.let { stringResource(R.string.gift_idea_for_person, it) },
                    state.occasion?.let { occasionTitle(it) },
                )
                if (context.isNotEmpty()) {
                    Text(text = context.joinToString(" · "), style = GiftiaryType.bodySmall, color = colors.textSecondary)
                }
            }
        }
        Text(
            text = stringResource(R.string.gift_idea_mark_given_explanation),
            style = GiftiaryType.bodySmall,
            color = colors.textSecondary,
        )
        GiftiaryDateField(
            label = stringResource(R.string.gift_idea_given_when),
            date = state.givenAt,
            onDateChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            GiftiaryTextField(
                value = state.price,
                onValueChange = onPriceChange,
                label = stringResource(R.string.gift_idea_final_price_optional),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = state.priceError?.let { priceErrorText(it, state.currencyCode) },
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                suffix = state.currencyCode,
            )
            if (state.priceError == null) {
                state.estimate?.let {
                    Text(
                        text = stringResource(R.string.gift_idea_estimated_line, stringResource(R.string.wish_price_estimate, giftPrice(it))),
                        style = GiftiaryType.metadata,
                        color = colors.textMuted,
                    )
                }
            }
        }
        // Stays until the next try, so the failure is not missed; the form keeps what was entered.
        if (state.writeFailed) {
            Text(
                text = stringResource(R.string.gift_idea_mark_given_failed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = GiftiaryType.bodySmall,
                color = colors.error,
            )
        }
        GiftiaryPrimaryButton(
            text = stringResource(R.string.gift_idea_save_to_history),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.status == MarkGivenStatus.READY,
            loading = state.isBusy,
            trailingArrow = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun MarkGiftIdeaGivenPreview() {
    GiftiaryTheme {
        MarkGiftIdeaGivenScreen(
            state = MarkGivenUiState(
                givenAt = LocalDate.of(2026, 3, 12),
                status = MarkGivenStatus.READY,
                ideaName = "Wool scarf",
                recipientName = "Anna",
                price = "9900",
                estimate = Money(10000, "HUF"),
            ),
            onCancel = {},
            onDateChange = {},
            onPriceChange = {},
            onConfirm = {},
        )
    }
}
