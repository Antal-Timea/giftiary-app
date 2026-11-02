package app.giftiary.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * Giftiary input, single-line unless [singleLine] is off. The label sits above the field, as in
 * the approved forms; a null [label] is for a field a heading right above already names, which
 * the caller then gives that name in semantics. An [errorMessage] gives the field a coral border
 * and is written below it with a mark, so errors are never communicated by color alone.
 */
@Composable
fun GiftiaryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    suffix: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    // Decorative, as the label names the field, e.g. a link mark.
    leadingIcon: Painter? = null,
) {
    GiftiaryInput(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder,
        errorMessage = errorMessage,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        suffix = suffix?.let { { Text(it, style = GiftiaryType.fieldValue) } },
        leadingIcon = leadingIcon?.let { { Icon(painter = it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
        visualTransformation = visualTransformation,
    )
}

/**
 * The bordered white field of the design system: radius 16, a 1.5dp border that turns teal on
 * focus and coral on error, at least 48dp tall. [strongBorder] deepens the resting border for a
 * value that can be cleared.
 *
 * The [label] above and the [errorMessage] below are drawn inside the text field's decoration, so
 * screen readers announce them with the field, as they do Material's floating label.
 */
@Composable
internal fun GiftiaryInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    strongBorder: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val fieldColors = giftiaryTextFieldColors(strongBorder)
    val isError = errorMessage != null
    val textStyle = if (singleLine) GiftiaryType.fieldValue else GiftiaryType.note
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (errorMessage != null) error(errorMessage) },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = if (enabled) GiftiaryTheme.colors.textPrimary else GiftiaryTheme.colors.textMuted),
        cursorBrush = SolidColor(if (isError) GiftiaryTheme.colors.error else GiftiaryTheme.colors.caret),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
    ) { innerTextField ->
        // Column drops the minimum width, so each level fills explicitly; else the field shrinks to its text.
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            if (label != null) {
                Text(text = label, style = GiftiaryType.label, color = GiftiaryTheme.colors.textLabel)
            }
            Box(Modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 64.dp), propagateMinConstraints = true) {
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    placeholder = placeholder?.let { { Text(it, style = GiftiaryType.body) } },
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    suffix = suffix,
                    colors = fieldColors,
                    contentPadding = PaddingValues(horizontal = GiftiarySpacing.l, vertical = 13.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = fieldColors,
                            shape = GiftiaryShapes.input,
                            focusedBorderThickness = 1.5.dp,
                            unfocusedBorderThickness = 1.5.dp,
                        )
                    },
                )
            }
            if (errorMessage != null) {
                FieldError(errorMessage)
            }
        }
    }
}

@Composable
internal fun FieldError(message: String) {
    val colors = GiftiaryTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp).size(13.dp),
            tint = colors.textPrimary,
        )
        Text(text = message, style = GiftiaryType.metadata.copy(fontSize = 11.5.sp), color = colors.textPrimary)
    }
}

/** Shared by every Giftiary input so text fields, selectors and the search field look identical. */
@Composable
internal fun giftiaryTextFieldColors(strongBorder: Boolean = false): TextFieldColors {
    val colors = GiftiaryTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textMuted,
        errorTextColor = colors.textPrimary,
        focusedContainerColor = colors.inputSurface,
        unfocusedContainerColor = colors.inputSurface,
        disabledContainerColor = colors.inputSurface,
        errorContainerColor = colors.inputSurface,
        cursorColor = colors.caret,
        errorCursorColor = colors.error,
        selectionColors = TextSelectionColors(
            handleColor = colors.inputFocused,
            backgroundColor = colors.inputFocused.copy(alpha = 0.3f),
        ),
        focusedBorderColor = colors.inputFocused,
        unfocusedBorderColor = if (strongBorder) colors.borderStrong else colors.border,
        disabledBorderColor = colors.divider,
        errorBorderColor = colors.error,
        focusedLeadingIconColor = colors.textSecondary,
        unfocusedLeadingIconColor = colors.textSecondary,
        focusedTrailingIconColor = colors.placeholder,
        unfocusedTrailingIconColor = colors.placeholder,
        focusedPlaceholderColor = colors.placeholder,
        unfocusedPlaceholderColor = colors.placeholder,
        disabledPlaceholderColor = colors.placeholder,
        focusedSuffixColor = colors.textSecondary,
        unfocusedSuffixColor = colors.textSecondary,
    )
}
