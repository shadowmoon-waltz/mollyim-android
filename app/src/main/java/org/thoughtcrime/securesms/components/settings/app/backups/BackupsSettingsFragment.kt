/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.compose.TextWithBetaLabel
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

/**
 * Top-level backups settings screen.
 */
class BackupsSettingsFragment : ComposeFragment() {

  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>

  private val viewModel: BackupsSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    checkoutLauncher = createBackupsCheckoutLauncher {
      findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    BackupsSettingsContent(
      backupsSettingsState = state,
      onNavigationClick = { requireActivity().onNavigateUp() },
      onBackupsRowClick = {
        when (state.enabledState) {
          is BackupsSettingsState.EnabledState.Active, BackupsSettingsState.EnabledState.Inactive -> {
            findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
          }

          BackupsSettingsState.EnabledState.Never -> {
            checkoutLauncher.launch(null)
          }

          else -> Unit
        }
      },
      onOnDeviceBackupsRowClick = { findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_backupsPreferenceFragment) },
      onBackupTierInternalOverrideChanged = { viewModel.onBackupTierInternalOverrideChanged(it) }
    )
  }
}

@Composable
private fun BackupsSettingsContent(
  backupsSettingsState: BackupsSettingsState,
  onNavigationClick: () -> Unit = {},
  onBackupsRowClick: () -> Unit = {},
  onOnDeviceBackupsRowClick: () -> Unit = {},
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__backups),
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      if (backupsSettingsState.showBackupTierInternalOverride) {
        item {
          Column(modifier = Modifier.padding(horizontal = dimensionResource(id = org.signal.core.ui.R.dimen.gutter))) {
            Text(
              text = "INTERNAL ONLY",
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = "Use this to override the subscription state to one of your choosing.",
              style = MaterialTheme.typography.bodyMedium
            )
            InternalBackupOverrideRow(backupsSettingsState, onBackupTierInternalOverrideChanged)
          }
          Dividers.Default()
        }
      }

      item {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__back_up_your_message_history),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
        )
      }

      item {
        when (backupsSettingsState.enabledState) {
          BackupsSettingsState.EnabledState.Loading -> {
            LoadingBackupsRow()

            OtherWaysToBackUpHeading()
          }

          BackupsSettingsState.EnabledState.Inactive -> {
            InactiveBackupsRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          is BackupsSettingsState.EnabledState.Active -> {
            ActiveBackupsRow(
              enabledState = backupsSettingsState.enabledState,
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          BackupsSettingsState.EnabledState.Never -> {
            NeverEnabledBackupsRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          BackupsSettingsState.EnabledState.Failed -> {
            WaitingForNetworkRow()
            OtherWaysToBackUpHeading()
          }

          BackupsSettingsState.EnabledState.NotAvailable -> Unit
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
          label = stringResource(R.string.RemoteBackupsSettingsFragment__save_your_backups_to),
          onClick = onOnDeviceBackupsRowClick
        )
      }
    }
  }
}

@Composable
private fun OtherWaysToBackUpHeading() {
  Dividers.Default()

  Texts.SectionHeader(
    text = stringResource(R.string.RemoteBackupsSettingsFragment__other_ways_to_backup)
  )
}

@Composable
private fun NeverEnabledBackupsRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups))

        Text(
          text = stringResource(R.string.BackupsSettingsFragment_automatic_backups_with_signals),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        Buttons.MediumTonal(
          onClick = onBackupsRowClick,
          modifier = Modifier.padding(top = 12.dp)
        ) {
          Text(
            text = stringResource(R.string.BackupsSettingsFragment_set_up)
          )
        }
      }
    }
  )
}

@Composable
private fun WaitingForNetworkRow() {
  Rows.TextRow(
    text = {
      Text(text = stringResource(R.string.RemoteBackupsSettingsFragment__waiting_for_network))
    },
    icon = {
      CircularProgressIndicator()
    }
  )
}

@Composable
private fun InactiveBackupsRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    text = {
      Column {
        TextWithBetaLabel(text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups))
        Text(
          text = stringResource(R.string.preferences_off),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    icon = {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_backup_24),
        contentDescription = stringResource(R.string.preferences_chats__backups),
        tint = MaterialTheme.colorScheme.onSurface
      )
    },
    onClick = onBackupsRowClick
  )
}

@Composable
private fun ActiveBackupsRow(
  enabledState: BackupsSettingsState.EnabledState.Active,
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups))

        when (enabledState.type) {
          is MessageBackupsType.Paid -> {
            Text(
              text = stringResource(
                R.string.BackupsSettingsFragment_s_month_renews_s,
                FiatMoneyUtil.format(LocalContext.current.resources, enabledState.type.pricePerMonth),
                DateUtils.formatDateWithYear(Locale.getDefault(), enabledState.expiresAt.inWholeMilliseconds)
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium
            )
          }

          is MessageBackupsType.Free -> {
            Text(
              text = stringResource(
                R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }

        Text(
          text = stringResource(
            R.string.BackupsSettingsFragment_last_backup_s,
            DateUtils.getDatelessRelativeTimeSpanFormattedDate(
              LocalContext.current,
              Locale.getDefault(),
              enabledState.lastBackupAt.inWholeMilliseconds
            ).value
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    },
    onClick = onBackupsRowClick
  )
}

@Composable
private fun LoadingBackupsRow() {
  Box(
    contentAlignment = Alignment.CenterStart,
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp)
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun InternalBackupOverrideRow(
  backupsSettingsState: BackupsSettingsState,
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  val options = remember {
    mapOf(
      "Unset" to null,
      "Free" to MessageBackupTier.FREE,
      "Paid" to MessageBackupTier.PAID
    )
  }

  Row(verticalAlignment = Alignment.CenterVertically) {
    options.forEach { option ->
      RadioButton(
        selected = option.value == backupsSettingsState.backupTierInternalOverride,
        onClick = { onBackupTierInternalOverrideChanged(option.value) }
      )
      Text(option.key)
    }
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentPreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        enabledState = BackupsSettingsState.EnabledState.Active(
          type = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD")),
            storageAllowanceBytes = 1_000_000,
            mediaTtl = 30.days
          ),
          expiresAt = 0.seconds,
          lastBackupAt = 0.seconds
        )
      )
    )
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentNotAvailablePreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        enabledState = BackupsSettingsState.EnabledState.NotAvailable
      )
    )
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentBackupTierInternalOverridePreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        enabledState = BackupsSettingsState.EnabledState.Never,
        showBackupTierInternalOverride = true,
        backupTierInternalOverride = null
      )
    )
  }
}

@SignalPreview
@Composable
private fun WaitingForNetworkRowPreview() {
  Previews.Preview {
    WaitingForNetworkRow()
  }
}

@SignalPreview
@Composable
private fun InactiveBackupsRowPreview() {
  Previews.Preview {
    InactiveBackupsRow()
  }
}

@SignalPreview
@Composable
private fun ActivePaidBackupsRowPreview() {
  Previews.Preview {
    ActiveBackupsRow(
      enabledState = BackupsSettingsState.EnabledState.Active(
        type = MessageBackupsType.Paid(
          pricePerMonth = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD")),
          storageAllowanceBytes = 1_000_000,
          mediaTtl = 30.days
        ),
        expiresAt = 0.seconds,
        lastBackupAt = 0.seconds
      )
    )
  }
}

@SignalPreview
@Composable
private fun ActiveFreeBackupsRowPreview() {
  Previews.Preview {
    ActiveBackupsRow(
      enabledState = BackupsSettingsState.EnabledState.Active(
        type = MessageBackupsType.Free(
          mediaRetentionDays = 30
        ),
        expiresAt = 0.seconds,
        lastBackupAt = 0.seconds
      )
    )
  }
}

@SignalPreview
@Composable
private fun LoadingBackupsRowPreview() {
  Previews.Preview {
    LoadingBackupsRow()
  }
}

@SignalPreview
@Composable
private fun NeverEnabledBackupsRowPreview() {
  Previews.Preview {
    NeverEnabledBackupsRow()
  }
}
