package org.thoughtcrime.securesms.components.settings.app.fork

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ForkSettingsFragment : DSLSettingsFragment(R.string.preferences__fork_specific) {

  private lateinit var viewModel: ForkSettingsViewModel

  private val swipeToRightActionLabels by lazy { resources.getStringArray(R.array.ForkSettingsFragment__swipe_to_right_action__entries) }
  private val swipeToRightActionValues by lazy { resources.getStringArray(R.array.ForkSettingsFragment__swipe_to_right_action__values) }

  private val swipeToLeftActionLabels by lazy { resources.getStringArray(R.array.ForkSettingsFragment__swipe_to_left_action__entries) }
  private val swipeToLeftActionValues by lazy { resources.getStringArray(R.array.ForkSettingsFragment__swipe_to_left_action__values) }

  private val doubleTapActionLabels by lazy { resources.getStringArray(R.array.ForkSettingsFragment__double_tap_action__entries) }
  private val doubleTapActionValues by lazy { resources.getStringArray(R.array.ForkSettingsFragment__double_tap_action__values) }

  override fun bindAdapter(adapter: MappingAdapter) {
    viewModel = ViewModelProvider(this)[ForkSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: ForkSettingsState): DSLConfiguration {
    return configure {
      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__show_reaction_timestamps),
        isChecked = state.showReactionTimestamps,
        onClick = {
          TextSecurePreferences.setShowReactionTimeEnabled(requireContext(), !state.showReactionTimestamps)
          viewModel.setShowReactionTimestamps(!state.showReactionTimestamps)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__force_websocket_mode),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__force_websocket_mode_summary),
        isChecked = state.forceWebsocketMode,
        onClick = {
          TextSecurePreferences.setForceWebsocketMode(requireContext(), !state.forceWebsocketMode)
          viewModel.setForceWebsocketMode(!state.forceWebsocketMode)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__view_set_identity_keys),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_forkSettingsFragment_to_setIdentityKeysFragment)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__fast_custom_reaction_change),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__fast_custom_reaction_change_summary),
        isChecked = state.fastCustomReactionChange,
        onClick = {
          TextSecurePreferences.setFastCustomReactionChange(requireContext(), !state.fastCustomReactionChange)
          viewModel.setFastCustomReactionChange(!state.fastCustomReactionChange)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__copy_text_opens_popup),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__copy_text_opens_popup_summary),
        isChecked = state.copyTextOpensPopup,
        onClick = {
          TextSecurePreferences.setCopyTextOpensPopup(requireContext(), !state.copyTextOpensPopup)
          viewModel.setCopyTextOpensPopup(!state.copyTextOpensPopup)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__conversation_delete_in_menu),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__conversation_delete_in_menu_summary),
        isChecked = state.conversationDeleteInMenu,
        onClick = {
          TextSecurePreferences.setConversationDeleteInMenu(requireContext(), !state.conversationDeleteInMenu)
          viewModel.setConversationDeleteInMenu(!state.conversationDeleteInMenu)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__swipe_to_right_action),
        listItems = swipeToRightActionLabels,
        selected = swipeToRightActionValues.indexOf(state.swipeToRightAction),
        onSelected = {
          TextSecurePreferences.setSwipeToRightAction(requireContext(), swipeToRightActionValues[it])
          viewModel.setSwipeToRightAction(swipeToRightActionValues[it])
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__range_multi_select),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__range_multi_select_summary),
        isChecked = state.rangeMultiSelect,
        onClick = {
          TextSecurePreferences.setRangeMultiSelect(requireContext(), !state.rangeMultiSelect)
          viewModel.setRangeMultiSelect(!state.rangeMultiSelect)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__long_press_multi_select),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__long_press_multi_select_summary),
        isChecked = state.longPressMultiSelect,
        onClick = {
          TextSecurePreferences.setLongPressMultiSelect(requireContext(), !state.longPressMultiSelect)
          viewModel.setLongPressMultiSelect(!state.longPressMultiSelect)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__also_show_profile_name),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__also_show_profile_name_summary),
        isChecked = state.alsoShowProfileName,
        onClick = {
          TextSecurePreferences.setAlsoShowProfileName(requireContext(), !state.alsoShowProfileName)
          viewModel.setAlsoShowProfileName(!state.alsoShowProfileName)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__manage_group_tweaks),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__manage_group_tweaks_summary),
        isChecked = state.manageGroupTweaks,
        onClick = {
          TextSecurePreferences.setManageGroupTweaks(requireContext(), !state.manageGroupTweaks)
          viewModel.setManageGroupTweaks(!state.manageGroupTweaks)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__swipe_to_left_action),
        listItems = swipeToLeftActionLabels,
        selected = swipeToLeftActionValues.indexOf(state.swipeToLeftAction),
        onSelected = {
          TextSecurePreferences.setSwipeToLeftAction(requireContext(), swipeToLeftActionValues[it])
          viewModel.setSwipeToLeftAction(swipeToLeftActionValues[it])
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__trash_no_prompt_for_me),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__trash_no_prompt_for_me_summary),
        isChecked = state.trashNoPromptForMe,
        onClick = {
          TextSecurePreferences.setTrashNoPromptForMe(requireContext(), !state.trashNoPromptForMe)
          viewModel.setTrashNoPromptForMe(!state.trashNoPromptForMe)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__prompt_mp4_as_gif),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__prompt_mp4_as_gif_summary),
        isChecked = state.promptMp4AsGif,
        onClick = {
          TextSecurePreferences.setPromptMp4AsGif(requireContext(), !state.promptMp4AsGif)
          viewModel.setPromptMp4AsGif(!state.promptMp4AsGif)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.fork__backup_interval),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__backup_interval_in_days_summary),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_forkSettingsFragment_to_backupsPreferenceFragment)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__alt_collapse_media_keyboard),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__alt_collapse_media_keyboard_summary),
        isChecked = state.altCollapseMediaKeyboard,
        onClick = {
          TextSecurePreferences.setAltCollapseMediaKeyboard(requireContext(), !state.altCollapseMediaKeyboard)
          viewModel.setAltCollapseMediaKeyboard(!state.altCollapseMediaKeyboard)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__alt_close_media_selection),
        summary = DSLSettingsText.from(R.string.ForkSettingsFragment__alt_close_media_selection_summary),
        isChecked = state.altCloseMediaSelection,
        onClick = {
          TextSecurePreferences.setAltCloseMediaSelection(requireContext(), !state.altCloseMediaSelection)
          viewModel.setAltCloseMediaSelection(!state.altCloseMediaSelection)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__sticker_mru_long_press_to_pack),
        isChecked = state.stickerMruLongPressToPack,
        onClick = {
          TextSecurePreferences.setStickerMruLongPressToPack(requireContext(), !state.stickerMruLongPressToPack)
          viewModel.setStickerMruLongPressToPack(!state.stickerMruLongPressToPack)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__sticker_keyboard_pack_mru),
        isChecked = state.stickerKeyboardPackMru,
        onClick = {
          TextSecurePreferences.setStickerKeyboardPackMru(requireContext(), !state.stickerKeyboardPackMru)
          viewModel.setStickerKeyboardPackMru(!state.stickerKeyboardPackMru)
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.ForkSettingsFragment__double_tap_action),
        listItems = doubleTapActionLabels,
        selected = doubleTapActionValues.indexOf(state.doubleTapAction),
        onSelected = {
          TextSecurePreferences.setDoubleTapAction(requireContext(), doubleTapActionValues[it])
          viewModel.setDoubleTapAction(doubleTapActionValues[it])
        }
      )
    }
  }
}
