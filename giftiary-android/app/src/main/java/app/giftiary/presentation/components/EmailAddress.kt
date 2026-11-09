package app.giftiary.presentation.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow

/**
 * An email address that stays recognisable in a narrow space. When it does not fit on one line it
 * breaks before the "@", so the domain moves to a line of its own and stays whole. Only a name too
 * long for a line of its own is shortened. Ellipsizing the whole address instead leaves something
 * like "bob@example.c…" or "some…e.com", which no longer identifies the account.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailAddress(
    email: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val at = email.lastIndexOf('@')
    val parts = if (at > 0) listOf(email.substring(0, at), email.substring(at)) else listOf(email)
    // Read and found as one address, not as its two halves.
    FlowRow(modifier = modifier.clearAndSetSemantics { text = AnnotatedString(email) }) {
        parts.forEach { part ->
            Text(
                text = part,
                style = style.copy(hyphens = Hyphens.None),
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
