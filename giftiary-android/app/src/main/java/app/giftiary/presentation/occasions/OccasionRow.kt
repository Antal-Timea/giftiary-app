package app.giftiary.presentation.occasions

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** An occasion in a list, by its title and date; [clickLabel] says what tapping it does. */
@Composable
fun OccasionRow(occasion: Occasion, onClick: () -> Unit, @StringRes clickLabel: Int = R.string.people_edit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(clickLabel), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(
            text = occasionTitle(occasion),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        val date = occasionDate(occasion)
        Text(
            text = if (occasion.repeatsYearly) date else stringResource(R.string.occasion_date_one_time, date),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
