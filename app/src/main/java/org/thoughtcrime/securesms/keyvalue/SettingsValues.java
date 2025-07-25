package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.webrtc.CallDataMode;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@SuppressWarnings("deprecation")
public final class SettingsValues extends SignalStoreValues {

  private static final String TAG = Log.tag(SettingsValues.class);

  public static final String LINK_PREVIEWS          = "settings.link_previews";
  public static final String KEEP_MESSAGES_DURATION = "settings.keep_messages_duration";

  public static final String PREFER_SYSTEM_CONTACT_PHOTOS = "settings.prefer.system.contact.photos";

  private static final String SIGNAL_BACKUP_DIRECTORY        = "settings.signal.backup.directory";
  private static final String SIGNAL_LATEST_BACKUP_DIRECTORY = "settings.signal.backup.directory,latest";
  private static final String CALL_DATA_MODE                 = "settings.signal.call.bandwidth.mode";

  public static final String THREAD_TRIM_LENGTH  = "pref_trim_length";
  public static final String THREAD_TRIM_ENABLED = "pref_trim_threads";

  public static final  String THEME                                   = "settings.theme";
  public static final  String MESSAGE_FONT_SIZE                       = "settings.message.font.size";
  public static final  String LANGUAGE                                = "settings.language";
  public static final  String PREFER_SYSTEM_EMOJI                     = "settings.use.system.emoji";
  public static final  String ENTER_KEY_SENDS                         = "settings.enter.key.sends";
  public static final  String BACKUPS_ENABLED                         = "settings.backups.enabled";
  public static final  String BACKUPS_SCHEDULE_HOUR                   = "settings.backups.schedule.hour";
  public static final  String BACKUPS_SCHEDULE_MINUTE                 = "settings.backups.schedule.minute";
  public static final  String SMS_DELIVERY_REPORTS_ENABLED            = "settings.sms.delivery.reports.enabled";
  public static final  String WIFI_CALLING_COMPATIBILITY_MODE_ENABLED = "settings.wifi.calling.compatibility.mode.enabled";
  public static final  String MESSAGE_NOTIFICATIONS_ENABLED           = "settings.message.notifications.enabled";
  public static final  String MESSAGE_NOTIFICATION_SOUND              = "settings.message.notifications.sound";
  public static final  String MESSAGE_VIBRATE_ENABLED                 = "settings.message.vibrate.enabled";
  public static final  String MESSAGE_LED_COLOR                       = "settings.message.led.color";
  public static final  String MESSAGE_LED_BLINK_PATTERN               = "settings.message.led.blink";
  public static final  String MESSAGE_IN_CHAT_SOUNDS_ENABLED          = "settings.message.in.chats.sounds.enabled";
  public static final  String MESSAGE_REPEAT_ALERTS                   = "settings.message.repeat.alerts";
  public static final  String MESSAGE_NOTIFICATION_PRIVACY            = "settings.message.notification.privacy";
  public static final  String CALL_NOTIFICATIONS_ENABLED              = "settings.call.notifications.enabled";
  public static final  String CALL_RINGTONE                           = "settings.call.ringtone";
  public static final  String CALL_VIBRATE_ENABLED                    = "settings.call.vibrate.enabled";
  public static final  String NOTIFY_WHEN_CONTACT_JOINS_SIGNAL        = "settings.notify.when.contact.joins.signal";
  private static final String UNIVERSAL_EXPIRE_TIMER                  = "settings.universal.expire.timer";
  private static final String SENT_MEDIA_QUALITY                      = "settings.sentMediaQuality";
  private static final String CENSORSHIP_CIRCUMVENTION_ENABLED        = "settings.censorshipCircumventionEnabled";
  private static final String KEEP_MUTED_CHATS_ARCHIVED               = "settings.keepMutedChatsArchived";
  private static final String USE_COMPACT_NAVIGATION_BAR              = "settings.useCompactNavigationBar";
  private static final String THREAD_TRIM_SYNC_TO_LINKED_DEVICES      = "settings.storage.syncThreadTrimDeletes";
  private static final String MOLLY_NOTIFICATION_METHOD               = "molly.notificationMethod";

  public static final int BACKUP_DEFAULT_HOUR   = 2;
  public static final int BACKUP_DEFAULT_MINUTE = 0;

  public static final String SHOW_REACTION_TIMESTAMPS                = "settings.fork.show.reaction.timestamps";
  public static final String FORCE_WEBSOCKET_MODE                    = "settings.fork.force.websocket.mode";
  public static final String FAST_CUSTOM_REACTION_CHANGE             = "settings.fork.fast.custom.reaction.change";
  public static final String COPY_TEXT_OPENS_POPUP                   = "settings.fork.copy.text.opens.popup";
  public static final String CONVERSATION_DELETE_IN_MENU             = "settings.conversation.delete.in.menu";
  public static final String SWIPE_TO_RIGHT_ACTION                   = "settings.swipe.to.right.action";
  public static final String RANGE_MULTI_SELECT                      = "settings.range.multi.select";
  public static final String LONG_PRESS_MULTI_SELECT                 = "settings.long.press.multi.select";
  public static final String ALSO_SHOW_PROFILE_NAME                  = "settings.also.show.profile.name";
  public static final String MANAGE_GROUP_TWEAKS                     = "settings.manage.group.tweaks";
  public static final String SWIPE_TO_LEFT_ACTION                    = "settings.swipe.to.left.action";
  public static final String TRASH_NO_PROMPT_FOR_ME                  = "settings.trash.no.prompt.for.me";
  public static final String PROMPT_MP4_AS_GIF                       = "settings.prompt.mp4.as.gif";
  public static final String BACKUP_INTERVAL_IN_DAYS                 = "settings.backup.interval.in.days";
  public static final String ALT_COLLAPSE_MEDIA_KEYBOARD             = "settings.alt.collapse.media.keyboard";
  public static final String ALT_CLOSE_MEDIA_SELECTION               = "settings.alt.close.media.selection";
  public static final String STICKER_MRU_LONG_PRESS_TO_PACK          = "settings.sticker.mru.long.press.to.pack";
  public static final String STICKER_KEYBOARD_PACK_MRU               = "settings.sticker.keyboard.pack.mru";
  public static final String DOUBLE_TAP_ACTION                       = "settings.double.tap.action";

  private final SingleLiveEvent<String> onConfigurationSettingChanged = new SingleLiveEvent<>();

  SettingsValues(@NonNull KeyValueStore store, Context context) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    final KeyValueStore store = getStore();
    if (!store.containsKey(LINK_PREVIEWS)) {
      store.beginWrite()
           .putBoolean(LINK_PREVIEWS, true)
           .apply();
    }
    if (!store.containsKey(BACKUPS_SCHEDULE_HOUR)) {
      // Initialize backup time to a 5min interval between 1-5am
      setBackupSchedule(new Random().nextInt(5) + 1, new Random().nextInt(12) * 5);
    }
    // MOLLY: These settings are saved in shared prefs too. Sync with the stored values.
    if (getStore().containsKey(THEME)) {
      setTheme(Theme.deserialize(getStore().getString(THEME, null)), isDynamicColorsEnabled());
    }
    if (getStore().containsKey(PREFER_SYSTEM_EMOJI)) {
      setPreferSystemEmoji(getStore().getBoolean(PREFER_SYSTEM_EMOJI, false));
    }
    if (getStore().containsKey(LANGUAGE)) {
      setLanguage(getStore().getString(LANGUAGE, null));
    }
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Arrays.asList(LINK_PREVIEWS,
                         KEEP_MESSAGES_DURATION,
                         PREFER_SYSTEM_CONTACT_PHOTOS,
                         CALL_DATA_MODE,
                         THREAD_TRIM_LENGTH,
                         THREAD_TRIM_ENABLED,
                         LANGUAGE,
                         THEME,
                         MESSAGE_FONT_SIZE,
                         PREFER_SYSTEM_EMOJI,
                         ENTER_KEY_SENDS,
                         BACKUPS_ENABLED,
                         MESSAGE_NOTIFICATIONS_ENABLED,
                         MESSAGE_NOTIFICATION_SOUND,
                         MESSAGE_VIBRATE_ENABLED,
                         MESSAGE_LED_COLOR,
                         MESSAGE_LED_BLINK_PATTERN,
                         MESSAGE_IN_CHAT_SOUNDS_ENABLED,
                         MESSAGE_REPEAT_ALERTS,
                         MESSAGE_NOTIFICATION_PRIVACY,
                         CALL_NOTIFICATIONS_ENABLED,
                         CALL_RINGTONE,
                         CALL_VIBRATE_ENABLED,
                         NOTIFY_WHEN_CONTACT_JOINS_SIGNAL,
                         UNIVERSAL_EXPIRE_TIMER,
                         SENT_MEDIA_QUALITY,
                         KEEP_MUTED_CHATS_ARCHIVED,
                         USE_COMPACT_NAVIGATION_BAR,
                         THREAD_TRIM_SYNC_TO_LINKED_DEVICES,
                         SHOW_REACTION_TIMESTAMPS,
                         FORCE_WEBSOCKET_MODE,
                         FAST_CUSTOM_REACTION_CHANGE,
                         COPY_TEXT_OPENS_POPUP,
                         CONVERSATION_DELETE_IN_MENU,
                         SWIPE_TO_RIGHT_ACTION,
                         RANGE_MULTI_SELECT,
                         LONG_PRESS_MULTI_SELECT,
                         ALSO_SHOW_PROFILE_NAME,
                         MANAGE_GROUP_TWEAKS,
                         SWIPE_TO_LEFT_ACTION,
                         TRASH_NO_PROMPT_FOR_ME,
                         PROMPT_MP4_AS_GIF,
                         BACKUP_INTERVAL_IN_DAYS,
                         ALT_COLLAPSE_MEDIA_KEYBOARD,
                         ALT_CLOSE_MEDIA_SELECTION,
                         STICKER_MRU_LONG_PRESS_TO_PACK,
                         STICKER_KEYBOARD_PACK_MRU,
                         DOUBLE_TAP_ACTION);
  }

  public @NonNull LiveData<String> getOnConfigurationSettingChanged() {
    return onConfigurationSettingChanged;
  }

  public boolean isLinkPreviewsEnabled() {
    return getBoolean(LINK_PREVIEWS, false);
  }

  public void setLinkPreviewsEnabled(boolean enabled) {
    putBoolean(LINK_PREVIEWS, enabled);
  }

  public @NonNull KeepMessagesDuration getKeepMessagesDuration() {
    return KeepMessagesDuration.fromId(getInteger(KEEP_MESSAGES_DURATION, 0));
  }

  public void setKeepMessagesForDuration(@NonNull KeepMessagesDuration duration) {
    putInteger(KEEP_MESSAGES_DURATION, duration.getId());
  }

  public boolean isTrimByLengthEnabled() {
    return getBoolean(THREAD_TRIM_ENABLED, false);
  }

  public void setThreadTrimByLengthEnabled(boolean enabled) {
    putBoolean(THREAD_TRIM_ENABLED, enabled);
  }

  public int getThreadTrimLength() {
    return getInteger(THREAD_TRIM_LENGTH, 500);
  }

  public void setThreadTrimLength(int length) {
    putInteger(THREAD_TRIM_LENGTH, length);
  }

  public boolean shouldSyncThreadTrimDeletes() {
    if (!getStore().containsKey(THREAD_TRIM_SYNC_TO_LINKED_DEVICES)) {
      setSyncThreadTrimDeletes(!isTrimByLengthEnabled() && getKeepMessagesDuration() == KeepMessagesDuration.FOREVER);
    }

    return getBoolean(THREAD_TRIM_SYNC_TO_LINKED_DEVICES, true);
  }

  public void setSyncThreadTrimDeletes(boolean syncDeletes) {
    putBoolean(THREAD_TRIM_SYNC_TO_LINKED_DEVICES, syncDeletes);
  }

  public void setSignalBackupDirectory(@NonNull Uri uri) {
    putString(SIGNAL_BACKUP_DIRECTORY, uri.toString());
    putString(SIGNAL_LATEST_BACKUP_DIRECTORY, uri.toString());
  }

  public void setPreferSystemContactPhotos(boolean preferSystemContactPhotos) {
    putBoolean(PREFER_SYSTEM_CONTACT_PHOTOS, preferSystemContactPhotos);
  }

  public boolean isPreferSystemContactPhotos() {
    return getBoolean(PREFER_SYSTEM_CONTACT_PHOTOS, false);
  }

  public @Nullable Uri getSignalBackupDirectory() {
    return getUri(SIGNAL_BACKUP_DIRECTORY);
  }

  public @Nullable Uri getLatestSignalBackupDirectory() {
    return getUri(SIGNAL_LATEST_BACKUP_DIRECTORY);
  }

  public void clearSignalBackupDirectory() {
    putString(SIGNAL_BACKUP_DIRECTORY, null);
  }

  public void setCallDataMode(@NonNull CallDataMode callDataMode) {
    putInteger(CALL_DATA_MODE, callDataMode.getCode());
  }

  public @NonNull CallDataMode getCallDataMode() {
    return CallDataMode.fromCode(getInteger(CALL_DATA_MODE, CallDataMode.HIGH_ALWAYS.getCode()));
  }

  public @NonNull Theme getTheme() {
    return Theme.deserialize(TextSecurePreferences.getTheme(AppDependencies.getApplication()));
  }

  public void setTheme(@NonNull Theme theme, boolean useDynamicColors) {
    final Context context = AppDependencies.getApplication();
    String serializedTheme = theme.serialize();
    putString(THEME,serializedTheme);
    // MOLLY: Store the value unencrypted to be able to read it back when app is locked
    TextSecurePreferences.setTheme(context, serializedTheme);
    TextSecurePreferences.setDynamicColorsEnabled(context, useDynamicColors);
    onConfigurationSettingChanged.postValue(THEME);
  }

  public boolean isDynamicColorsEnabled() {
    return TextSecurePreferences.isDynamicColorsEnabled(AppDependencies.getApplication());
  }

  public int getMessageFontSize() {
    return getInteger(MESSAGE_FONT_SIZE, TextSecurePreferences.getMessageBodyTextSize(AppDependencies.getApplication()));
  }

  public int getMessageQuoteFontSize(@NonNull Context context) {
    int   currentMessageSize   = getMessageFontSize();
    int[] possibleMessageSizes = context.getResources().getIntArray(R.array.pref_message_font_size_values);
    int[] possibleQuoteSizes   = context.getResources().getIntArray(R.array.pref_message_font_quote_size_values);
    int   sizeIndex            = Arrays.binarySearch(possibleMessageSizes, currentMessageSize);

    if (sizeIndex < 0) {
      int closestSizeIndex = 0;
      int closestSizeDiff  = Integer.MAX_VALUE;

      for (int i = 0; i < possibleMessageSizes.length; i++) {
        int diff = Math.abs(possibleMessageSizes[i] - currentMessageSize);
        if (diff < closestSizeDiff) {
          closestSizeIndex = i;
          closestSizeDiff  = diff;
        }
      }

      int newSize = possibleMessageSizes[closestSizeIndex];
      Log.w(TAG, "Using non-standard font size of " + currentMessageSize + ". Closest match was " + newSize + ". Updating.");

      setMessageFontSize(newSize);
      sizeIndex = Arrays.binarySearch(possibleMessageSizes, newSize);
    }

    return possibleQuoteSizes[sizeIndex];
  }

  public void setMessageFontSize(int messageFontSize) {
    putInteger(MESSAGE_FONT_SIZE, messageFontSize);
  }

  public @NonNull String getLanguage() {
    return TextSecurePreferences.getLanguage(AppDependencies.getApplication());
  }

  public void setLanguage(@NonNull String language) {
    // MOLLY: Keep language setting in SharedPreferences
    TextSecurePreferences.setLanguage(AppDependencies.getApplication(), language);
    onConfigurationSettingChanged.postValue(LANGUAGE);
  }

  public boolean isPreferSystemEmoji() {
    return TextSecurePreferences.isSystemEmojiPreferred(AppDependencies.getApplication());
  }

  public void setPreferSystemEmoji(boolean useSystemEmoji) {
    putBoolean(PREFER_SYSTEM_EMOJI, useSystemEmoji);
    // MOLLY: Store the value unencrypted to be able to read it back when app is locked
    TextSecurePreferences.setSystemEmojiPreferred(AppDependencies.getApplication(), useSystemEmoji);
  }

  public boolean isEnterKeySends() {
    return getBoolean(ENTER_KEY_SENDS, TextSecurePreferences.isEnterSendsEnabled(AppDependencies.getApplication()));
  }

  public void setEnterKeySends(boolean enterKeySends) {
    putBoolean(ENTER_KEY_SENDS, enterKeySends);
  }

  public boolean isBackupEnabled() {
    return getBoolean(BACKUPS_ENABLED, TextSecurePreferences.isBackupEnabled(AppDependencies.getApplication()));
  }

  public void setBackupEnabled(boolean backupEnabled) {
    putBoolean(BACKUPS_ENABLED, backupEnabled);
  }

  public int getBackupHour() {
    return getInteger(BACKUPS_SCHEDULE_HOUR, BACKUP_DEFAULT_HOUR);
  }

  public int getBackupMinute() {
    return getInteger(BACKUPS_SCHEDULE_MINUTE, BACKUP_DEFAULT_MINUTE);
  }

  public void setBackupSchedule(int hour, int minute) {
    putInteger(BACKUPS_SCHEDULE_HOUR, hour);
    putInteger(BACKUPS_SCHEDULE_MINUTE, minute);
  }

  public boolean isSmsDeliveryReportsEnabled() {
    return getBoolean(SMS_DELIVERY_REPORTS_ENABLED, false);
  }

  public void setSmsDeliveryReportsEnabled(boolean smsDeliveryReportsEnabled) {
    putBoolean(SMS_DELIVERY_REPORTS_ENABLED, smsDeliveryReportsEnabled);
  }

  public boolean isWifiCallingCompatibilityModeEnabled() {
    return getBoolean(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, false);
  }

  public void setWifiCallingCompatibilityModeEnabled(boolean wifiCallingCompatibilityModeEnabled) {
    putBoolean(WIFI_CALLING_COMPATIBILITY_MODE_ENABLED, wifiCallingCompatibilityModeEnabled);
  }

  public void setMessageNotificationsEnabled(boolean messageNotificationsEnabled) {
    putBoolean(MESSAGE_NOTIFICATIONS_ENABLED, messageNotificationsEnabled);
  }

  public boolean isMessageNotificationsEnabled() {
    return getBoolean(MESSAGE_NOTIFICATIONS_ENABLED, TextSecurePreferences.isNotificationsEnabled(AppDependencies.getApplication()));
  }

  public void setMessageNotificationSound(@NonNull Uri sound) {
    putString(MESSAGE_NOTIFICATION_SOUND, sound.toString());
  }

  public @NonNull Uri getMessageNotificationSound() {
    String result = getString(MESSAGE_NOTIFICATION_SOUND, TextSecurePreferences.getNotificationRingtone(AppDependencies.getApplication()).toString());

    if (result.startsWith("file:")) {
      result = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    return Uri.parse(result);
  }

  public boolean isMessageVibrateEnabled() {
    return getBoolean(MESSAGE_VIBRATE_ENABLED, TextSecurePreferences.isNotificationVibrateEnabled(AppDependencies.getApplication()));
  }

  public void setMessageVibrateEnabled(boolean messageVibrateEnabled) {
    putBoolean(MESSAGE_VIBRATE_ENABLED, messageVibrateEnabled);
  }

  public @NonNull String getMessageLedColor() {
    return getString(MESSAGE_LED_COLOR, TextSecurePreferences.getNotificationLedColor(AppDependencies.getApplication()));
  }

  public void setMessageLedColor(@NonNull String ledColor) {
    putString(MESSAGE_LED_COLOR, ledColor);
  }

  public @NonNull String getMessageLedBlinkPattern() {
    return getString(MESSAGE_LED_BLINK_PATTERN, TextSecurePreferences.getNotificationLedPattern(AppDependencies.getApplication()));
  }

  public void setMessageLedBlinkPattern(@NonNull String blinkPattern) {
    putString(MESSAGE_LED_BLINK_PATTERN, blinkPattern);
  }

  public boolean isMessageNotificationsInChatSoundsEnabled() {
    return getBoolean(MESSAGE_IN_CHAT_SOUNDS_ENABLED, TextSecurePreferences.isInThreadNotifications(AppDependencies.getApplication()));
  }

  public void setMessageNotificationsInChatSoundsEnabled(boolean inChatSoundsEnabled) {
    putBoolean(MESSAGE_IN_CHAT_SOUNDS_ENABLED, inChatSoundsEnabled);
  }

  public int getMessageNotificationsRepeatAlerts() {
    return getInteger(MESSAGE_REPEAT_ALERTS, TextSecurePreferences.getRepeatAlertsCount(AppDependencies.getApplication()));
  }

  public void setMessageNotificationsRepeatAlerts(int count) {
    putInteger(MESSAGE_REPEAT_ALERTS, count);
  }

  public @NonNull NotificationPrivacyPreference getMessageNotificationsPrivacy() {
    return new NotificationPrivacyPreference(getString(MESSAGE_NOTIFICATION_PRIVACY, TextSecurePreferences.getNotificationPrivacy(AppDependencies.getApplication()).toString()));
  }

  public void setMessageNotificationsPrivacy(@NonNull NotificationPrivacyPreference messageNotificationsPrivacy) {
    putString(MESSAGE_NOTIFICATION_PRIVACY, messageNotificationsPrivacy.toString());
  }

  public boolean isCallNotificationsEnabled() {
    return getBoolean(CALL_NOTIFICATIONS_ENABLED, TextSecurePreferences.isCallNotificationsEnabled(AppDependencies.getApplication()));
  }

  public void setCallNotificationsEnabled(boolean callNotificationsEnabled) {
    putBoolean(CALL_NOTIFICATIONS_ENABLED, callNotificationsEnabled);
  }

  public @NonNull Uri getCallRingtone() {
    String result = getString(CALL_RINGTONE, TextSecurePreferences.getCallNotificationRingtone(AppDependencies.getApplication()).toString());

    if (result != null && result.startsWith("file:")) {
      result = Settings.System.DEFAULT_RINGTONE_URI.toString();
    }

    return Uri.parse(result);
  }

  public void setCallRingtone(@NonNull Uri ringtone) {
    putString(CALL_RINGTONE, ringtone.toString());
  }

  public boolean isCallVibrateEnabled() {
    return getBoolean(CALL_VIBRATE_ENABLED, TextSecurePreferences.isCallNotificationVibrateEnabled(AppDependencies.getApplication()));
  }

  public void setCallVibrateEnabled(boolean callVibrateEnabled) {
    putBoolean(CALL_VIBRATE_ENABLED, callVibrateEnabled);
  }

  public boolean isNotifyWhenContactJoinsSignal() {
    return getBoolean(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, TextSecurePreferences.isNewContactsNotificationEnabled(AppDependencies.getApplication()));
  }

  public void setNotifyWhenContactJoinsSignal(boolean notifyWhenContactJoinsSignal) {
    putBoolean(NOTIFY_WHEN_CONTACT_JOINS_SIGNAL, notifyWhenContactJoinsSignal);
  }

  public void setUniversalExpireTimer(int seconds) {
    putInteger(UNIVERSAL_EXPIRE_TIMER, seconds);
  }

  public int getUniversalExpireTimer() {
    return getInteger(UNIVERSAL_EXPIRE_TIMER, 0);
  }

  public void setSentMediaQuality(@NonNull SentMediaQuality sentMediaQuality) {
    putInteger(SENT_MEDIA_QUALITY, sentMediaQuality.getCode());
  }

  public @NonNull SentMediaQuality getSentMediaQuality() {
    return SentMediaQuality.fromCode(getInteger(SENT_MEDIA_QUALITY, SentMediaQuality.STANDARD.getCode()));
  }

  public @NonNull CensorshipCircumventionEnabled getCensorshipCircumventionEnabled() {
    return CensorshipCircumventionEnabled.deserialize(getInteger(CENSORSHIP_CIRCUMVENTION_ENABLED, CensorshipCircumventionEnabled.DEFAULT.serialize()));
  }

  public void setCensorshipCircumventionEnabled(boolean enabled) {
    Log.i(TAG, "Changing censorship circumvention state to: " + enabled, new Throwable());
    putInteger(CENSORSHIP_CIRCUMVENTION_ENABLED, enabled ? CensorshipCircumventionEnabled.ENABLED.serialize() : CensorshipCircumventionEnabled.DISABLED.serialize());
  }

  public void setKeepMutedChatsArchived(boolean enabled) {
    putBoolean(KEEP_MUTED_CHATS_ARCHIVED, enabled);
  }

  public boolean shouldKeepMutedChatsArchived() {
    return getBoolean(KEEP_MUTED_CHATS_ARCHIVED, false);
  }

  public void setUseCompactNavigationBar(boolean enabled) {
    putBoolean(USE_COMPACT_NAVIGATION_BAR, enabled);
  }

  public boolean getUseCompactNavigationBar() {
    return getBoolean(USE_COMPACT_NAVIGATION_BAR, false);
  }

  public NotificationDeliveryMethod getPreferredNotificationMethod() {
    final NotificationDeliveryMethod method;
    if (getStore().containsKey(MOLLY_NOTIFICATION_METHOD)) {
      method = NotificationDeliveryMethod.deserialize(
          getString(MOLLY_NOTIFICATION_METHOD, NotificationDeliveryMethod.FCM.serialize())
      );
    } else {
      method = SignalStore.account().isFcmEnabled() ? NotificationDeliveryMethod.FCM
                                                    : NotificationDeliveryMethod.WEBSOCKET;
    }
    if (!BuildConfig.USE_PLAY_SERVICES && method == NotificationDeliveryMethod.FCM) {
      return NotificationDeliveryMethod.WEBSOCKET;
    }
    return method;
  }

  public void setPreferredNotificationMethod(NotificationDeliveryMethod method) {
    putString(MOLLY_NOTIFICATION_METHOD, method.serialize());
  }

  private @Nullable Uri getUri(@NonNull String key) {
    String uri = getString(key, "");

    if (TextUtils.isEmpty(uri)) {
      return null;
    } else {
      return Uri.parse(uri);
    }
  }

  public boolean isShowReactionTimestamps() {
    return getBoolean(SHOW_REACTION_TIMESTAMPS, TextSecurePreferences.isShowReactionTimeEnabled(AppDependencies.getApplication()));
  }

  public void setShowReactionTimestamps(boolean showReactionTimestamps) {
    putBoolean(SHOW_REACTION_TIMESTAMPS, showReactionTimestamps);
  }

  public boolean isForceWebsocketMode() {
    return getBoolean(FORCE_WEBSOCKET_MODE, TextSecurePreferences.isForceWebsocketMode(AppDependencies.getApplication()));
  }

  public void setForceWebsocketMode(boolean forceWebsocketMode) {
    putBoolean(FORCE_WEBSOCKET_MODE, forceWebsocketMode);
  }

  public boolean isFastCustomReactionChange() {
    return getBoolean(FAST_CUSTOM_REACTION_CHANGE, TextSecurePreferences.isFastCustomReactionChange(AppDependencies.getApplication()));
  }

  public void setFastCustomReactionChange(boolean fastCustomReactionChange) {
    putBoolean(FAST_CUSTOM_REACTION_CHANGE, fastCustomReactionChange);
  }

  public boolean isCopyTextOpensPopup() {
    return getBoolean(COPY_TEXT_OPENS_POPUP, TextSecurePreferences.isCopyTextOpensPopup(AppDependencies.getApplication()));
  }

  public void setCopyTextOpensPopup(boolean copyTextOpensPopup) {
    putBoolean(COPY_TEXT_OPENS_POPUP, copyTextOpensPopup);
  }

  public boolean isConversationDeleteInMenu() {
    return getBoolean(CONVERSATION_DELETE_IN_MENU, TextSecurePreferences.isConversationDeleteInMenu(AppDependencies.getApplication()));
  }

  public void setConversationDeleteInMenu(boolean conversationDeleteInMenu) {
    putBoolean(CONVERSATION_DELETE_IN_MENU, conversationDeleteInMenu);
  }

  public @NonNull String getSwipeToRightAction() {
    return getString(SWIPE_TO_RIGHT_ACTION, TextSecurePreferences.getSwipeToRightAction(AppDependencies.getApplication()));
  }

  public void setSwipeToRightAction(@NonNull String swipeToRightAction) {
    putString(SWIPE_TO_RIGHT_ACTION, swipeToRightAction);
  }

  public boolean isRangeMultiSelect() {
    return getBoolean(RANGE_MULTI_SELECT, TextSecurePreferences.isRangeMultiSelect(AppDependencies.getApplication()));
  }

  public void setRangeMultiSelect(boolean rangeMultiSelect) {
    putBoolean(RANGE_MULTI_SELECT, rangeMultiSelect);
  }

  public boolean isLongPressMultiSelect() {
    return getBoolean(LONG_PRESS_MULTI_SELECT, TextSecurePreferences.isLongPressMultiSelect(AppDependencies.getApplication()));
  }

  public void setLongPressMultiSelect(boolean longPressMultiSelect) {
    putBoolean(LONG_PRESS_MULTI_SELECT, longPressMultiSelect);
  }

  public boolean isAlsoShowProfileName() {
    return getBoolean(ALSO_SHOW_PROFILE_NAME, TextSecurePreferences.isAlsoShowProfileName(AppDependencies.getApplication()));
  }

  public void setAlsoShowProfileName(boolean alsoShowProfileName) {
    putBoolean(ALSO_SHOW_PROFILE_NAME, alsoShowProfileName);
  }

  public boolean isManageGroupTweaks() {
    return getBoolean(MANAGE_GROUP_TWEAKS, TextSecurePreferences.isManageGroupTweaks(AppDependencies.getApplication()));
  }

  public void setManageGroupTweaks(boolean manageGroupTweaks) {
    putBoolean(MANAGE_GROUP_TWEAKS, manageGroupTweaks);
  }

  public @NonNull String getSwipeToLeftAction() {
    return getString(SWIPE_TO_LEFT_ACTION, TextSecurePreferences.getSwipeToLeftAction(AppDependencies.getApplication()));
  }

  public void setSwipeToLeftAction(@NonNull String swipeToLeftAction) {
    putString(SWIPE_TO_LEFT_ACTION, swipeToLeftAction);
  }

  public boolean isTrashNoPromptForMe() {
    return getBoolean(TRASH_NO_PROMPT_FOR_ME, TextSecurePreferences.isTrashNoPromptForMe(AppDependencies.getApplication()));
  }

  public void setTrashNoPromptForMe(boolean trashNoPromptForMe) {
    putBoolean(TRASH_NO_PROMPT_FOR_ME, trashNoPromptForMe);
  }

  public boolean isPromptMp4AsGif() {
    return getBoolean(PROMPT_MP4_AS_GIF, TextSecurePreferences.isPromptMp4AsGif(AppDependencies.getApplication()));
  }

  public void setPromptMp4AsGif(boolean promptMp4AsGif) {
    putBoolean(PROMPT_MP4_AS_GIF, promptMp4AsGif);
  }

  public int getBackupIntervalInDays() {
    return getInteger(BACKUP_INTERVAL_IN_DAYS, TextSecurePreferences.getBackupIntervalInDays(AppDependencies.getApplication()));
  }

  public void setBackupIntervalInDays(int backupIntervalInDays) {
    putInteger(BACKUP_INTERVAL_IN_DAYS, backupIntervalInDays);
  }

  public boolean isAltCollapseMediaKeyboard() {
    return getBoolean(ALT_COLLAPSE_MEDIA_KEYBOARD, TextSecurePreferences.isAltCollapseMediaKeyboard(AppDependencies.getApplication()));
  }

  public void setAltCollapseMediaKeyboard(boolean altCollapseMediaKeyboard) {
    putBoolean(ALT_COLLAPSE_MEDIA_KEYBOARD, altCollapseMediaKeyboard);
  }

  public boolean isAltCloseMediaSelection() {
    return getBoolean(ALT_CLOSE_MEDIA_SELECTION, TextSecurePreferences.isAltCloseMediaSelection(AppDependencies.getApplication()));
  }

  public void setAltCloseMediaSelection(boolean altCloseMediaSelection) {
    putBoolean(ALT_CLOSE_MEDIA_SELECTION, altCloseMediaSelection);
  }

  public boolean isStickerMruLongPressToPack() {
    return getBoolean(STICKER_MRU_LONG_PRESS_TO_PACK, TextSecurePreferences.isStickerMruLongPressToPack(AppDependencies.getApplication()));
  }

  public void setStickerMruLongPressToPack(boolean stickerMruLongPressToPack) {
    putBoolean(STICKER_MRU_LONG_PRESS_TO_PACK, stickerMruLongPressToPack);
  }

  public boolean isStickerKeyboardPackMru() {
    return getBoolean(STICKER_KEYBOARD_PACK_MRU, TextSecurePreferences.isStickerKeyboardPackMru(AppDependencies.getApplication()));
  }

  public void setStickerKeyboardPackMru(boolean stickerKeyboardPackMru) {
    putBoolean(STICKER_KEYBOARD_PACK_MRU, stickerKeyboardPackMru);
  }

  public @NonNull String getDoubleTapAction() {
    return getString(DOUBLE_TAP_ACTION, TextSecurePreferences.getDoubleTapAction(AppDependencies.getApplication()));
  }

  public void setDoubleTapAction(@NonNull String doubleTapAction) {
    putString(DOUBLE_TAP_ACTION, doubleTapAction);
  }

  public enum CensorshipCircumventionEnabled {
    DEFAULT(0), ENABLED(1), DISABLED(2);

    private final int value;

    CensorshipCircumventionEnabled(int value) {
      this.value = value;
    }

    public static CensorshipCircumventionEnabled deserialize(int value) {
      switch (value) {
        case 0:
          return DEFAULT;
        case 1:
          return ENABLED;
        case 2:
          return DISABLED;
        default:
          throw new IllegalArgumentException("Bad value: " + value);
      }
    }

    public int serialize() {
      return value;
    }
  }

  public enum Theme {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    private final String value;

    Theme(String value) {
      this.value = value;
    }

    public @NonNull String serialize() {
      return value;
    }

    public static @NonNull Theme deserialize(@NonNull String value) {
      switch (value) {
        case "system":
          return SYSTEM;
        case "light":
          return LIGHT;
        case "dark":
          return DARK;
        default:
          throw new IllegalArgumentException("Unrecognized value " + value);
      }
    }
  }

  public enum NotificationDeliveryMethod {
    FCM, WEBSOCKET, UNIFIEDPUSH;

    public @NonNull String serialize() {
      return name();
    }

    public static @NonNull NotificationDeliveryMethod deserialize(@NonNull String value) {
      return NotificationDeliveryMethod.valueOf(value);
    }

    public @StringRes int getStringId() {
      return switch (this) {
        case FCM -> R.string.NotificationDeliveryMethod__fcm;
        case WEBSOCKET -> R.string.NotificationDeliveryMethod__websocket;
        case UNIFIEDPUSH -> R.string.NotificationDeliveryMethod__unifiedpush;
      };
    }
  }
}
