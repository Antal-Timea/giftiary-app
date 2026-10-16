package app.giftiary.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.model.NotificationAccess
import app.giftiary.domain.repository.OccasionRepository
import app.giftiary.domain.repository.ReminderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Home card for daily reminders. What the system allows can change while Giftiary is in the
 * background, e.g. in its notification settings, so it is read again on every return ([onResume]).
 */
@HiltViewModel
class ReminderCardViewModel @Inject constructor(
    occasionRepository: OccasionRepository,
    private val settings: ReminderSettingsRepository,
) : ViewModel() {

    private val setting = MutableStateFlow(readSetting())

    val state: StateFlow<ReminderCardState> = combine(
        occasionRepository.observeOccasions().map { it.isNotEmpty() }.catch { emit(false) },
        setting,
    ) { hasOccasions, current -> cardState(hasOccasions, current) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReminderCardState.Hidden,
        )

    fun onResume() {
        setting.value = readSetting()
    }

    /** True if Android must now be asked for permission to notify; only ever from this tap. */
    fun onTurnOn(): Boolean {
        settings.setEnabled(true)
        onResume()
        return setting.value.access == NotificationAccess.PERMISSION_MISSING
    }

    fun onTurnOff() {
        settings.setEnabled(false)
        onResume()
    }

    /** Whatever was answered, the state is read from the system again rather than inferred. */
    fun onPermissionResult() = onResume()

    private fun readSetting() = Setting(settings.isEnabled(), settings.notificationAccess())

    private fun cardState(hasOccasions: Boolean, setting: Setting): ReminderCardState = when (setting.enabled) {
        null -> ReminderCardState.Hidden
        // Nothing to remind of yet, so nothing to offer; reminders already on can still be turned off.
        false -> if (hasOccasions) ReminderCardState.Off else ReminderCardState.Hidden
        true -> when (setting.access) {
            NotificationAccess.GRANTED -> ReminderCardState.On
            NotificationAccess.PERMISSION_MISSING -> ReminderCardState.NeedsPermission
            NotificationAccess.BLOCKED -> ReminderCardState.Blocked
        }
    }

    private data class Setting(val enabled: Boolean?, val access: NotificationAccess)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

enum class ReminderCardState {
    Hidden,
    Off,
    On,

    /** Turned on, but Android has not been allowed to show them. */
    NeedsPermission,

    /** Turned on, but notifications are switched off in the system settings. */
    Blocked,
}
