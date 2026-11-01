package app.giftiary.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * The search field of root screens. The query stays visible with a clear control while it holds
 * text, so a "no results" state can always be undone in place.
 */
@Composable
fun GiftiarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    GiftiaryInput(
        value = query,
        onValueChange = onQueryChange,
        // No visible label: the placeholder says what is searched.
        label = null,
        modifier = modifier,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(17.dp))
        },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                        modifier = Modifier.size(15.dp),
                        tint = GiftiaryTheme.colors.textSecondary,
                    )
                }
            }
        },
        strongBorder = query.isNotEmpty(),
    )
}
