package app.giftiary.presentation.gifts

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.HeroCircleButton
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.PendingSyncStatus
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.RowSupportingText
import app.giftiary.presentation.components.arrow
import app.giftiary.presentation.components.directionColor
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun GiftDetailScreen(
    viewModel: GiftDetailViewModel,
    // Null beside the history list, which stays in view instead.
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    onEditGift: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == GiftDetailContent.Deleted) onDeleted()
    }
    GiftDetailScreen(
        state = state,
        onBack = onBack,
        onEditGift = { onEditGift(it.gift.id) },
        onOpenPerson = { onOpenPerson(it.id) },
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
    )
}

/**
 * A gift as a memory: its photo as the hero, or the branded fallback in the same shape; the people
 * it connects; the facts on aqua; and the note in handwriting. Editing and deleting wait in the
 * hero's options, so neither competes with the memory.
 */
@Composable
fun GiftDetailScreen(
    state: GiftDetailUiState,
    onBack: (() -> Unit)?,
    onEditGift: (GiftEntry) -> Unit,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedMessage = stringResource(R.string.gift_delete_failed)
    LaunchedEffect(state.actions.deleteFailed) {
        if (state.actions.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            onDeleteErrorShown()
        }
    }

    val entry = (state.content as? GiftDetailContent.Loaded)?.entry
    // The hero runs up under the status bar; the other states keep clear of it themselves.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val messageModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (state.content) {
            GiftDetailContent.Loading, GiftDetailContent.Deleted -> LoadingContent(messageModifier)
            GiftDetailContent.NotFound -> MessageContent(stringResource(R.string.gift_not_found), messageModifier)
            GiftDetailContent.LoadFailed -> MessageContent(stringResource(R.string.gift_load_failed), messageModifier)
            is GiftDetailContent.Loaded -> GiftDetailContent(
                entry = state.content.entry,
                state = state,
                onBack = onBack,
                onEdit = { onEditGift(state.content.entry) },
                onOpenPerson = onOpenPerson,
                onDeleteRequested = onDeleteRequested,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (state.actions.confirmingDelete && entry != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.gift_delete_dialog_title, entry.gift.name),
            message = stringResource(R.string.gift_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun GiftDetailContent(
    entry: GiftEntry,
    state: GiftDetailUiState,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gift = entry.gift
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(bottom = GiftiarySpacing.xxl),
    ) {
        GiftHero(
            entry = entry,
            // Until it is read, and when it cannot be read now, the fallback holds the photo's place.
            photo = state.photo?.photo,
            onBack = onBack,
            onEdit = onEdit,
            deleteEnabled = !state.actions.isDeleting,
            onDeleteRequested = onDeleteRequested,
        )
        if (state.pendingSync) {
            PendingSyncStatus(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l))
        }
        // A gift between two other people has both; the user's own gifts have only the other side.
        listOfNotNull(entry.giver, entry.recipient).forEach { person ->
            PersonRow(person = person, onClick = { onOpenPerson(person) })
        }
        GiftFacts(entry = entry, modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l))
        gift.note?.takeIf { it.isNotBlank() }?.let { note ->
            GiftNote(note = note, modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.xl))
        }
    }
}

@Composable
private fun GiftHero(
    entry: GiftEntry,
    photo: LoadedPhoto?,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    deleteEnabled: Boolean,
    onDeleteRequested: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    val image = rememberDecodedImage(photo)
    val onImage = image != null
    val circle = if (onImage) colors.background.copy(alpha = 0.85f) else colors.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(GiftiaryShapes.hero)
            .background(colors.surfaceContext),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.gift_photo_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Darkens only towards the bottom, where the name sits, so the photo stays itself.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to colors.photoScrim.copy(alpha = 0.06f),
                            0.3f to Color.Transparent,
                            0.58f to colors.photoScrim.copy(alpha = 0.10f),
                            1f to colors.photoScrim.copy(alpha = 0.46f),
                        ),
                    ),
            )
        } else {
            // No photo: the branded gift glyph, placed like the monogram on Person detail.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 28.dp, y = (-6).dp)
                    .size(196.dp)
                    .background(colors.surfaceContextQuiet, GiftiaryShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gift_glyph),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp),
                    tint = colors.textLabel,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = GiftiarySpacing.s),
        ) {
            if (onBack != null) {
                HeroCircleButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack, containerColor = circle)
            }
            Spacer(Modifier.weight(1f))
            GiftOptions(
                giftName = entry.gift.name,
                circle = circle,
                onEdit = onEdit,
                deleteEnabled = deleteEnabled,
                onDeleteRequested = onDeleteRequested,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = HERO_TEXT_MAX_WIDTH)
                .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xl),
        ) {
            val direction = entry.gift.direction
            val lineColor = if (onImage) colors.onPhoto else directionColor(direction)
            val shadow = if (onImage) Shadow(colors.photoScrim.copy(alpha = 0.45f), Offset(0f, 2f), blurRadius = 12f) else null
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(direction.arrow), contentDescription = null, modifier = Modifier.size(13.dp), tint = lineColor)
                Text(text = giftDirectionLine(entry), style = GiftiaryType.label.copy(shadow = shadow), color = lineColor)
            }
            Text(
                text = entry.gift.name,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .semantics { heading() },
                style = GiftiaryType.screenTitle.copy(shadow = shadow),
                color = if (onImage) colors.onPhoto else colors.textPrimary,
            )
        }
    }
}

/** The hero's options: Edit, then a deliberately separate Delete that still asks first. */
@Composable
private fun GiftOptions(
    giftName: String,
    circle: Color,
    onEdit: () -> Unit,
    deleteEnabled: Boolean,
    onDeleteRequested: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        HeroCircleButton(R.drawable.ic_more, stringResource(R.string.gift_detail_more, giftName), { open = true }, containerColor = circle)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = GiftiaryShapes.photoCard,
            containerColor = colors.surfaceElevated,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gift_edit), style = GiftiaryType.actionSmall, color = colors.textPrimary) },
                onClick = {
                    open = false
                    onEdit()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textPrimary) },
            )
            GiftiaryDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gift_delete), style = GiftiaryType.actionSmall, color = colors.textMuted) },
                onClick = {
                    open = false
                    onDeleteRequested()
                },
                enabled = deleteEnabled,
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted) },
            )
        }
    }
}

/** The relationship, and the way through to everything else shared with this person. */
@Composable
private fun PersonRow(person: Person, onClick: () -> Unit) {
    GiftiaryRow(
        title = person.name,
        modifier = Modifier.padding(top = GiftiarySpacing.s),
        supporting = person.relationship?.takeIf { it.isNotBlank() }?.let { { RowSupportingText(it) } },
        leading = { GiftiaryAvatar(name = person.name, size = 46.dp) },
        trailing = { RowChevron() },
        onClickLabel = stringResource(R.string.people_open),
        onClick = onClick,
    )
}

/** Only the facts the gift has: the date always, the occasion, price and link when known. */
@Composable
private fun GiftFacts(entry: GiftEntry, modifier: Modifier = Modifier) {
    val gift = entry.gift
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GiftiaryTheme.colors.infoSurface, GiftiaryShapes.container)
            .padding(horizontal = 18.dp, vertical = GiftiarySpacing.s),
    ) {
        val date = giftDate(gift.givenAt)
        FactRow(icon = R.drawable.ic_calendar, text = entry.occasion?.let { "${occasionTitle(it)} · $date" } ?: date)
        gift.price?.let {
            FactDivider()
            FactRow(icon = R.drawable.ic_price_tag, text = giftPrice(it))
        }
        gift.productUrl?.let { url ->
            FactDivider()
            FactRow(
                icon = R.drawable.ic_link,
                text = url,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.gift_product_url)) {
                    // A stored link may not be openable (no browser, unusual scheme); that is not an app error.
                    runCatching { uriHandler.openUri(url) }
                },
                link = true,
            )
        }
    }
}

@Composable
private fun FactRow(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier, link: Boolean = false) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(17.dp), tint = colors.textPrimary)
        Text(
            text = text,
            style = GiftiaryType.fieldValue.copy(fontSize = GiftiaryType.body.fontSize),
            color = colors.textPrimary,
            textDecoration = if (link) TextDecoration.Underline else null,
            maxLines = if (link) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FactDivider() {
    GiftiaryDivider(startIndent = 28.dp)
}

/** What the user remembers about it, in the one place handwriting belongs. */
@Composable
private fun GiftNote(note: String, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.support.copy(alpha = 0.55f), GiftiaryShapes.pill),
        )
        Text(
            text = note,
            modifier = Modifier.padding(start = GiftiarySpacing.m),
            style = GiftiaryType.handwriting,
            color = colors.textLabel,
        )
    }
}

private val HERO_HEIGHT = 400.dp

// The name keeps to the photo's left, clear of the fallback glyph, as on the board.
private val HERO_TEXT_MAX_WIDTH = 324.dp

@PreviewLightDark
@Composable
private fun GiftDetailPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        GiftDetailScreen(
            state = GiftDetailUiState(
                content = GiftDetailContent.Loaded(
                    GiftEntry(
                        gift = Gift(
                            id = UUID.randomUUID(),
                            direction = GiftDirection.GIVEN,
                            giverPersonId = null,
                            recipientPersonId = anna.id,
                            occasionId = null,
                            sourceGiftIdeaId = null,
                            name = "Birthday book",
                            givenAt = LocalDate.of(2026, 3, 12),
                            price = Money(12500, "HUF"),
                            photoPath = null,
                            productUrl = "https://example.com/book",
                            note = "Signed by the author.",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                        giver = null,
                        recipient = anna,
                        occasion = null,
                    ),
                ),
                waitingToSync = true,
            ),
            onBack = {},
            onEditGift = {},
            onOpenPerson = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onDeleteErrorShown = {},
        )
    }
}
