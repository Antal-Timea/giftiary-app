package app.giftiary.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.components.EditorialHero
import app.giftiary.presentation.components.EditorialImage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/** The three approved chapters, in order, each with its owner-approved image. */
enum class OnboardingChapter(
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @DrawableRes val image: Int,
) {
    REMEMBER(R.string.onboarding_remember_eyebrow, R.string.onboarding_remember_title, R.string.onboarding_remember_body, GiftCutout.LARGE.image),
    PLAN(R.string.onboarding_plan_eyebrow, R.string.onboarding_plan_title, R.string.onboarding_plan_body, GiftCutout.SMALL.image),
    SHARE(R.string.onboarding_share_eyebrow, R.string.onboarding_share_title, R.string.onboarding_share_body, R.drawable.giftiary_onboarding_share),
}

/**
 * The introduction on a new installation. Continue moves one chapter on and the last one's Get
 * started finishes it; Skip finishes it from anywhere. Back returns a chapter and, from the first,
 * leaves the app as usual, so nothing can loop. Finishing leads to sign-in, and it is not shown
 * again.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = index > 0) { index-- }
    val chapters = OnboardingChapter.entries
    OnboardingContent(
        chapter = chapters[index],
        onContinue = { if (index == chapters.lastIndex) onDone() else index++ },
        onSkip = onDone,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingContent(
    chapter: OnboardingChapter,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .giftiaryReadableWidth(),
    ) {
        // The chapter scrolls when large text needs it; the progress and Continue stay below it.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The supporting image takes what the text leaves of a phone screen, never less than a band.
                val imageHeight = (maxWidth * 1.1f).coerceIn(160.dp, 460.dp)
                Column {
                    when (chapter) {
                        OnboardingChapter.REMEMBER -> Remember(imageHeight, onSkip)
                        OnboardingChapter.PLAN -> Plan(imageHeight, onSkip)
                        OnboardingChapter.SHARE -> Share(imageHeight, onSkip)
                    }
                }
            }
        }
        Progress(
            position = chapter.ordinal + 1,
            count = OnboardingChapter.entries.size,
            modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.l),
        )
        GiftiaryPrimaryButton(
            text = stringResource(if (chapter == OnboardingChapter.entries.last()) R.string.onboarding_get_started else R.string.onboarding_continue),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(GiftiarySpacing.taskGutter),
            trailingArrow = true,
        )
    }
}

/** Remember: the image first, straight on the page, then the words. */
@Composable
private fun ColumnScope.Remember(imageHeight: Dp, onSkip: () -> Unit) {
    TopRow(onSkip)
    // G1-3 at full strength: the kept gift, whole, the chapter's picture rather than decoration.
    Image(
        painter = painterResource(OnboardingChapter.REMEMBER.image),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(imageHeight)
            .padding(horizontal = GiftiarySpacing.l),
    )
    Words(OnboardingChapter.REMEMBER, showTitle = true)
}

/** Plan: the words first, then the round image leaving the screen to the right. */
@Composable
private fun ColumnScope.Plan(imageHeight: Dp, onSkip: () -> Unit) {
    val colors = GiftiaryTheme.colors
    TopRow(onSkip)
    Heading(OnboardingChapter.PLAN)
    // G1-4 inside the aqua ring, close up, the circle leaving the screen to the right.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(imageHeight)
            .padding(vertical = GiftiarySpacing.l),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val diameter = imageHeight - GiftiarySpacing.xxl
        Box(
            modifier = Modifier
                .size(diameter)
                .offset(x = diameter / 4)
                .background(colors.surfaceContextQuiet, GiftiaryShapes.pill)
                .padding(GiftiarySpacing.xl)
                .clip(GiftiaryShapes.pill)
                .background(colors.surfaceContext),
        ) {
            Image(
                painter = painterResource(OnboardingChapter.PLAN.image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(PLAN_ZOOM),
            )
        }
    }
    Words(OnboardingChapter.PLAN, showTitle = false)
}

/**
 * Share: the warm image, ending in the hero's rounded edge, then the words. The approved
 * photograph fills its frame with the gift, so the words follow it instead of covering it.
 */
@Composable
private fun ColumnScope.Share(imageHeight: Dp, onSkip: () -> Unit) {
    // G1-2, centred: the gift, the hands and the coral marks all stay in the frame.
    EditorialHero(
        modifier = Modifier
            .fillMaxWidth()
            .height(imageHeight + GiftiarySpacing.xxxl),
        image = { EditorialImage(OnboardingChapter.SHARE.image) },
    ) {
        TopRow(onSkip)
    }
    Words(OnboardingChapter.SHARE, showTitle = true)
    Row(
        modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.l),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_gift_reserve),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = GiftiaryTheme.colors.textLabel,
        )
        Text(
            text = stringResource(R.string.onboarding_share_privacy),
            style = GiftiaryType.bodySmall,
            color = GiftiaryTheme.colors.textSecondary,
        )
    }
}

/** The wordmark, and Skip, which finishes the introduction from any chapter. */
@Composable
private fun TopRow(onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.l, top = GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            modifier = Modifier.weight(1f),
            style = GiftiaryType.wordmark,
            color = GiftiaryTheme.colors.textPrimary,
        )
        GiftiaryTextAction(text = stringResource(R.string.onboarding_skip), onClick = onSkip)
    }
}

@Composable
private fun Heading(chapter: OnboardingChapter) {
    Column(Modifier.padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.l)) {
        Text(
            text = stringResource(chapter.eyebrow).uppercase(),
            style = GiftiaryType.wordmark,
            color = GiftiaryTheme.colors.textLabel,
        )
        Text(
            text = stringResource(chapter.title),
            modifier = Modifier
                .padding(top = GiftiarySpacing.m)
                .semantics { heading() },
            style = GiftiaryType.display,
            color = GiftiaryTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun Words(chapter: OnboardingChapter, showTitle: Boolean) {
    if (showTitle) Heading(chapter)
    Text(
        text = stringResource(chapter.body),
        modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.l),
        style = GiftiaryType.body.copy(fontSize = GiftiaryType.contentTitle.fontSize),
        color = GiftiaryTheme.colors.textSecondary,
    )
}

/** The chapter dots, the current one long, and "1 / 3", read out as "Step 1 of 3". */
@Composable
private fun Progress(position: Int, count: Int, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    val description = stringResource(R.string.onboarding_progress_description, position, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val current = i + 1 == position
            Box(
                Modifier
                    .size(width = if (current) 22.dp else 10.dp, height = 4.dp)
                    .background(if (current) colors.textPrimary else colors.border, GiftiaryShapes.pill),
            )
        }
        Box(Modifier.width(GiftiarySpacing.m))
        Text(
            text = stringResource(R.string.onboarding_progress, position, count),
            style = GiftiaryType.actionSmall,
            color = colors.textMuted,
        )
    }
}

@Preview
@Composable
private fun OnboardingPreview() {
    GiftiaryTheme {
        OnboardingContent(OnboardingChapter.PLAN, {}, {})
    }
}

// The board shows the Plan gift close up, filling the ring rather than floating in it.
private const val PLAN_ZOOM = 1.25f
