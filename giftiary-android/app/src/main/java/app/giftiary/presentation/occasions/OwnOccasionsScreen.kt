package app.giftiary.presentation.occasions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
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

@Composable
fun OwnOccasionsScreen(
    content: OwnOccasionsContent,
    onBack: () -> Unit,
    onAddOccasion: () -> Unit,
    onOpenOccasion: (Occasion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.own_occasions_title), onBack = onBack) },
        floatingActionButton = { AddOccasionButton(onClick = onAddOccasion) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (content) {
            OwnOccasionsContent.Loading -> LoadingContent(contentModifier)
            OwnOccasionsContent.LoadFailed -> MessageContent(stringResource(R.string.own_occasions_load_failed), contentModifier)
            is OwnOccasionsContent.Loaded -> if (content.occasions.isEmpty()) {
                MessageContent(
                    title = stringResource(R.string.own_occasions_empty_title),
                    message = stringResource(R.string.own_occasions_empty_message),
                    modifier = contentModifier,
                )
            } else {
                LazyColumn(
                    modifier = contentModifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = GiftiarySpacing.s, bottom = FAB_CLEARANCE),
                ) {
                    items(content.occasions, key = { it.id }) { occasion ->
                        OccasionRow(occasion = occasion, onClick = { onOpenOccasion(occasion) }, clickLabel = R.string.people_open)
                        HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = GiftiaryTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOccasionButton(onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    // The content overload keeps the label in the semantics tree, as on the People screen.
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.primaryAction,
        contentColor = colors.onPrimaryAction,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(GiftiarySpacing.m))
        Text(stringResource(R.string.occasion_editor_add_title))
    }
}

private val FAB_CLEARANCE = 96.dp

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
