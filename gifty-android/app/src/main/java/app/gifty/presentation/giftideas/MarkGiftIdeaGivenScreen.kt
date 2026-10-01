package app.gifty.presentation.giftideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.presentation.components.GiftyDateField
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTextField
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.gifts.priceErrorText
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.time.LocalDate
import java.util.UUID

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
    MarkGiftIdeaGivenScreen(
        state = state,
        onBack = onBack,
        onDateChange = viewModel::onDateChange,
        onPriceChange = viewModel::onPriceChange,
        onConfirm = viewModel::confirm,
        onErrorShown = viewModel::onErrorShown,
    )
}

@Composable
fun MarkGiftIdeaGivenScreen(
    state: MarkGivenUiState,
    onBack: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.writeError?.let {
        stringResource(
            when (it) {
                MarkGivenError.FAILED -> R.string.gift_idea_mark_given_failed
                MarkGivenError.ALREADY_GIVEN -> R.string.gift_idea_already_given
            },
        )
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(R.string.gift_idea_mark_as_given), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            MarkGivenStatus.LOADING -> LoadingContent(contentModifier)
            MarkGivenStatus.NOT_AVAILABLE ->
                MessageContent(stringResource(R.string.gift_idea_not_found), contentModifier)
            MarkGivenStatus.LOAD_FAILED ->
                MessageContent(stringResource(R.string.gift_idea_load_failed), contentModifier)
            MarkGivenStatus.READY, MarkGivenStatus.DONE -> MarkGivenForm(
                state = state,
                onDateChange = onDateChange,
                onPriceChange = onPriceChange,
                onConfirm = onConfirm,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun MarkGivenForm(
    state: MarkGivenUiState,
    onDateChange: (LocalDate) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftyTheme.colors
    val canEdit = state.status == MarkGivenStatus.READY && !state.isBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
            Text(
                text = state.ideaName,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            state.recipientName?.let {
                Text(
                    text = stringResource(R.string.gift_idea_for_person, it),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        Text(
            text = stringResource(R.string.gift_idea_mark_given_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        GiftyDateField(
            label = stringResource(R.string.gift_idea_date_given),
            date = state.givenAt,
            onDateChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
        )
        GiftyTextField(
            value = state.price,
            onValueChange = onPriceChange,
            label = stringResource(R.string.gift_idea_final_price_optional),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = state.priceError?.let { priceErrorText(it, state.currencyCode) },
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            suffix = state.currencyCode,
        )
        GiftyPrimaryButton(
            text = stringResource(R.string.gift_idea_mark_as_given),
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftySpacing.s),
            enabled = state.status == MarkGivenStatus.READY,
            loading = state.isBusy,
        )
    }
}

@PreviewLightDark
@Composable
private fun MarkGiftIdeaGivenPreview() {
    GiftyTheme {
        MarkGiftIdeaGivenScreen(
            state = MarkGivenUiState(
                givenAt = LocalDate.of(2026, 3, 12),
                status = MarkGivenStatus.READY,
                ideaName = "Wool scarf",
                recipientName = "Anna",
                price = "9900",
            ),
            onBack = {},
            onDateChange = {},
            onPriceChange = {},
            onConfirm = {},
            onErrorShown = {},
        )
    }
}
