package app.giftiary.presentation.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.RowSupportingText
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun OwnOccasionsScreen(
    onBack: () -> Unit,
    onAddOccasion: () -> Unit,
    onOpenOccasion: (UUID) -> Unit,
    viewModel: OwnOccasionsViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    OwnOccasionsScreen(
        content = content,
        onBack = onBack,
        onAddOccasion = onAddOccasion,
        onOpenOccasion = { onOpenOccasion(it.id) },
    )
}

/**
 * The user's own occasions, reached from Home: a support list, so it has the task flows' back
 * circle rather than a top bar, and Add occasion sits under the title instead of floating.
 */
@Composable
fun OwnOccasionsScreen(
    content: OwnOccasionsContent,
    onBack: () -> Unit,
    onAddOccasion: () -> Unit,
    onOpenOccasion: (Occasion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (content) {
            OwnOccasionsContent.Loading -> GiftiaryTaskStatus(null, onBack, contentModifier)
            OwnOccasionsContent.LoadFailed ->
                GiftiaryTaskStatus(stringResource(R.string.own_occasions_load_failed), onBack, contentModifier)
            is OwnOccasionsContent.Loaded -> if (content.occasions.isEmpty()) {
                Column(contentModifier.fillMaxSize().giftiaryReadableWidth()) {
                    OwnOccasionsHeader(onBack = onBack, onAddOccasion = null)
                    EmptyOccasions(onAddOccasion = onAddOccasion)
                }
            } else {
                LazyColumn(
                    modifier = contentModifier.fillMaxSize().giftiaryReadableWidth(),
                    contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl),
                ) {
                    item { OwnOccasionsHeader(onBack = onBack, onAddOccasion = onAddOccasion) }
                    items(content.occasions, key = { it.id }) { occasion ->
                        GiftiaryRow(
                            title = occasionTitle(occasion),
                            supporting = { RowSupportingText(occasionDateLine(occasion)) },
                            trailing = { RowChevron() },
                            onClickLabel = stringResource(R.string.occasion_open),
                            onClick = { onOpenOccasion(occasion) },
                        )
                        GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter))
                    }
                }
            }
        }
    }
}

/** [onAddOccasion] is null on the empty state, whose own action is the one way to add. */
@Composable
private fun OwnOccasionsHeader(onBack: () -> Unit, onAddOccasion: (() -> Unit)?) {
    Column(
        modifier = Modifier.padding(
            start = GiftiarySpacing.browseGutter,
            end = GiftiarySpacing.browseGutter,
            top = GiftiarySpacing.browseGutter,
            bottom = GiftiarySpacing.l,
        ),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.own_occasions_title),
            subtitle = onAddOccasion?.let { stringResource(R.string.own_occasions_subtitle) },
            onBack = onBack,
            eyebrow = null,
        )
        if (onAddOccasion != null) {
            GiftiaryPillButton(
                text = stringResource(R.string.occasion_editor_add_title),
                onClick = onAddOccasion,
                modifier = Modifier.padding(top = GiftiarySpacing.xl),
                leadingIcon = painterResource(R.drawable.ic_add),
            )
        }
    }
}

/** No occasions of the user's own: an invitation to add the first, as the other lists' empty states do. */
@Composable
private fun EmptyOccasions(onAddOccasion: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(colors.thumbnailFallback, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = colors.textLabel,
            )
        }
        Text(
            text = stringResource(R.string.own_occasions_empty_title),
            modifier = Modifier
                .padding(top = GiftiarySpacing.xl)
                .semantics { heading() },
            style = GiftiaryType.sheetTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.own_occasions_empty_message),
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
        GiftiaryPillButton(
            text = stringResource(R.string.occasion_editor_add_title),
            onClick = onAddOccasion,
            modifier = Modifier.padding(top = GiftiarySpacing.xl),
            leadingIcon = painterResource(R.drawable.ic_add),
        )
    }
}

@PreviewLightDark
@Composable
private fun OwnOccasionsPreview() {
    GiftiaryTheme {
        OwnOccasionsScreen(
            content = OwnOccasionsContent.Loaded(
                listOf(
                    Occasion(UUID.randomUUID(), null, OccasionType.ANNIVERSARY, null, 18, 6, 2015, true, Instant.EPOCH, Instant.EPOCH),
                    Occasion(UUID.randomUUID(), null, OccasionType.CHRISTMAS, null, 24, 12, null, true, Instant.EPOCH, Instant.EPOCH),
                ),
            ),
            onBack = {},
            onAddOccasion = {},
            onOpenOccasion = {},
        )
    }
}
