package org.thoughtcrime.securesms.components.settings.app.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class AccountSettingsViewModel : ViewModel() {
  private val store: Store<AccountSettingsState> = Store(getCurrentState())

  val state: LiveData<AccountSettingsState> = store.stateLiveData

  fun refreshState() {
    store.update { getCurrentState() }
  }

  private fun getCurrentState(): AccountSettingsState {
    return AccountSettingsState(
      isLinkedDevice = SignalStore.account.isLinkedDevice,
      hasPin = SignalStore.svr.hasPin() && !SignalStore.svr.hasOptedOut(),
      hasRestoredAep = SignalStore.account.restoredAccountEntropyPool,
      pinRemindersEnabled = SignalStore.pin.arePinRemindersEnabled() && SignalStore.svr.hasPin(),
      registrationLockEnabled = SignalStore.svr.isRegistrationLockEnabled,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      clientDeprecated = SignalStore.misc.isClientDeprecated
    )
  }
}
