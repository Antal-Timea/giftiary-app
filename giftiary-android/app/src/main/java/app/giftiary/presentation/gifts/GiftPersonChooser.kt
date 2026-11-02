package app.giftiary.presentation.gifts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.FieldError
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiarySearchField
import app.giftiary.presentation.components.giftiaryDashedOutline
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.util.UUID

/**
 * Who a gift was for or from, picked from a row of the user's people, which the search narrows.
 * The chosen person leads the row even when the search would hide them, so the choice is always
 * visible; "New" adds someone without leaving the gift behind.
 */
@Composable
internal fun GiftPersonChooser(
    people: List<Person>,
    selectedId: UUID?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (Person) -> Unit,
    onAddPerson: () -> Unit,
    enabled: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val selected = people.find { it.id == selectedId }
    val matches = people.filter { it.id != selectedId && it.name.contains(query.trim(), ignoreCase = true) }
    val shown = listOfNotNull(selected) + matches
    Column(modifier = modifier.fillMaxWidth()) {
        GiftiarySearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.gift_editor_search_people),
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(
            modifier = Modifier
                .padding(top = GiftiarySpacing.l)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
        ) {
            items(shown, key = { it.id }) { person ->
                PersonChoice(person, selected = person.id == selectedId, enabled = enabled, onClick = { onSelect(person) })
            }
            item(key = "new") { NewPersonChoice(enabled = enabled, onClick = onAddPerson) }
        }
        if (matches.isEmpty() && query.isNotBlank()) {
            Text(
                text = stringResource(R.string.gift_editor_no_match),
                modifier = Modifier.padding(top = GiftiarySpacing.s),
                style = GiftiaryType.bodySmall,
                color = GiftiaryTheme.colors.textSecondary,
            )
        }
        if (errorMessage != null) {
            Box(Modifier.padding(top = GiftiarySpacing.s)) { FieldError(errorMessage) }
        }
    }
}

@Composable
private fun PersonChoice(person: Person, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    ChoiceColumn(
        modifier = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        GiftiaryAvatar(
            name = person.name,
            size = AVATAR,
            // A ring, not only a colour, marks the chosen person.
            modifier = if (selected) Modifier.border(2.dp, colors.selected, GiftiaryShapes.pill) else Modifier,
        )
        ChoiceName(
            text = person.name,
            color = if (enabled) colors.textPrimary else colors.textMuted,
            bold = selected,
        )
    }
}

@Composable
private fun NewPersonChoice(enabled: Boolean, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val content = if (enabled) colors.primaryActionText else colors.textMuted
    val description = stringResource(R.string.gift_editor_new_person_description)
    ChoiceColumn(
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (enabled) onClick { onClick(); true }
            },
    ) {
        Box(
            modifier = Modifier
                .size(AVATAR)
                .giftiaryDashedOutline(content, cornerRadius = AVATAR / 2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
        }
        ChoiceName(text = stringResource(R.string.gift_editor_new_person), color = content, bold = true)
    }
}

@Composable
private fun ChoiceColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(CHOICE_WIDTH)
            .clip(GiftiaryShapes.input)
            .then(modifier)
            .padding(vertical = GiftiarySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun ChoiceName(text: String, color: Color, bold: Boolean) {
    Text(
        text = text,
        style = GiftiaryType.metadata.copy(fontSize = 11.5.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val AVATAR = 48.dp
private val CHOICE_WIDTH = 60.dp
