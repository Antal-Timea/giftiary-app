package app.gifty.presentation.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.gifty.presentation.theme.GiftyShapes
import app.gifty.presentation.theme.GiftyTheme

/**
 * Single-line Gifty input. An [errorMessage] puts the field in its error state and is shown
 * below it, so errors are never communicated by color alone.
 */
@Composable
fun GiftyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = errorMessage?.let { { Text(it) } },
        isError = errorMessage != null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = GiftyShapes.input,
        colors = giftyTextFieldColors(),
    )
}

/** Shared by every outlined Gifty input so text fields and dropdowns look identical. */
@Composable
internal fun giftyTextFieldColors(): TextFieldColors {
    val colors = GiftyTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textMuted,
        errorTextColor = colors.textPrimary,
        focusedContainerColor = colors.inputSurface,
        unfocusedContainerColor = colors.inputSurface,
        disabledContainerColor = colors.inputSurface,
        errorContainerColor = colors.inputSurface,
        cursorColor = colors.inputFocused,
        errorCursorColor = colors.error,
        selectionColors = TextSelectionColors(
            handleColor = colors.inputFocused,
            backgroundColor = colors.inputFocused.copy(alpha = 0.3f),
        ),
        focusedBorderColor = colors.inputFocused,
        unfocusedBorderColor = colors.border,
        disabledBorderColor = colors.divider,
        errorBorderColor = colors.error,
        focusedLabelColor = colors.inputFocused,
        unfocusedLabelColor = colors.textLabel,
        disabledLabelColor = colors.textMuted,
        errorLabelColor = colors.error,
        focusedPlaceholderColor = colors.placeholder,
        unfocusedPlaceholderColor = colors.placeholder,
        disabledPlaceholderColor = colors.placeholder,
        errorSupportingTextColor = colors.error,
    )
}
