/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import androidx.media3.common.MediaItem;

import com.bumptech.glide.RequestManager;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.Colorizable;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Playable;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ProjectionList;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter that renders a conversation.
 *
 * Important spacial thing to keep in mind: The adapter is intended to be shown on a reversed layout
 * manager, so position 0 is at the bottom of the screen. That's why the "header" is at the bottom,
 * the "footer" is at the top, and we refer to the "next" record as having a lower index.
 */
public class ConversationAdapter
    extends ListAdapter<ConversationMessage, RecyclerView.ViewHolder>
    implements StickyHeaderDecoration.StickyHeaderAdapter<ConversationAdapter.StickyHeaderViewHolder>,
               ConversationAdapterBridge
{

  private static final String TAG = Log.tag(ConversationAdapter.class);

  public static final int HEADER_TYPE_POPOVER_DATE = 1;
  public static final int HEADER_TYPE_INLINE_DATE  = 2;
  public static final int HEADER_TYPE_LAST_SEEN    = 3;

  private static final int MESSAGE_TYPE_OUTGOING_MULTIMEDIA = 0;
  private static final int MESSAGE_TYPE_OUTGOING_TEXT       = 1;
  private static final int MESSAGE_TYPE_INCOMING_MULTIMEDIA = 2;
  private static final int MESSAGE_TYPE_INCOMING_TEXT       = 3;
  private static final int MESSAGE_TYPE_UPDATE              = 4;
  private static final int MESSAGE_TYPE_HEADER              = 5;
  public  static final int MESSAGE_TYPE_FOOTER              = 6;
  private static final int MESSAGE_TYPE_PLACEHOLDER         = 7;

  private final ItemClickListener clickListener;
  private final Context           context;
  private final LifecycleOwner    lifecycleOwner;
  private final RequestManager    requestManager;
  private final Locale            locale;
  private final Set<MultiselectPart>         selected;
  private final Calendar                     calendar;

  private String                      searchQuery;
  private ConversationMessage         recordToPulse;
  private View                        typingView;
  private View                        footerView;
  private PagingController            pagingController;
  private boolean                     hasWallpaper;
  private boolean                     isMessageRequestAccepted;
  private ConversationMessage         inlineContent;
  private Colorizer                   colorizer;
  private boolean                     isTypingViewEnabled;
  private ConversationItemDisplayMode displayMode;
  private PulseRequest                pulseRequest;

  private ConversationMessage mostRecentSelected;

  public ConversationAdapter(@NonNull Context context,
                      @NonNull LifecycleOwner lifecycleOwner,
                      @NonNull RequestManager requestManager,
                      @NonNull Locale locale,
                      @Nullable ItemClickListener clickListener,
                      boolean hasWallpaper,
                      @NonNull Colorizer colorizer)
  {
    super(new DiffUtil.ItemCallback<ConversationMessage>() {
      @Override
      public boolean areItemsTheSame(@NonNull ConversationMessage oldItem, @NonNull ConversationMessage newItem) {
        return oldItem.getMessageRecord().getId() == newItem.getMessageRecord().getId();
      }

      @Override
      public boolean areContentsTheSame(@NonNull ConversationMessage oldItem, @NonNull ConversationMessage newItem) {
        return false;
      }
    });

    this.lifecycleOwner               = lifecycleOwner;
    this.context                      = context;

    this.requestManager               = requestManager;
    this.locale                       = locale;
    this.clickListener                = clickListener;
    this.selected                     = new HashSet<>();
    this.calendar                     = Calendar.getInstance();
    this.hasWallpaper                 = hasWallpaper;
    this.isMessageRequestAccepted     = true;
    this.colorizer                    = colorizer;

    this.mostRecentSelected           = null;
  }

  @Override
  public int getItemViewType(int position) {
    if (isTypingViewEnabled() && position == 0) {
      return MESSAGE_TYPE_HEADER;
    }

    if (hasFooter() && position == getItemCount() - 1) {
      return MESSAGE_TYPE_FOOTER;
    }

    ConversationMessage conversationMessage = getItem(position);
    MessageRecord       messageRecord       = (conversationMessage != null) ? conversationMessage.getMessageRecord() : null;

    if (messageRecord == null) {
      return MESSAGE_TYPE_PLACEHOLDER;
    } else if (messageRecord.isUpdate()) {
      return MESSAGE_TYPE_UPDATE;
    } else if (messageRecord.isOutgoing()) {
      return conversationMessage.isTextOnly(context) ? MESSAGE_TYPE_OUTGOING_TEXT : MESSAGE_TYPE_OUTGOING_MULTIMEDIA;
    } else {
      return conversationMessage.isTextOnly(context) ? MESSAGE_TYPE_INCOMING_TEXT : MESSAGE_TYPE_INCOMING_MULTIMEDIA;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public @NonNull RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_INCOMING_TEXT:
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
      case MESSAGE_TYPE_OUTGOING_TEXT:
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
      case MESSAGE_TYPE_UPDATE:
        View                          itemView        = CachedInflater.from(parent.getContext()).inflate(getLayoutForViewType(viewType), parent, false);
        BindableConversationItem      bindable        = (BindableConversationItem) itemView;

        itemView.setOnClickListener((v) -> {
          if (clickListener != null) {
            clickListener.onItemClick(bindable.getMultiselectPartForLatestTouch());
          }
        });

        itemView.setOnLongClickListener((v) -> {
          if (clickListener != null) {
            clickListener.onItemLongClick(itemView, bindable.getMultiselectPartForLatestTouch());
          }

          return true;
        });

        bindable.setEventListener(clickListener);

        return new ConversationViewHolder(itemView);
      case MESSAGE_TYPE_PLACEHOLDER:
        View v = new FrameLayout(parent.getContext());
        v.setLayoutParams(new FrameLayout.LayoutParams(1, ViewUtil.dpToPx(100)));
        return new PlaceholderViewHolder(v);
      case MESSAGE_TYPE_HEADER:
        return new HeaderViewHolder(CachedInflater.from(parent.getContext()).inflate(R.layout.cursor_adapter_header_footer_view, parent, false));
      case MESSAGE_TYPE_FOOTER:
        return new FooterViewHolder(CachedInflater.from(parent.getContext()).inflate(R.layout.cursor_adapter_header_footer_view, parent, false));
      default:
        throw new IllegalStateException("Cannot create viewholder for type: " + viewType);
    }
  }

  private boolean containsValidPayload(@NonNull List<Object> payloads) {
    return payloads.contains(PAYLOAD_TIMESTAMP) || payloads.contains(PAYLOAD_NAME_COLORS) || payloads.contains(PAYLOAD_SELECTED);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (containsValidPayload(payloads)) {
      switch (getItemViewType(position)) {
        case MESSAGE_TYPE_INCOMING_TEXT:
        case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
        case MESSAGE_TYPE_OUTGOING_TEXT:
        case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
        case MESSAGE_TYPE_UPDATE:
          ConversationViewHolder conversationViewHolder = (ConversationViewHolder) holder;
          if (payloads.contains(PAYLOAD_TIMESTAMP)) {
            conversationViewHolder.getBindable().updateTimestamps();
          }

          if (payloads.contains(PAYLOAD_NAME_COLORS)) {
            conversationViewHolder.getBindable().updateContactNameColor();
          }

          if (payloads.contains(PAYLOAD_SELECTED)) {
            conversationViewHolder.getBindable().updateSelectedState();
          }

        default:
          return;
      }
    } else {
      super.onBindViewHolder(holder, position, payloads);
    }
  }

  public void setCondensedMode(ConversationItemDisplayMode condensedMode) {
    this.displayMode = condensedMode;
    notifyDataSetChanged();
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    switch (getItemViewType(position)) {
      case MESSAGE_TYPE_INCOMING_TEXT:
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA:
      case MESSAGE_TYPE_OUTGOING_TEXT:
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA:
      case MESSAGE_TYPE_UPDATE:
        ConversationViewHolder conversationViewHolder = (ConversationViewHolder) holder;
        ConversationMessage    conversationMessage    = Objects.requireNonNull(getItem(position));
        int                    adapterPosition        = holder.getAdapterPosition();

        ConversationMessage previousMessage = adapterPosition < getItemCount() - 1  && !isFooterPosition(adapterPosition + 1) ? getItem(adapterPosition + 1) : null;
        ConversationMessage nextMessage     = adapterPosition > 0                   && !isHeaderPosition(adapterPosition - 1) ? getItem(adapterPosition - 1) : null;

        ConversationItemDisplayMode itemDisplayMode = displayMode != null ? displayMode : ConversationItemDisplayMode.Standard.INSTANCE;

        conversationViewHolder.getBindable().bind(lifecycleOwner,
                                                  conversationMessage,
                                                  Optional.ofNullable(previousMessage != null ? previousMessage.getMessageRecord() : null),
                                                  Optional.ofNullable(nextMessage != null ? nextMessage.getMessageRecord() : null),
                                                  requestManager,
                                                  locale,
                                                  selected,
                                                  conversationMessage.getThreadRecipient(),
                                                  searchQuery,
                                                  conversationMessage == recordToPulse,
                                                  hasWallpaper && itemDisplayMode.displayWallpaper(),
                                                  isMessageRequestAccepted,
                                                  conversationMessage == inlineContent,
                                                  colorizer,
                                                  itemDisplayMode);

        if (conversationMessage == recordToPulse) {
          recordToPulse = null;
        }
        break;
      case MESSAGE_TYPE_HEADER:
        ((HeaderViewHolder) holder).bind(typingView);
        break;
      case MESSAGE_TYPE_FOOTER:
        ((HeaderFooterViewHolder) holder).bind(footerView);
        break;
    }
  }

  @Override
  public int getItemCount() {
    boolean hasFooter = footerView != null;
    return super.getItemCount() + (isTypingViewEnabled ? 1 : 0) + (hasFooter ? 1 : 0);
  }

  @Override
  public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
    if (holder instanceof ConversationViewHolder) {
      ((ConversationViewHolder) holder).getBindable().unbind();
    }
  }

  @Override
  public long getHeaderId(int position) {
    if (isHeaderPosition(position)) return -1;
    if (isFooterPosition(position)) return -1;
    if (position >= getItemCount()) return -1;
    if (position < 0)               return -1;

    ConversationMessage conversationMessage = getItem(position);

    if (conversationMessage == null) return -1;

    if (displayMode.getScheduleMessageMode()) {
      calendar.setTimeInMillis(((MmsMessageRecord) conversationMessage.getMessageRecord()).getScheduledDate());
    } else if (displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE) {
      calendar.setTimeInMillis(conversationMessage.getMessageRecord().getDateSent());
    } else {
      calendar.setTimeInMillis(conversationMessage.getConversationTimestamp());
    }
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
  }

  @Override
  public StickyHeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position, int type) {
    return new StickyHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(StickyHeaderViewHolder viewHolder, int position, int type) {
    Context             context             = viewHolder.itemView.getContext();
    ConversationMessage conversationMessage = Objects.requireNonNull(getItem(position));

    if (displayMode.getScheduleMessageMode()) {
      viewHolder.setText(DateUtils.getScheduledMessagesDateHeaderString(viewHolder.itemView.getContext(), locale, ((MmsMessageRecord) conversationMessage.getMessageRecord()).getScheduledDate()));
    } else if (displayMode == ConversationItemDisplayMode.EditHistory.INSTANCE) {
      viewHolder.setText(DateUtils.getConversationDateHeaderString(viewHolder.itemView.getContext(), locale, conversationMessage.getMessageRecord().getDateSent()));
    } else {
      viewHolder.setText(DateUtils.getConversationDateHeaderString(viewHolder.itemView.getContext(), locale, conversationMessage.getConversationTimestamp()));
    }

    if (type == HEADER_TYPE_POPOVER_DATE) {
      if (hasWallpaper) {
        viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_18);
      } else {
        viewHolder.setBackgroundRes(R.drawable.sticky_date_header_background);
      }
    } else if (type == HEADER_TYPE_INLINE_DATE) {
      if (hasWallpaper) {
        viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_18);
      } else {
        viewHolder.clearBackground();
      }
    }

    if (hasWallpaper && ThemeUtil.isDarkTheme(context)) {
      viewHolder.setTextColor(ContextCompat.getColor(context, R.color.signal_colorNeutralInverse));
    } else {
      viewHolder.setTextColor(ThemeUtil.getThemedColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
    }
  }

  public @Nullable ConversationMessage getConversationMessage(int position) {
    return getItem(position);
  }

  public @Nullable ConversationMessage getItem(int position) {
    position = isTypingViewEnabled() ? position - 1 : position;

    if (position < 0) {
      return null;
    } else {
      if (pagingController != null) {
        pagingController.onDataNeededAroundIndex(position);
      }

      if (position < super.getItemCount()) {
        return super.getItem(position);
      } else {
        Log.d(TAG, "Could not access corrected position " + position + " as it is out of bounds.");
        return null;
      }
    }
  }

  /**
   * Checks a range around the given position for nulls.
   *
   * @param position The position we wish to jump to.
   * @return true if we seem like we've paged in the right data, false if not so.
   */
  public boolean canJumpToPosition(int position) {
    position = isTypingViewEnabled() ? position - 1 : position;
    if (position < 0) {
      return false;
    }

    if (position > super.getItemCount()) {
      Log.d(TAG, "Could not access corrected position " + position + " as it is out of bounds.");
      return false;
    }

    int start = Math.max(position - 10, 0);
    int end = Math.min(position + 5, super.getItemCount());

    for (int i = start; i < end; i++) {
      if (super.getItem(i) == null) {
        if (pagingController != null) {
          pagingController.onDataNeededAroundIndex(position);
        }

        return false;
      }
    }

    return true;
  }

  public void setPagingController(@Nullable PagingController pagingController) {
    this.pagingController = pagingController;
  }

  public boolean isForRecipientId(@NonNull RecipientId recipientId) {
    // TODO [alex] -- This should be fine, since we now have a 1:1 relationship between fragment and recipient.
    return true;
  }

  void onBindLastSeenViewHolder(StickyHeaderViewHolder viewHolder, long unreadCount) {
    viewHolder.setText(viewHolder.itemView.getContext().getResources().getQuantityString(R.plurals.ConversationAdapter_n_unread_messages, (int) unreadCount, (int) unreadCount));

    if (hasWallpaper) {
      viewHolder.setBackgroundRes(R.drawable.wallpaper_bubble_background_18);
      viewHolder.setDividerColor(viewHolder.itemView.getResources().getColor(R.color.transparent_black_80));
    } else {
      viewHolder.clearBackground();
      viewHolder.setDividerColor(viewHolder.itemView.getResources().getColor(R.color.core_grey_45));
    }
  }

  public boolean hasNoConversationMessages() {
    return super.getItemCount() == 0;
  }

  /**
   * The presence of a header may throw off the position you'd like to jump to. This will return
   * an adjusted message position based on adapter state.
   */
  @MainThread
  public int getAdapterPositionForMessagePosition(int messagePosition) {
    return isTypingViewEnabled() ? messagePosition + 1 : messagePosition;
  }

  /**
   * Finds the received timestamp for the item at the requested adapter position. Will return 0 if
   * the position doesn't refer to an incoming message.
   */
  @MainThread
  long getReceivedTimestamp(int position) {
    if (isHeaderPosition(position)) return 0;
    if (isFooterPosition(position)) return 0;
    if (position >= getItemCount()) return 0;
    if (position < 0)               return 0;

    ConversationMessage conversationMessage = getItem(position);

    if (conversationMessage == null || conversationMessage.getMessageRecord().isOutgoing()) {
      return 0;
    } else {
      return conversationMessage.getMessageRecord().getDateReceived();
    }
  }

  /**
   * Sets the view the appears at the top of the list (because the list is reversed).
   */
  void setFooterView(@Nullable View view) {
    boolean hadFooter = hasFooter();

    this.footerView = view;

    if (view == null && hadFooter) {
      notifyItemRemoved(getItemCount());
    } else if (view != null && hadFooter) {
      notifyItemChanged(getItemCount() - 1);
    } else if (view != null) {
      notifyItemInserted(getItemCount() - 1);
    }
  }

  /**
   * Sets the view that appears at the bottom of the list (because the list is reversed).
   */
  void setTypingView(@NonNull View view) {
    this.typingView = view;
  }

  void setTypingViewEnabled(boolean isTypingViewEnabled) {
    if (typingView == null && isTypingViewEnabled) {
      throw new IllegalStateException("Must set header before enabling.");
    }

    if (this.isTypingViewEnabled && !isTypingViewEnabled) {
      this.isTypingViewEnabled = false;
      notifyItemRemoved(0);
    } else if (this.isTypingViewEnabled) {
      notifyItemChanged(0);
    } else if (isTypingViewEnabled) {
      this.isTypingViewEnabled = true;
      notifyItemInserted(0);
    }
  }

  /**
   * Momentarily highlights a mention at the requested position.
   */
  public void pulseAtPosition(int position) {
    if (position >= 0 && position < getItemCount()) {
      int correctedPosition = isHeaderPosition(position) ? position + 1 : position;

      recordToPulse = getItem(correctedPosition);
      pulseRequest = new PulseRequest(position, recordToPulse.getMessageRecord().isOutgoing());
      notifyItemChanged(correctedPosition);
    }
  }

  @Nullable
  public PulseRequest consumePulseRequest() {
    PulseRequest request = pulseRequest;
    pulseRequest = null;
    return request;
  }

  /**
   * Conversation search query updated. Allows rendering of text highlighting.
   */
  void onSearchQueryUpdated(String query) {
    if (!Objects.equals(query, this.searchQuery)) {
      this.searchQuery = query;
      notifyDataSetChanged();
    }
  }

  /**
   * Lets the adapter know that the wallpaper state has changed.
   * @return True if the internal wallpaper state changed, otherwise false.
   */
  public boolean onHasWallpaperChanged(boolean hasWallpaper) {
    if (this.hasWallpaper != hasWallpaper) {
      Log.d(TAG, "Resetting adapter due to wallpaper change.");
      this.hasWallpaper = hasWallpaper;
      notifyDataSetChanged();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns set of records that are selected in multi-select mode.
   */
  public Set<MultiselectPart> getSelectedItems() {
    return new HashSet<>(selected);
  }

  public void removeFromSelection(@NonNull Set<MultiselectPart> parts) {
    clearMostRecentSelectedIfNecessary(Stream.of(parts).map(MultiselectPart::getConversationMessage).collect(Collectors.toSet()));
    selected.removeAll(parts);
    updateSelected();
  }

  /**
   * Clears all selected records from multi-select mode.
   */
  void clearSelection() {
    mostRecentSelected = null;
    selected.clear();
    updateSelected();
  }

  /**
   * Toggles the selected state of a record in multi-select mode.
   */
  void toggleSelection(MultiselectPart multiselectPart) {
    if (selected.contains(multiselectPart)) {
      mostRecentSelected = null;
      selected.remove(multiselectPart);
    } else {
      mostRecentSelected = multiselectPart.getConversationMessage();
      selected.add(multiselectPart);
    }
    updateSelected();
  }

  private void updateSelected() {
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTED);
  }

  void toggleFromMostRecentSelectedTo(@NonNull ConversationMessage conversationMessage) {
    if (mostRecentSelected == null) {
      return;
    }

    int indexOfMRS = 0;
    int indexOfCM = 0;

    final int itemCount = getItemCount();

    for (; indexOfMRS < itemCount; indexOfMRS++) {
      final ConversationMessage cm = getItem(indexOfMRS);
      if (cm != null && cm.getMessageRecord().getId() == mostRecentSelected.getMessageRecord().getId()) {
        break;
      }
    }

    if (indexOfMRS == itemCount) {
      return;
    }

    for (; indexOfCM < itemCount; indexOfCM++) {
      final ConversationMessage cm = getItem(indexOfCM);
      if (cm != null && cm.getMessageRecord().getId() == conversationMessage.getMessageRecord().getId()) {
        break;
      }
    }

    if (indexOfCM == itemCount) {
      return;
    }

    if (indexOfMRS > indexOfCM) {
      final int t = indexOfMRS;
      indexOfMRS = indexOfCM;
      indexOfCM = t;
    }

    while (indexOfMRS <= indexOfCM) {
      selected.addAll(getItem(indexOfMRS).getMultiselectCollection().toSet());
      indexOfMRS++;
    }

    mostRecentSelected = conversationMessage;
  }

  public void clearMostRecentSelectedIfNecessary(@Nullable final Set<ConversationMessage> conversationMessages) {
    if (mostRecentSelected != null && conversationMessages.contains(mostRecentSelected)) {
      mostRecentSelected = null;
    }
  }

  /**
   * Provided a pool, this will initialize it with view counts that make sense.
   */
  @MainThread
  public static void initializePool(@NonNull RecyclerView.RecycledViewPool pool) {
    pool.setMaxRecycledViews(MESSAGE_TYPE_INCOMING_TEXT, 25);
    pool.setMaxRecycledViews(MESSAGE_TYPE_INCOMING_MULTIMEDIA, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_OUTGOING_TEXT, 25);
    pool.setMaxRecycledViews(MESSAGE_TYPE_OUTGOING_MULTIMEDIA, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_PLACEHOLDER, 15);
    pool.setMaxRecycledViews(MESSAGE_TYPE_HEADER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_FOOTER, 1);
    pool.setMaxRecycledViews(MESSAGE_TYPE_UPDATE, 5);
  }

  public boolean isTypingViewEnabled() {
    return isTypingViewEnabled;
  }

  public boolean hasFooter() {
    return footerView != null;
  }

  private boolean isHeaderPosition(int position) {
    return isTypingViewEnabled() && position == 0;
  }

  private boolean isFooterPosition(int position) {
    return hasFooter() && position == (getItemCount() - 1);
  }

  private static @LayoutRes int getLayoutForViewType(int viewType) {
    switch (viewType) {
      case MESSAGE_TYPE_OUTGOING_TEXT:       return R.layout.conversation_item_sent_text_only;
      case MESSAGE_TYPE_OUTGOING_MULTIMEDIA: return R.layout.conversation_item_sent_multimedia;
      case MESSAGE_TYPE_INCOMING_TEXT:       return R.layout.conversation_item_received_text_only;
      case MESSAGE_TYPE_INCOMING_MULTIMEDIA: return R.layout.conversation_item_received_multimedia;
      case MESSAGE_TYPE_UPDATE:              return R.layout.conversation_item_update;
      default:                               throw new IllegalArgumentException("Unknown type!");
    }
  }

  public @Nullable ConversationMessage getLastVisibleConversationMessage(int position) {
    try {
      return getItem(position - ((hasFooter() && position == getItemCount() - 1) ? 1 : 0));
    } catch (IndexOutOfBoundsException e) {
      Log.w(TAG, "Race condition changed size of conversation", e);
      return null;
    }
  }

  public void setMessageRequestAccepted(boolean messageRequestAccepted) {
    if (this.isMessageRequestAccepted != messageRequestAccepted) {
      this.isMessageRequestAccepted = messageRequestAccepted;
      notifyDataSetChanged();
    }
  }

  public void playInlineContent(@Nullable ConversationMessage conversationMessage) {
    if (this.inlineContent != conversationMessage) {
      this.inlineContent = conversationMessage;
      notifyDataSetChanged();
    }
  }

  public void updateTimestamps() {
    notifyItemRangeChanged(0, getItemCount(), PAYLOAD_TIMESTAMP);
  }

  final static class ConversationViewHolder extends RecyclerView.ViewHolder implements GiphyMp4Playable, Colorizable {
    public ConversationViewHolder(final @NonNull View itemView) {
      super(itemView);
    }

    public BindableConversationItem getBindable() {
      return (BindableConversationItem) itemView;
    }

    @Override
    public void showProjectionArea() {
      getBindable().showProjectionArea();
    }

    @Override
    public void hideProjectionArea() {
      getBindable().hideProjectionArea();
    }

    @Override
    public @Nullable MediaItem getMediaItem() {
      return getBindable().getMediaItem();
    }

    @Override
    public @Nullable GiphyMp4PlaybackPolicyEnforcer getPlaybackPolicyEnforcer() {
      return getBindable().getPlaybackPolicyEnforcer();
    }

    @Override
    public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
      return getBindable().getGiphyMp4PlayableProjection(recyclerView);
    }

    @Override
    public boolean canPlayContent() {
      return getBindable().canPlayContent();
    }

    @Override
    public boolean shouldProjectContent() {
      return getBindable().shouldProjectContent();
    }

    @Override
    public @NonNull ProjectionList getColorizerProjections(@NonNull ViewGroup coordinateRoot) {
      return getBindable().getColorizerProjections(coordinateRoot);
    }
  }

  static class StickyHeaderViewHolder extends RecyclerView.ViewHolder {
    TextView textView;
    View     divider;

    StickyHeaderViewHolder(View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.text);
      divider  = itemView.findViewById(R.id.last_seen_divider);
    }

    StickyHeaderViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }

    public void setText(CharSequence text) {
      textView.setText(text);
    }

    public void setTextColor(@ColorInt int color) {
      textView.setTextColor(color);
    }

    public void setBackgroundRes(@DrawableRes int resId) {
      textView.setBackgroundResource(resId);
    }

    public void setDividerColor(@ColorInt int color) {
      if (divider != null) {
        divider.setBackgroundColor(color);
      }
    }

    public void clearBackground() {
      textView.setBackground(null);
    }
  }

  public abstract static class HeaderFooterViewHolder extends RecyclerView.ViewHolder {

    private ViewGroup container;

    HeaderFooterViewHolder(@NonNull View itemView) {
      super(itemView);
      this.container = (ViewGroup) itemView;
    }

    void bind(@Nullable View view) {
      unbind();

      if (view != null) {
        removeViewFromParent(view);
        container.addView(view);
      }
    }

    void unbind() {
      container.removeAllViews();
    }

    private void removeViewFromParent(@NonNull View view) {
      if (view.getParent() != null) {
        ((ViewGroup) view.getParent()).removeView(view);
      }
    }
  }

  public static class FooterViewHolder extends HeaderFooterViewHolder {
    FooterViewHolder(@NonNull View itemView) {
      super(itemView);
      setPaddingTop();
    }

    @Override
    void bind(@Nullable View view) {
      super.bind(view);
      setPaddingTop();
    }

    private void setPaddingTop() {
      if (Build.VERSION.SDK_INT <= 23) {
        int addToPadding = ViewUtil.getStatusBarHeight(itemView) + (int) ThemeUtil.getThemedDimen(itemView.getContext(), android.R.attr.actionBarSize);
        ViewUtil.setPaddingTop(itemView, itemView.getPaddingTop() + addToPadding);
      }
    }
  }

  public static class HeaderViewHolder extends HeaderFooterViewHolder {
    HeaderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  private static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
    PlaceholderViewHolder(@NonNull View itemView) {
      super(itemView);
    }
  }

  public interface ItemClickListener extends BindableConversationItem.EventListener {
    void onItemClick(MultiselectPart item);
    void onItemLongClick(View itemView, MultiselectPart item);
  }
}
