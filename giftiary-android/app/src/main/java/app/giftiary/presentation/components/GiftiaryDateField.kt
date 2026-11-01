package app.giftiary.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.giftiary.R
import app.giftiary.presentation.occasions.OccasionDateFormatter
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Shows a full calendar date and opens a date picker. Only for dates that always have a year,
 * such as when a gift was given; occasions may lack one and use separate fields instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftiaryDateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource, enabled) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release && enabled) picking = true }
    }
    GiftiaryInput(
        value = OccasionDateFormatter.format(date.dayOfMonth, date.monthValue, date.year, LocalConfiguration.current.locales[0]),
        onValueChange = {},
        label = label,
        modifier = modifier,
        enabled = enabled,
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { picking = true }, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = stringResource(R.string.gift_choose_date),
                )
            }
        },
        interactionSource = interactionSource,
    )

    if (picking) {
        val colors = GiftiaryTheme.colors
        // The picker works in UTC milliseconds; converting at UTC keeps the calendar date unchanged.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val pickerColors = DatePickerDefaults.colors(
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textSecondary,
            headlineContentColor = colors.textPrimary,
            weekdayContentColor = colors.textSecondary,
            dayContentColor = colors.textPrimary,
            selectedDayContainerColor = colors.primaryAction,
            selectedDayContentColor = colors.onPrimaryAction,
            todayContentColor = colors.textPrimary,
            todayDateBorderColor = colors.primaryAction,
            selectedYearContainerColor = colors.primaryAction,
            selectedYearContentColor = colors.onPrimaryAction,
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        picking = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                ) {
                    Text(stringResource(R.string.gift_date_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { picking = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            colors = pickerColors,
        ) {
            DatePicker(state = pickerState, colors = pickerColors)
        }
    }
}
